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
package net.sf.freecol.client.gui.panel.report;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.RouteHistory;
import net.sf.freecol.client.control.SupplyForecast;
import net.sf.freecol.client.gui.panel.MovedChart;
import net.sf.freecol.client.control.SupplyWatch;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.PathNode;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Unit;


/**
 * This panel displays the logistics report: every trade route with
 * its stops, what it is going to deliver where and when, and the
 * wagon trains and ships working on it.
 */
public final class ReportLogisticsPanel extends OverviewReportPanel {

    private static final String PREFIX = "report.logistics.";

    private final SupplyForecast forecast;


    /**
     * Creates the logistics report.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ReportLogisticsPanel(FreeColClient freeColClient) {
        super(freeColClient, "reportLogisticsAction");

        this.forecast = new SupplyForecast(getMyPlayer());
        update();
    }


    /**
     * Build the map of the routes.
     *
     * @param routes The player's trade routes.
     * @return The map component.
     */
    private LogisticsMap buildMap(List<TradeRoute> routes) {
        final Player player = getMyPlayer();
        List<LogisticsMap.Route> lines = new ArrayList<>();
        int n = 0;
        for (TradeRoute route : routes) {
            LogisticsMap.Route line = new LogisticsMap.Route(route.getName(),
                LogisticsMap.routeColor(n++));
            for (TradeRouteStop stop : route.getStopList()) {
                if (stop.getLocation() != null) {
                    line.addStop(stop.getLocation().getTile());
                }
            }
            for (Unit u : SupplyForecast.getCarriers(player, route)) {
                if (u.getTile() == null) continue;
                List<Tile> path = new ArrayList<>();
                int turns = 0;
                String next = null;
                TradeRouteStop stop = u.getStop();
                if (stop != null && stop.getLocation() != null) {
                    next = Messages.message(stop.getLocation()
                        .getLocationLabelFor(player));
                    if (!u.atStop(stop)) {
                        PathNode p = u.findPath(stop.getLocation());
                        if (p != null) {
                            turns = p.getLastNode().getTurns();
                            for (PathNode pn = p; pn != null; pn = pn.next) {
                                if (pn.getTile() != null) path.add(pn.getTile());
                            }
                        }
                    }
                }
                List<Goods> cargo = u.getCompactGoodsList();
                line.addCarrier(new LogisticsMap.Carrier(u, path, turns, next,
                        (cargo.isEmpty()) ? null : cargo.get(0).getType()));
            }
            lines.add(line);
        }
        return new LogisticsMap(lib, getGame().getMap(), player, lines,
            (Tile t) -> {
                // Close the report and look at the tile on the main map.
                getGUI().removeComponent(this);
                getGUI().setFocus(t);
            });
    }

    /**
     * Describe the stops of a route on one line.
     *
     * @param route The {@code TradeRoute} to describe.
     * @return A panel with the stops.
     */
    private JPanel stopsRow(TradeRoute route) {
        final Player player = getMyPlayer();
        JPanel p = row();
        boolean first = true;
        for (TradeRouteStop stop : route.getStopList()) {
            if (!first) p.add(label(">", Color.GRAY));
            first = false;
            String name = (stop.getLocation() == null) ? "?"
                : Messages.message(stop.getLocation()
                    .getLocationLabelFor(player));
            List<GoodsType> cargo = new ArrayList<>();
            for (GoodsType gt : stop.getCargo()) {
                if (!cargo.contains(gt)) cargo.add(gt);
            }
            if (cargo.isEmpty()) {
                p.add(label(name + " ("
                        + Messages.message(PREFIX + "unloadsAll") + ")",
                        cPlain));
            } else {
                p.add(label(name + " ("
                        + Messages.message(PREFIX + "loads"), cPlain));
                for (GoodsType gt : cargo) p.add(goodsLabel(gt, "", null, null));
                p.add(label(")", cPlain));
            }
        }
        return p;
    }

    /**
     * What the route actually unloaded over the last turns: a small
     * bar chart (hover a bar for that turn) and the main goods.
     *
     * @param route The {@code TradeRoute}.
     * @param color The route colour.
     * @return The row.
     */
    private JPanel movedRow(TradeRoute route, Color color) {
        final RouteHistory history = getFreeColClient()
            .getInGameController().getRouteHistory();
        final int turn = getGame().getTurn().getNumber();
        JPanel p = row();
        p.add(boldLabel(Messages.message(StringTemplate
                    .template(PREFIX + "moved")
                    .addAmount("%turns%", MovedChart.TURNS))));
        MovedChart chart = new MovedChart(history, route.getId(), turn,
                                          color, getSpecification());
        p.add(chart);
        Map<String, Integer> goods = history.sumByGoods(route.getId(), turn,
                                                        MovedChart.TURNS);
        if (goods.isEmpty()) {
            p.add(label(Messages.message(PREFIX + "moved.none"), Color.GRAY));
        } else {
            int shown = 0;
            for (Map.Entry<String, Integer> e : goods.entrySet()) {
                if (shown++ == 4) break;
                GoodsType gt = getSpecification().getGoodsType(e.getKey());
                p.add(goodsLabel(gt, String.valueOf(e.getValue()), cPlain,
                                 Messages.getName(gt)));
            }
        }
        return p;
    }

    /**
     * Describe what a route delivers, one entry per colony and goods.
     *
     * @param route The {@code TradeRoute} to describe.
     * @return A panel with the deliveries, or null if the route is
     *     not set up to deliver anything to a colony.
     */
    private JPanel deliveriesRow(TradeRoute route) {
        JPanel p = row();
        p.add(boldLabel(Messages.message(PREFIX + "delivers")));
        int n = 0;
        for (SupplyForecast.Target t : this.forecast.getTargets()) {
            if (t.route != route) continue;
            final Colony colony = t.colony;
            final Integer left = SupplyWatch.getTurnsLeft(colony, t.goodsType);
            final SupplyForecast.Outlook o = this.forecast
                .getOutlook(colony, t.goodsType, (left == null) ? -1 : left);
            String text;
            Color c;
            if (o.delivery == null) {
                text = Messages.message(StringTemplate
                    .template(PREFIX + "delivery.nothing")
                    .addName("%colony%", colony.getName()));
                c = (left == null) ? cPlain : cAlarm;
            } else if (o.status == SupplyForecast.Status.LATE) {
                text = Messages.message(StringTemplate
                    .template(PREFIX + "delivery.late")
                    .addName("%colony%", colony.getName())
                    .addAmount("%turns%", o.delivery.turns)
                    .addAmount("%left%", left));
                c = cAlarm;
            } else {
                text = Messages.message(StringTemplate
                    .template(PREFIX + "delivery.inTime")
                    .addName("%colony%", colony.getName())
                    .addAmount("%turns%", o.delivery.turns)
                    .addAmount("%amount%", o.delivery.amount));
                c = (left == null || left < 0) ? cPlain : cCovered;
            }
            p.add(goodsLabel(t.goodsType, text, c, null));
            n++;
        }
        return (n == 0) ? null : p;
    }

    /**
     * Add the rows describing one carrier.
     *
     * @param panel The panel to add to (five columns).
     * @param unit The carrier {@code Unit}.
     * @param onRoute Is the carrier following a trade route?
     */
    private void addCarrier(JPanel panel, Unit unit, boolean onRoute) {
        final Player player = getMyPlayer();
        panel.add(unitButton(unit));

        final String group = igc().getUnitGroups().getGroupName(unit);
        panel.add(label((group == null) ? "" : group, cPlain));

        String where = where(unit);
        if (onRoute && unit.getStop() != null
            && unit.getStop().getLocation() != null
            && !unit.atStop(unit.getStop())) {
            where += ", " + Messages.message(StringTemplate
                .template(PREFIX + "heading")
                .addStringTemplate("%location%", unit.getStop().getLocation()
                    .getLocationLabelFor(player)));
        }
        panel.add(label(where, cPlain));

        JPanel cargo = row();
        for (Goods g : unit.getCompactGoodsList()) {
            cargo.add(goodsLabel(g.getType(),
                    Integer.toString(g.getAmount()), cPlain, null));
        }
        if (cargo.getComponentCount() == 0) {
            cargo.add(label(Messages.message(PREFIX + "empty"), Color.GRAY));
        }
        panel.add(cargo);

        JPanel next = row();
        if (onRoute) {
            for (SupplyForecast.Delivery d : this.forecast.getDeliveries(unit)) {
                next.add(goodsLabel(d.goodsType, Messages.message(StringTemplate
                            .template(PREFIX + "carrier.delivery")
                            .addAmount("%amount%", d.amount)
                            .addName("%colony%", d.colony.getName())
                            .addAmount("%turns%", d.turns)),
                        cPlain, null));
            }
            if (next.getComponentCount() == 0) {
                next.add(label(Messages.message(PREFIX + "carrier.nothing"),
                               cWarn));
            }
        }
        panel.add(next);
    }

    private JPanel carrierTable() {
        JPanel p = new JPanel(new MigLayout("wrap 5, gap 14 2, insets 0 24 0 0",
                                            "[l][l][l][l][l]", ""));
        p.setOpaque(false);
        return p;
    }

    private void update() {
        final Player player = getMyPlayer();
        reportPanel.removeAll();
        reportPanel.setLayout(new MigLayout("fillx, wrap 1, gap 0 4",
                                            "[l, grow]", ""));

        final List<TradeRoute> routes = new ArrayList<>(player.getTradeRoutes());
        reportPanel.add(buildMap(routes), "growx, h 320!");
        reportPanel.add(label(Messages.message(PREFIX + "map.help"),
                              Color.GRAY), "gapbottom 6");
        if (routes.isEmpty()) {
            reportPanel.add(new JLabel(Messages.message(PREFIX + "none")));
        }
        int index = 0;
        for (TradeRoute route : routes) {
            final List<Unit> units = SupplyForecast.getCarriers(player, route);
            JPanel title = row();
            JLabel name = boldLabel(route.getName());
            name.setIcon(LogisticsMap.swatch(LogisticsMap.routeColor(index++)));
            title.add(name);
            title.add(label(Messages.message(StringTemplate
                        .template(PREFIX + "carriers")
                        .addAmount("%number%", units.size())),
                    (units.isEmpty()) ? cAlarm : cPlain));
            reportPanel.add(title);
            reportPanel.add(movedRow(route, LogisticsMap.routeColor(index - 1)),
                            "gapleft 16");
            reportPanel.add(stopsRow(route), "gapleft 16");
            JPanel deliveries = deliveriesRow(route);
            if (deliveries != null) reportPanel.add(deliveries, "gapleft 16");

            if (!units.isEmpty()) {
                JPanel table = carrierTable();
                for (Unit u : units) addCarrier(table, u, true);
                reportPanel.add(table);
            }
            reportPanel.add(new JSeparator(JSeparator.HORIZONTAL),
                            "growx, gaptop 4, gapbottom 4");
        }

        // Wagon trains and ships that are not on any route.
        List<Unit> spare = new ArrayList<>();
        for (Unit u : player.getUnitSet()) {
            if (u.isCarrier() && u.getTradeRoute() == null
                && !u.isDisposed()) spare.add(u);
        }
        spare.sort((a, b) -> {
                int c = Boolean.compare(a.isNaval(), b.isNaval());
                return (c != 0) ? c : where(a).compareTo(where(b));
            });
        if (!spare.isEmpty()) {
            reportPanel.add(boldLabel(Messages.message(PREFIX + "unassigned")));
            JPanel table = carrierTable();
            for (Unit u : spare) addCarrier(table, u, false);
            reportPanel.add(table);
        }
    }
}
