/**
 *  Copyright (C) 2002-2024   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client.companion;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.client.control.SupplyForecast;
import net.sf.freecol.client.control.SupplyWatch;
import net.sf.freecol.client.control.UnitGroups;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.UnitBadges;
import net.sf.freecol.client.gui.panel.report.LogisticsMap;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.PathNode;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.ProductionInfo;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.WorkLocation;


/**
 * Builds the snapshots of the game that the companion page shows,
 * as JSON, and notices what changed since the last one for the log.
 *
 * All methods must be called on the Swing event thread, like the
 * report panels that read the same data.  Everything here only reads
 * the game; nothing is ever changed.
 */
final class CompanionState {

    /** How many goods to list per colony in each column. */
    private static final int MAX_PRODUCES = 3, MAX_NEEDS = 4, MAX_STOCK = 6,
        MAX_INCOMING = 4;

    /** How far ahead a shortage is worth an alert. */
    private static final int SOON_TURNS = 10;

    /** Percentage of capacity that counts as nearly full. */
    private static final int NEARLY_FULL_PERCENT = 90;

    /** Turns of goods history to keep for the trend lines. */
    private static final int HISTORY = 30;

    /** Longest path to send for a unit, in tiles. */
    private static final int MAX_PATH = 80;

    private final FreeColClient fcc;

    // What the last snapshot saw, to find what changed.
    private String gameId = null;
    private int lastTurn = -1;
    private Set<ModelMessage> seenMessages = newIdentitySet();
    /** Message texts already logged this turn. */
    private final Set<String> loggedTexts = new java.util.HashSet<>();
    private final Map<String, Tile> lastDestinations = new HashMap<>();
    private final Map<String, String> lastColonies = new HashMap<>();

    /** Goods totals at each turn, for the trend lines. */
    private final LinkedHashMap<Integer, Map<String, Integer>> history
        = new LinkedHashMap<>();

    /** Paths found this turn, by unit, place and moves left. */
    private final Map<String, List<Tile>> pathCache = new HashMap<>();

    /** A unit showing each unit icon in use, to draw it from. */
    private final Map<String, Unit> iconUnits = new HashMap<>();

    /** Icons already drawn, as PNG. */
    private final Map<String, byte[]> icons = new HashMap<>();

    /** The map part of the state, which changes rarely. */
    private String mapJson = "{}";
    private long mapRev = 0;


    CompanionState(FreeColClient fcc) {
        this.fcc = fcc;
    }

    private static Set<ModelMessage> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }


    // Public entry points, all on the event thread.

    /**
     * Build the current state.
     *
     * @param since The id of the last log entry the page has seen.
     * @return The state as JSON.
     */
    String build(long since) {
        final Game game = this.fcc.getGame();
        final Player player = this.fcc.getMyPlayer();
        Json j = new Json().obj();
        j.put("session", CompanionLog.getSession());
        if (game == null || player == null || game.getMap() == null) {
            j.put("inGame", false);
            this.gameId = null;
            log(j, since);
            return j.end().toString();
        }
        j.put("inGame", true);
        observe(game, player);

        buildMap(game, player);
        j.put("mapRev", this.mapRev);
        header(j, game, player);

        final List<TradeRoute> routes = new ArrayList<>(player.getTradeRoutes());
        final SupplyForecast forecast = new SupplyForecast(player);
        final List<Colony> colonies = getColonies(player);
        final List<GoodsType> goodsTypes = getGoodsTypes(game);

        List<String[]> alerts = new ArrayList<>();
        colonies(j, player, colonies, goodsTypes, forecast, alerts);
        routes(j, player, routes, alerts);
        units(j, game, player, routes, alerts);
        others(j, game, player);
        alerts(j, alerts);
        goods(j, game, colonies, goodsTypes);
        log(j, since);
        return j.end().toString();
    }

    /** The map part, sent only when it changed. */
    String getMapJson() {
        return this.mapJson;
    }

    /**
     * Get an icon as PNG.
     *
     * @param kind "goods" or "unit".
     * @param key The goods type id, or the unit icon key.
     * @return The PNG bytes, or null if unknown.
     */
    byte[] getIcon(String kind, String key) {
        final String k = kind + ":" + key;
        byte[] png = this.icons.get(k);
        if (png != null) return png;
        final ImageLibrary lib = this.fcc.getGUI().getFixedImageLibrary();
        final Game game = this.fcc.getGame();
        if (lib == null || game == null) return null;
        BufferedImage img = null;
        if ("goods".equals(kind)) {
            GoodsType gt = game.getSpecification().getGoodsType(key);
            if (gt != null) img = lib.getSmallGoodsTypeImage(gt);
        } else if ("unit".equals(kind)) {
            Unit u = this.iconUnits.get(key);
            if (u != null && !u.isDisposed()) img = lib.getSmallUnitImage(u);
        }
        if (img == null) return null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            png = out.toByteArray();
        } catch (IOException ioe) {
            return null;
        }
        this.icons.put(k, png);
        return png;
    }


    // Noticing changes for the log.

    private void observe(Game game, Player player) {
        final int turn = game.getTurn().getNumber();
        final String id = String.valueOf(game.getUUID()) + "/" + player.getId();
        final boolean fresh = !id.equals(this.gameId);
        if (fresh) {
            this.gameId = id;
            this.lastTurn = -1;
            this.seenMessages = newIdentitySet();
            this.lastDestinations.clear();
            this.lastColonies.clear();
            this.history.clear();
            this.iconUnits.clear();
        }
        if (turn != this.lastTurn) {
            this.lastTurn = turn;
            this.pathCache.clear();
            this.turnsCache.clear();
            this.loggedTexts.clear();
            CompanionLog.post(turn, "turn", Messages.message(StringTemplate
                    .template("companion.log.turn")
                    .addAmount("%number%", turn)
                    .addStringTemplate("%date%", game.getTurn().getLabel())),
                null, null);
        }

        // Messages the game gave the player, other than the trade
        // route summaries, which the carrier hook covers in detail.
        // The game rebuilds its list at times, so also skip texts
        // already logged this turn, and log repeats once with a count.
        Set<ModelMessage> now = newIdentitySet();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, ModelMessage> firsts = new HashMap<>();
        for (ModelMessage m : player.getModelMessages()) {
            now.add(m);
            if (this.seenMessages.contains(m)) continue;
            if (m.getMessageType() == ModelMessage.MessageType.GOODS_MOVEMENT) {
                continue;
            }
            String text;
            try {
                text = Messages.message(m);
            } catch (RuntimeException re) {
                continue;
            }
            if (this.loggedTexts.contains(text)) continue;
            counts.merge(text, 1, Integer::sum);
            firsts.putIfAbsent(text, m);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            final ModelMessage m = firsts.get(e.getKey());
            final String text = (e.getValue() > 1)
                ? e.getKey() + " (\u00d7" + e.getValue() + ")" : e.getKey();
            CompanionLog.post(turn, "msg-" + m.getMessageType().name()
                    .toLowerCase(), text, sourceTile(game, m), null);
            this.loggedTexts.add(e.getKey());
        }
        this.seenMessages = now;

        // Units that reached the place they were heading for.  The
        // members of a group arriving together make one entry.
        final UnitGroups groups = this.fcc.getInGameController()
            .getUnitGroups();
        Map<String, List<Unit>> arrived = new LinkedHashMap<>();
        Map<String, Tile> dests = new HashMap<>();
        for (Unit u : player.getUnitSet()) {
            if (u.getTradeRoute() != null) continue; // Logged at the stops
            Location d = u.getDestination();
            Tile dt = (d == null) ? null : d.getTile();
            if (dt != null) dests.put(u.getId(), dt);
            Tile before = this.lastDestinations.get(u.getId());
            if (!fresh && before != null && dt == null
                && u.getTile() == before) {
                String group = groups.getGroupName(u);
                String key = before.getId() + "/"
                    + ((group == null) ? u.getId() : group);
                arrived.computeIfAbsent(key, k -> new ArrayList<>()).add(u);
            }
        }
        for (List<Unit> list : arrived.values()) {
            final Unit u = list.get(0);
            final String group = groups.getGroupName(u);
            final String who = (list.size() > 1 && group != null)
                ? Messages.message(StringTemplate
                    .template("companion.log.group")
                    .addName("%group%", group)
                    .addAmount("%number%", list.size()))
                : Messages.message(u.getLabel(Unit.UnitLabelType.PLAIN));
            CompanionLog.post(turn, "arrive", Messages.message(StringTemplate
                    .template("companion.log.arrive")
                    .addName("%unit%", who)
                    .addStringTemplate("%location%",
                        u.getTile().getLocationLabelFor(player))),
                u.getTile(), null);
        }
        this.lastDestinations.clear();
        this.lastDestinations.putAll(dests);

        // Colonies founded or lost.
        Map<String, String> cols = new HashMap<>();
        for (Colony c : player.getColonyList()) {
            cols.put(c.getId(), c.getName());
            if (!fresh && !this.lastColonies.containsKey(c.getId())) {
                CompanionLog.post(turn, "colony", Messages.message(StringTemplate
                        .template("companion.log.newColony")
                        .addName("%colony%", c.getName())), c.getTile(), null);
            }
        }
        if (!fresh) {
            for (Map.Entry<String, String> e : this.lastColonies.entrySet()) {
                if (cols.containsKey(e.getKey())) continue;
                CompanionLog.post(turn, "lost", Messages.message(StringTemplate
                        .template("companion.log.lostColony")
                        .addName("%colony%", e.getValue())), null, null);
            }
        }
        this.lastColonies.clear();
        this.lastColonies.putAll(cols);
    }

    private static Tile sourceTile(Game game, ModelMessage m) {
        try {
            FreeColGameObject o = game.getFreeColGameObject(m.getSourceId());
            if (o instanceof Location) return ((Location)o).getTile();
        } catch (RuntimeException re) {
            // Fall through
        }
        return null;
    }


    // The parts of the state.

    private void header(Json j, Game game, Player player) {
        final InGameController igc = this.fcc.getInGameController();
        j.put("turn", game.getTurn().getNumber())
            .put("date", Messages.message(game.getTurn().getLabel()))
            .put("nation", Messages.message(player.getNationLabel()))
            .put("color", player.getNationColor())
            .put("gold", player.getGold())
            .put("tax", player.getTax())
            .put("myTurn", this.fcc.currentPlayerIsMyPlayer())
            .put("pending", igc.countPendingOrders());
    }

    private List<Colony> getColonies(Player player) {
        List<Colony> result = new ArrayList<>();
        for (Colony c : player.getColonyList()) {
            if (c.getUnitCount() > 0) result.add(c);
        }
        result.sort(this.fcc.getClientOptions().getColonyComparator());
        return result;
    }

    private static List<GoodsType> getGoodsTypes(Game game) {
        List<GoodsType> result = new ArrayList<>();
        for (GoodsType gt : game.getSpecification().getGoodsTypeList()) {
            if ((gt.isStorable() && !gt.isTradeGoods())
                || gt.isLibertyType() || gt.isImmigrationType()) {
                result.add(gt);
            }
        }
        result.sort(GoodsType.goodsTypeComparator);
        return result;
    }

    private static void goods(Json j, String id, int amount) {
        j.arr().val(id).val(amount).endArr();
    }

    private static String supplyText(SupplyForecast.Outlook o) {
        final String key = "report.colonyOverview.supply.";
        switch (o.status) {
        case IN_TIME: case LATE:
            return Messages.message(StringTemplate
                .template(key + ((o.status == SupplyForecast.Status.LATE)
                        ? "late" : "inTime"))
                .addStringTemplate("%unit%", o.delivery.unit
                    .getLabel(Unit.UnitLabelType.NATIONAL))
                .addName("%route%", o.route.getName())
                .addAmount("%turns%", o.delivery.turns)
                .addAmount("%amount%", o.delivery.amount));
        case NO_CARRIER:
            return Messages.message(StringTemplate
                .template(key + "noCarrier")
                .addName("%route%", o.route.getName()));
        case NOTHING_COMING:
            return Messages.message(StringTemplate
                .template(key + "nothingComing")
                .addName("%route%", o.route.getName()));
        default:
            return null;
        }
    }

    private static String[] alert(String level, String text, Tile tile,
                                  String goods) {
        return new String[] { level, text,
            (tile == null) ? "-1" : Integer.toString(tile.getX()),
            (tile == null) ? "-1" : Integer.toString(tile.getY()),
            goods };
    }

    private void colonies(Json j, Player player, List<Colony> colonies,
                          List<GoodsType> goodsTypes, SupplyForecast forecast,
                          List<String[]> alerts) {
        j.key("colonies").arr();
        for (Colony colony : colonies) {
            final Tile tile = colony.getTile();
            final int capacity = colony.getWarehouseCapacity();
            j.obj().put("id", colony.getId()).put("name", colony.getName())
                .put("x", tile.getX()).put("y", tile.getY())
                .put("size", colony.getUnitCount())
                .put("cap", capacity)
                .put("sol", colony.getSonsOfLiberty());

            // Main products.
            List<AbstractGoods> produces = new ArrayList<>();
            for (GoodsType gt : goodsTypes) {
                int p = colony.getAdjustedNetProductionOf(gt);
                if (p > 0) produces.add(new AbstractGoods(gt, p));
            }
            produces.sort(Comparator.comparingInt(AbstractGoods::getAmount)
                .reversed());
            j.key("produces").arr();
            for (AbstractGoods ag : produces.subList(0,
                    Math.min(MAX_PRODUCES, produces.size()))) {
                goods(j, ag.getType().getId(), ag.getAmount());
            }
            j.endArr();

            // Needs: goods running down, then inputs in short supply.
            List<Object[]> needs = new ArrayList<>();
            for (GoodsType gt : goodsTypes) {
                int p = colony.getAdjustedNetProductionOf(gt);
                if (p >= 0) continue;
                int stock = colony.getGoodsCount(gt);
                int turns = stock / -p;
                SupplyForecast.Outlook o = forecast.getOutlook(colony, gt, turns);
                String level = (o.status == SupplyForecast.Status.IN_TIME) ? "ok"
                    : (o.isBroken() || turns <= Colony.FAMINE_TURNS) ? "alarm"
                    : "warn";
                String tip = Messages.message(StringTemplate
                    .template("companion.need.draining")
                    .addNamed("%goods%", gt)
                    .addAmount("%amount%", -p)
                    .addAmount("%turns%", turns));
                String supply = supplyText(o);
                if (supply != null) tip += " " + supply;
                int rank = turns + (("ok".equals(level)) ? 100000 : 0);
                needs.add(new Object[] { gt, p, level, tip, rank, turns });
                if (!"ok".equals(level) && turns <= SOON_TURNS) {
                    alerts.add(alert(level, Messages.message(StringTemplate
                            .template("companion.alert.runsOut")
                            .addName("%colony%", colony.getName())
                            .addNamed("%goods%", gt)
                            .addAmount("%turns%", turns))
                        + ((supply == null) ? "" : " " + supply),
                        tile, gt.getId()));
                }
            }
            Map<GoodsType, Integer> shortfall = new LinkedHashMap<>();
            for (WorkLocation wl : colony.getCurrentWorkLocationsList()) {
                ProductionInfo pi = colony.getProductionInfo(wl);
                if (pi == null) continue;
                for (AbstractGoods ag : pi.getConsumptionDeficit()) {
                    if (ag.getAmount() > 0) {
                        shortfall.merge(ag.getType(), ag.getAmount(),
                                        Integer::sum);
                    }
                }
            }
            for (Map.Entry<GoodsType, Integer> e : shortfall.entrySet()) {
                boolean shown = false;
                for (Object[] n : needs) shown |= n[0] == e.getKey();
                if (shown) continue;
                needs.add(new Object[] { e.getKey(), -e.getValue(), "short",
                        Messages.message(StringTemplate
                            .template("companion.need.short")
                            .addNamed("%goods%", e.getKey())
                            .addAmount("%amount%", e.getValue())),
                        200000, -1 });
            }
            needs.sort(Comparator.comparingInt(n -> (Integer)n[4]));
            j.key("needs").arr();
            for (Object[] n : needs.subList(0, Math.min(MAX_NEEDS,
                                                        needs.size()))) {
                j.obj().put("g", ((GoodsType)n[0]).getId())
                    .put("n", (Integer)n[1]).put("lvl", (String)n[2])
                    .put("tip", (String)n[3]).put("t", (Integer)n[5]).end();
            }
            j.endArr();

            // Stock, overflowing first.
            List<Object[]> stock = new ArrayList<>();
            for (GoodsType gt : goodsTypes) {
                if (!gt.isStorable()) continue;
                int amount = colony.getGoodsCount(gt);
                if (amount <= 0) continue;
                int p = colony.getAdjustedNetProductionOf(gt);
                String level = "";
                if (!gt.limitIgnored() && p > 0 && amount + p > capacity) {
                    level = "alarm";
                    alerts.add(alert("warn", Messages.message(StringTemplate
                            .template("companion.alert.overflow")
                            .addName("%colony%", colony.getName())
                            .addNamed("%goods%", gt)
                            .addAmount("%amount%", amount + p - capacity)),
                        tile, gt.getId()));
                } else if (!gt.limitIgnored()
                    && amount * 100 >= capacity * NEARLY_FULL_PERCENT) {
                    level = "warn";
                }
                stock.add(new Object[] { gt, amount, level,
                        gt.limitIgnored() });
            }
            stock.sort(Comparator.comparingInt((Object[] s) ->
                    ("alarm".equals(s[2])) ? 0 : 1)
                .thenComparingInt(s -> -(Integer)s[1]));
            j.key("stock").arr();
            for (Object[] s : stock.subList(0, Math.min(MAX_STOCK,
                                                        stock.size()))) {
                j.obj().put("g", ((GoodsType)s[0]).getId())
                    .put("n", (Integer)s[1]).put("lvl", (String)s[2])
                    .put("free", (Boolean)s[3]).end();
            }
            j.endArr();

            build(j, colony, forecast, alerts);

            // Deliveries on their way.
            List<SupplyForecast.Delivery> incoming = new ArrayList<>();
            for (SupplyForecast.Target t : forecast.getTargets()) {
                if (t.colony != colony) continue;
                SupplyForecast.Delivery d = forecast.getEarliest(colony,
                                                                 t.goodsType);
                if (d != null) incoming.add(d);
            }
            incoming.sort(Comparator.comparingInt(d -> d.turns));
            j.key("incoming").arr();
            for (SupplyForecast.Delivery d : incoming.subList(0,
                    Math.min(MAX_INCOMING, incoming.size()))) {
                j.obj().put("g", d.goodsType.getId()).put("n", d.amount)
                    .put("t", d.turns)
                    .put("unit", Messages.message(d.unit
                            .getLabel(Unit.UnitLabelType.PLAIN)))
                    .put("route", d.route.getName()).end();
            }
            j.endArr();
            j.end();
        }
        j.endArr();
    }

    private void build(Json j, Colony colony, SupplyForecast forecast,
                       List<String[]> alerts) {
        final String prefix = "report.colonyOverview.build.";
        final BuildableType build = colony.getCurrentlyBuilding();
        j.key("build").obj();
        if (build == null) {
            j.put("name", Messages.message(prefix + "nothing"))
                .put("lvl", "alarm").put("text", "");
            alerts.add(alert("alarm", Messages.message(StringTemplate
                    .template("companion.alert.buildNothing")
                    .addName("%colony%", colony.getName())),
                colony.getTile(), null));
            j.end();
            return;
        }
        AbstractGoods needed = new AbstractGoods();
        int turns = colony.getTurnsToComplete(build, needed);
        String text, level = "";
        int progress = -1;
        if (turns >= 0) {
            text = Messages.message(StringTemplate.template(prefix + "turns")
                .addAmount("%turns%", turns));
        } else if (needed.getType() != null) {
            text = Messages.message(StringTemplate.template(prefix + "needs")
                .addAmount("%amount%", needed.getAmount())
                .addNamed("%goods%", needed.getType()));
            level = "warn";
            SupplyForecast.Outlook o = forecast.getOutlook(colony,
                needed.getType(), -1);
            if (o.delivery != null && o.delivery.amount >= needed.getAmount()) {
                text = Messages.message(StringTemplate
                    .template(prefix + "arriving")
                    .addNamed("%goods%", needed.getType())
                    .addAmount("%turns%", o.delivery.turns));
                level = "ok";
            } else {
                alerts.add(alert((o.isBroken()) ? "alarm" : "warn",
                    Messages.message(StringTemplate
                        .template("companion.alert.buildWaiting")
                        .addName("%colony%", colony.getName())
                        .addAmount("%amount%", needed.getAmount())
                        .addNamed("%goods%", needed.getType())
                        .addNamed("%building%", build)),
                    colony.getTile(), needed.getType().getId()));
            }
        } else {
            text = Messages.message(prefix + "blocked");
            level = "warn";
        }
        // How far along: the share of the required goods in store.
        int have = 0, need = 0;
        for (AbstractGoods ag : build.getRequiredGoodsList()) {
            need += ag.getAmount();
            have += Math.min(ag.getAmount(), colony.getGoodsCount(ag.getType()));
        }
        if (need > 0) progress = 100 * have / need;
        j.put("name", Messages.getName(build)).put("text", text)
            .put("lvl", level).put("pct", progress).end();
    }

    private void routes(Json j, Player player, List<TradeRoute> routes,
                        List<String[]> alerts) {
        j.key("routes").arr();
        int index = 0;
        for (TradeRoute route : routes) {
            final List<TradeRouteStop> stops = route.getStopList();
            final int n = stops.size();
            final int carriers = SupplyForecast.getCarriers(player, route).size();
            j.obj().put("name", route.getName())
                .put("color", LogisticsMap.routeColor(index++))
                .put("carriers", carriers);
            j.key("stops").arr();
            for (int i = 0; i < n; i++) {
                final TradeRouteStop stop = stops.get(i);
                final Location loc = stop.getLocation();
                final Tile tile = (loc == null) ? null : loc.getTile();
                j.obj().put("name", (loc == null) ? "?"
                    : Messages.message(loc.getLocationLabelFor(player)))
                    .put("x", (tile == null) ? -1 : tile.getX())
                    .put("y", (tile == null) ? -1 : tile.getY());
                j.key("load").arr();
                List<GoodsType> seen = new ArrayList<>();
                for (GoodsType gt : stop.getCargo()) {
                    if (seen.contains(gt)) continue;
                    seen.add(gt);
                    j.val(gt.getId());
                }
                j.endArr();
                j.key("unload").arr();
                final TradeRouteStop before = stops.get((i - 1 + n) % n);
                seen.clear();
                for (GoodsType gt : before.getCargo()) {
                    if (seen.contains(gt) || stop.getCargo().contains(gt)) {
                        continue;
                    }
                    seen.add(gt);
                    j.val(gt.getId());
                }
                j.endArr();
                j.end();
            }
            j.endArr();
            j.end();
            if (carriers == 0 && n >= 2) {
                alerts.add(alert("warn", Messages.message(StringTemplate
                        .template("companion.alert.routeNoCarrier")
                        .addName("%route%", route.getName())), null, null));
            }
        }
        j.endArr();
    }

    private static String unitKind(Unit u) {
        if (u.isNaval()) return "ship";
        if (u.isCarrier()) return "wagon";
        if (u.isOffensiveUnit()) return "army";
        return "unit";
    }

    private List<Tile> pathTo(Unit u, Location dest) {
        final String key = u.getId() + "@" + u.getTile().getId() + ">"
            + dest.getId() + "/" + u.getMovesLeft();
        List<Tile> path = this.pathCache.get(key);
        if (path != null) return path;
        path = new ArrayList<>();
        try {
            PathNode p = u.findPath(dest);
            for (PathNode pn = p; pn != null && path.size() < MAX_PATH;
                 pn = pn.next) {
                if (pn.getTile() != null) path.add(pn.getTile());
            }
            if (p != null) this.turnsCache.put(key, p.getLastNode().getTurns());
        } catch (RuntimeException re) {
            // No path then.
        }
        this.pathCache.put(key, path);
        return path;
    }

    /** Turns to the end of each cached path. */
    private final Map<String, Integer> turnsCache = new HashMap<>();

    private int turnsTo(Unit u, Location dest) {
        final String key = u.getId() + "@" + u.getTile().getId() + ">"
            + dest.getId() + "/" + u.getMovesLeft();
        return this.turnsCache.getOrDefault(key, -1);
    }

    private void units(Json j, Game game, Player player,
                       List<TradeRoute> routes, List<String[]> alerts) {
        final UnitGroups groups = this.fcc.getInGameController()
            .getUnitGroups();
        final boolean myTurn = this.fcc.currentPlayerIsMyPlayer();
        List<String> idle = new ArrayList<>();
        Tile idleTile = null;
        j.key("units").arr();
        List<Unit> units = new ArrayList<>(player.getUnitSet());
        units.sort(Comparator.comparing(Unit::getId));
        for (Unit u : units) {
            if (!u.isOnTile() || u.getTile() == null) continue;
            final Tile tile = u.getTile();
            final String icon = u.getType().getId() + "." + u.getRole().getId();
            this.iconUnits.putIfAbsent(icon, u);
            final UnitBadges.Kind badge = UnitBadges.kindOf(u);
            final String name = Messages.message(u.getLabel(Unit.UnitLabelType.PLAIN));
            j.obj().put("id", u.getId()).put("name", name)
                .put("kind", unitKind(u)).put("icon", icon)
                .put("x", tile.getX()).put("y", tile.getY())
                .put("badge", UnitBadges.getName(badge))
                .put("moves", u.getMovesLeft() > 0);
            final String group = groups.getGroupName(u);
            if (group != null) {
                j.put("group", group).put("rush", groups.isRushing(game, group));
            }

            Location dest = null;
            final TradeRoute route = u.getTradeRoute();
            if (route != null) {
                j.put("route", routes.indexOf(route));
                TradeRouteStop stop = u.getStop();
                if (stop != null && !u.atStop(stop)) dest = stop.getLocation();
            } else {
                dest = u.getDestination();
            }
            if (dest != null && dest.getTile() != tile) {
                j.put("dest", Messages.message(dest.getLocationLabelFor(player)));
                List<Tile> path = pathTo(u, dest);
                j.put("turns", turnsTo(u, dest));
                j.key("path").arr();
                for (Tile t : path) {
                    if (t == tile) continue;
                    j.arr().val(t.getX()).val(t.getY()).endArr();
                }
                j.endArr();
            }
            j.key("cargo").arr();
            for (Goods g : u.getCompactGoodsList()) {
                goods(j, g.getType().getId(), g.getAmount());
            }
            j.endArr();
            if (u.isCarrier()) j.put("units", u.getUnitCount());
            j.end();

            if (myTurn && badge == UnitBadges.Kind.ACTIVE && !u.isInColony()) {
                idle.add(name);
                if (idleTile == null) idleTile = tile;
            }
        }
        j.endArr();
        j.put("idle", idle.size());
        if (!idle.isEmpty()) {
            alerts.add(alert("info", Messages.message(StringTemplate
                    .template("companion.alert.idle")
                    .addAmount("%number%", idle.size())
                    .addName("%units%", String.join(", ", idle))),
                idleTile, null));
        }
        final int pending = this.fcc.getInGameController().countPendingOrders();
        if (pending > 0) {
            alerts.add(alert("info", Messages.message(StringTemplate
                    .template("companion.alert.pending")
                    .addAmount("%number%", pending)), null, null));
        }
    }

    /** Other nations' units we can see, as coloured dots. */
    private void others(Json j, Game game, Player player) {
        j.key("others").arr();
        final net.sf.freecol.common.model.Map map = game.getMap();
        for (Tile t : map.getTileSet(t -> !t.hasSettlement()
                && t.getUnitCount() > 0)) {
            {
                Unit u = t.getFirstUnit();
                if (u == null || u.getOwner() == player) continue;
                boolean military = false;
                for (Unit v : t.getUnitList()) military |= v.isOffensiveUnit();
                j.obj().put("x", t.getX()).put("y", t.getY())
                    .put("c", u.getOwner().getNationColor())
                    .put("n", t.getUnitCount()).put("mil", military)
                    .put("who", Messages.message(u.getOwner().getNationLabel()))
                    .end();
            }
        }
        j.endArr();
    }

    private static void alerts(Json j, List<String[]> alerts) {
        final List<String> order = Arrays.asList("alarm", "warn", "info");
        alerts.sort(Comparator.comparingInt(a -> order.indexOf(a[0])));
        j.key("alerts").arr();
        for (String[] a : alerts) {
            j.obj().put("lvl", a[0]).put("text", a[1])
                .put("x", Integer.parseInt(a[2]))
                .put("y", Integer.parseInt(a[3]))
                .put("g", a[4]).end();
        }
        j.endArr();
    }

    private void goods(Json j, Game game, List<Colony> colonies,
                       List<GoodsType> goodsTypes) {
        final int turn = game.getTurn().getNumber();
        Map<String, Integer> totals = new LinkedHashMap<>();
        Map<String, Integer> nets = new LinkedHashMap<>();
        for (GoodsType gt : goodsTypes) {
            if (!gt.isStorable()) continue;
            int total = 0, net = 0;
            for (Colony c : colonies) {
                total += c.getGoodsCount(gt);
                net += c.getAdjustedNetProductionOf(gt);
            }
            totals.put(gt.getId(), total);
            nets.put(gt.getId(), net);
        }
        this.history.put(turn, totals);
        while (this.history.size() > HISTORY) {
            this.history.remove(this.history.keySet().iterator().next());
        }
        j.key("goods").arr();
        for (GoodsType gt : goodsTypes) {
            if (!gt.isStorable()) continue;
            final String id = gt.getId();
            final int total = totals.get(id), net = nets.get(id);
            if (total == 0 && net == 0) continue;
            j.obj().put("g", id).put("name", Messages.getName(gt))
                .put("total", total).put("net", net);
            j.key("hist").arr();
            for (Map<String, Integer> h : this.history.values()) {
                j.val(h.getOrDefault(id, 0));
            }
            j.endArr().end();
        }
        j.endArr();
    }

    private static void log(Json j, long since) {
        j.key("log").arr();
        for (CompanionLog.Entry e : CompanionLog.since(since)) {
            j.obj().put("id", e.id).put("turn", e.turn).put("kind", e.kind)
                .put("text", e.text).put("x", e.x).put("y", e.y);
            j.key("goods").arr();
            for (Map.Entry<String, Integer> g : e.goods.entrySet()) {
                goods(j, g.getKey(), g.getValue());
            }
            j.endArr().end();
        }
        j.endArr();
    }


    // The map.

    private void buildMap(Game game, Player player) {
        final net.sf.freecol.common.model.Map map = game.getMap();
        final int w = map.getWidth(), h = map.getHeight();
        final int[] tiles = new int[w * h];
        final Map<TileType, Integer> palette = new LinkedHashMap<>();
        final Map<Player, Integer> owners = new LinkedHashMap<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Tile t = map.getTile(x, y);
                if (t == null || !t.isExplored() || t.getType() == null) continue;
                int v = palette.computeIfAbsent(t.getType(),
                                                k -> palette.size()) + 1;
                if (t.hasRoad()) v |= 1 << 8;
                if (t.hasRiver()) v |= 1 << 9;
                Player o = t.getOwner();
                if (o != null) {
                    v |= (owners.computeIfAbsent(o, k -> owners.size()) + 1)
                        << 10;
                }
                tiles[y * w + x] = v;
            }
        }
        List<Settlement> settlements = new ArrayList<>();
        long rev = Arrays.hashCode(tiles);
        for (Tile t : map.getTileSet(Tile::hasSettlement)) {
            {
                settlements.add(t.getSettlement());
                rev = 31 * rev + t.getSettlement().getName().hashCode();
            }
        }
        rev = 31 * rev + palette.hashCode() + owners.size();
        rev &= 0x7fffffffL; // Small enough for JavaScript numbers
        if (rev == this.mapRev) return;
        this.mapRev = rev;

        Json j = new Json().obj().put("w", w).put("h", h).put("rev", rev);
        j.key("palette").arr();
        for (TileType tt : palette.keySet()) {
            j.obj().put("c", ImageLibrary.getMinimapEconomicColor(tt))
                .put("land", !tt.isWater())
                .put("name", Messages.getName(tt)).end();
        }
        j.endArr();
        j.key("owners").arr();
        for (Player p : owners.keySet()) {
            j.obj().put("c", p.getNationColor())
                .put("me", p == player)
                .put("name", Messages.message(p.getNationLabel())).end();
        }
        j.endArr();
        j.key("tiles").arr();
        for (int v : tiles) j.val(v);
        j.endArr();
        j.key("settlements").arr();
        for (Settlement s : settlements) {
            final Color c = (s.getOwner() == null) ? Color.GRAY
                : s.getOwner().getNationColor();
            j.obj().put("x", s.getTile().getX()).put("y", s.getTile().getY())
                .put("name", s.getName()).put("c", c)
                .put("mine", s.getOwner() == player)
                .put("colony", s instanceof Colony).end();
        }
        j.endArr();
        this.mapJson = j.end().toString();
    }
}
