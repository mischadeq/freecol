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

package net.sf.freecol.server.ai.diplomacy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.Action;
import net.sf.freecol.common.diplomacy.Diplomat;
import net.sf.freecol.common.diplomacy.Goal;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.Mood;
import net.sf.freecol.common.diplomacy.NationView;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.diplomacy.Outlook;
import net.sf.freecol.common.diplomacy.ScoredAction;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeSet.See;
import net.sf.freecol.common.networking.OfferMessage;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.AIUnit;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.ai.mission.Mission;
import net.sf.freecol.server.ai.mission.TradeVoyageMission;
import net.sf.freecol.server.ai.mission.TransportMission;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * The offers a computer nation makes to the human players (mod):
 * peace terms by messenger when it wants out of a war with them, and
 * notices on the notice board ("wanted: 100 muskets, 600 gold") from
 * the diplomat's trade moves.  Also checks deliveries and deadlines,
 * and handles the player's answers.
 *
 * A player who accepts a notice brings the goods (or fetches them) by
 * ship: a ship of his next to the colony with the goods (or the room)
 * at the start of our turn completes the deal.
 */
public final class OfferPlanner {

    private static final Logger logger = Logger.getLogger(OfferPlanner.class.getName());

    /** Turns between two new offers of one nation. */
    public static final int GAP = 5;
    /** Finished offers are forgotten after this many turns. */
    public static final int KEEP = 40;
    /** Tiles a merchant ship sails in a turn, for deadlines. */
    public static final int SHIP_TILES = 5;
    /** Most gold sweetening peace terms. */
    public static final int MAX_PEACE_GOLD = 1000;
    /** A player's offer is taken if it gains us this share of its gold. */
    public static final double POSTED_GAIN = 0.1;

    private final EuropeanAIPlayer ai;
    /** Why the last fetch next to a colony could not be done (mod):
        "nogold:price" or "noroom:amount:goods", or null. */
    private String fetchWhy = null;


    public OfferPlanner(EuropeanAIPlayer ai) {
        this.ai = ai;
    }

    private Player me() {
        return ai.getPlayer();
    }

    private Game game() {
        return ai.getGame();
    }

    private int now() {
        return game().getTurn().getNumber();
    }

    /** The human players, not the King's forces. */
    private List<Player> humans() {
        List<Player> ret = new ArrayList<>();
        for (Player p : game().getLiveEuropeanPlayerList()) {
            if (!p.isAI() && !p.isREF() && p != me()) ret.add(p);
        }
        return ret;
    }

    /**
     * This turn's work: deliveries, deadlines, telling the players,
     * a new offer.
     *
     * @param outlook The diplomat's outlook for this turn.
     * @param lb A {@code LogBuilder} to log to.
     */
    public void plan(Outlook outlook, LogBuilder lb) {
        final Player me = me();
        if (me.isREF() || !me.isEuropean() || humans().isEmpty()) return;
        final ChangeSet cs = new ChangeSet();
        visits.clear();
        final int now = now();
        final List<Offer> book = ai.getOffers();
        for (Iterator<Offer> it = book.iterator(); it.hasNext();) {
            final Offer o = it.next();
            if (o.state.over() && now - o.posted > KEEP) it.remove();
        }
        // The players' envoys get their answer (step 8).
        new EnvoyDesk(ai).answerAll(outlook, cs, lb);
        for (Offer o : book) {
            if (!me.getNationId().equals(o.from)) {
                if (o.stale(now)) o.state = Offer.State.EXPIRED;
            } else if (o.state == Offer.State.TAKEN
                       && o.kind == Offer.Kind.AGREEMENT) {
                followAgreement(o, cs, lb);
            } else if (o.state == Offer.State.TAKEN) {
                followUp(o, cs);
            } else if (o.stale(now)) {
                o.state = Offer.State.EXPIRED;
            }
        }
        book.addAll(visits);
        considerPosted(outlook, cs, lb);
        final Offer made = post(outlook);
        if (made != null) {
            book.add(made);
            lb.add("\n  Offer: ", made);
        }
        for (Offer o : book) tell(o, cs);
        ((ServerGame)game()).sendToAll(cs);
    }


    // Deliveries and deadlines

    private void followUp(Offer o, ChangeSet cs) {
        final int now = now();
        final Player taker = game().getPlayerByNationId(o.takenBy);
        final Colony colony = game().getFreeColGameObject(o.place, Colony.class);
        if (taker == null || colony == null || colony.getOwner() != me()) {
            o.state = Offer.State.CANCELLED;
            return;
        }
        final int price = o.priceAt(now);
        if (price >= 0 && deliver(o, taker, colony, price, cs)) {
            complete(o, taker, price, cs);
            return;
        }
        if (price < 0) {
            o.state = Offer.State.FAILED;
            remember(Memory.Kind.DEAL_BROKEN, taker);
            message(cs, taker, "offer.failed", o, o.gold);
            return;
        }
        final int left = o.turnsLeft(now);
        if (left == 3 || left == 1) {
            message(cs, taker, (o.kind == Offer.Kind.WANT)
                ? "offer.reminder" : "offer.reminderFetch", o, o.gold);
        } else if (left == 0) {
            message(cs, taker, "offer.due", o, o.gold);
        }
    }

    /** A delivery made: the deal is done (or late), and remembered. */
    private void complete(Offer o, Player taker, int price, ChangeSet cs) {
        if (o.kind != Offer.Kind.WANT) o.paid = price;
        final boolean late = price < o.gold;
        o.state = (late) ? Offer.State.LATE : Offer.State.DONE;
        remember(late ? Memory.Kind.DEAL_LATE : Memory.Kind.DEAL_KEPT, taker);
        remember(Memory.Kind.TRADE, taker);
        message(cs, taker, (o.kind == Offer.Kind.WANT)
            ? ((late) ? "offer.deliveredLate" : "offer.delivered")
            : "offer.fetched", o, (o.kind == Offer.Kind.WANT) ? o.paid : price);
    }

    /**
     * The player moved a ship or wagon train into one of our colonies to
     * deliver (or fetch) a notice-board deal he took (mod): hand it over
     * now instead of at the start of our turn.
     *
     * @param taker The player.
     * @param colony Our colony.
     * @param cs A {@code ChangeSet} to update.
     * @return "done" if a deal was completed, "part" if goods were
     *     handed over but the deal is not complete, "nogold:price" or
     *     "noroom:amount:goods" when a deal he took can not be fetched
     *     yet, "none" otherwise.
     */
    public String deliverNow(ServerPlayer taker, Colony colony,
                             ChangeSet cs) {
        if (colony == null || colony.getOwner() != me()) return "none";
        String ret = "none", why = null;
        final int now = now();
        for (Offer o : new ArrayList<>(ai.getOffers())) {
            if ((o.kind != Offer.Kind.WANT && o.kind != Offer.Kind.SELL)
                || !me().getNationId().equals(o.from)
                || !colony.getId().equals(o.place)) continue;
            // A notice still open to him: coming with the goods (or the
            // room and the gold) takes it on the spot.
            final boolean open = o.state == Offer.State.OPEN
                && o.visibleTo(taker.getNationId(), now);
            if (!open && (o.state != Offer.State.TAKEN
                          || !taker.getNationId().equals(o.takenBy))) continue;
            if (open) {
                o.state = Offer.State.TAKEN;
                o.takenBy = taker.getNationId();
                o.takenTurn = now;
            }
            final int price = o.priceAt(now);
            final int before = o.delivered;
            this.fetchWhy = null;
            if (price >= 0 && deliver(o, taker, colony, price, cs)) {
                complete(o, taker, price, cs);
                ret = "done";
            } else if (!open && this.fetchWhy != null) {
                why = this.fetchWhy; // A deal he took: say what is missing
            } else if (o.delivered > before) {
                if (!"done".equals(ret)) ret = "part";
            } else if (open && o.state == Offer.State.TAKEN) {
                o.state = Offer.State.OPEN; // could not: still open
                o.takenBy = null;
                o.takenTurn = -1;
            }
        }
        // A running price agreement: ask how many they sell, now.
        for (Offer a : new ArrayList<>(ai.getOffers())) {
            if (a.kind != Offer.Kind.AGREEMENT || a.state != Offer.State.TAKEN
                || !taker.getNationId().equals(a.takenBy)
                || a.turnsLeft(now) <= 0
                || me().getStance(taker) == Stance.WAR) continue;
            final GoodsType gt = game().getSpecification().getGoodsType(a.goods);
            if (gt == null) continue;
            for (Tile t : colony.getTile().getSurroundingTiles(1)) {
                for (Unit u : t.getUnitList()) {
                    if (u.getOwner() != taker || !u.canCarryGoods()
                        || u.getGoodsCount(gt) <= 0 || askedLately(a, u)) continue;
                    final Offer q = saleVisit(a, taker, u, colony, cs);
                    ai.getOffers().add(q);
                    tell(q, cs);
                    if ("none".equals(ret)) ret = "asked";
                }
            }
        }
        return ("none".equals(ret) && why != null) ? why : ret;
    }

    /**
     * Complete a deal if a ship or wagon train of the taker is next to
     * the colony with the goods (or the room and the gold).
     *
     * @return True if done.
     */
    private boolean deliver(Offer o, Player taker, Colony colony, int price,
                            ChangeSet cs) {
        final GoodsType gt = game().getSpecification().getGoodsType(o.goods);
        if (gt == null) return false;
        final Player me = me();
        for (Tile t : colony.getTile().getSurroundingTiles(1)) {
            for (Unit u : t.getUnitList()) {
                // Ships and, since the player asked, wagon trains.
                if (u.getOwner() != taker || !u.canCarryGoods()) continue;
                if (o.kind == Offer.Kind.WANT) {
                    // In parts (step 4): what the ship has, paid pro rata.
                    final int n = Math.min(u.getGoodsCount(gt),
                                           o.amount - o.delivered);
                    if (n <= 0) continue;
                    final int part = (int)((long)price * n / o.amount);
                    final int pay = Math.min(part, Math.max(0, me.getGold()));
                    u.removeGoods(gt, n);
                    colony.addGoods(gt, n);
                    gold(me, -pay, cs);
                    gold(taker, pay, cs);
                    o.delivered += n;
                    o.paid += pay;
                    cs.add(See.only(taker), u);
                    if (o.delivered < o.amount) {
                        message(cs, taker, "offer.deliveredPart", o, pay);
                        return false;
                    }
                    return true;
                } else {
                    if (u.getSpaceLeft() * 100 < o.amount) {
                        this.fetchWhy = "noroom:" + o.amount + ":" + o.goods;
                        continue;
                    }
                    if (taker.getGold() < price) {
                        this.fetchWhy = "nogold:" + price;
                        continue;
                    }
                    if (colony.getGoodsCount(gt) < o.amount) {
                        // We no longer have them: our failure.
                        o.state = Offer.State.CANCELLED;
                        message(cs, taker, "offer.cancelled", o, price);
                        return false;
                    }
                    colony.removeGoods(gt, o.amount);
                    u.addGoods(gt, o.amount);
                    gold(taker, -price, cs);
                    gold(me, price, cs);
                }
                cs.add(See.only(taker), u);
                return true;
            }
        }
        return false;
    }

    private static void gold(Player p, int amount, ChangeSet cs) {
        if (amount == 0) return;
        p.modifyGold(amount);
        cs.addPartial(See.only(p), p, "gold", String.valueOf(p.getGold()),
                      "score", String.valueOf(p.getScore()));
    }

    private void remember(Memory.Kind k, Player about) {
        ai.getMemories().add(new Memory(k, about.getNationId(), now(), 1));
    }


    // Telling the players

    /** Show an open offer to every human who can see it now, once. */
    private void tell(Offer o, ChangeSet cs) {
        if (o.state != Offer.State.OPEN) return;
        if (o.kind.byShip()) { // a heads-up, the trade screen comes later
            final Player h = game().getPlayerByNationId(o.audience.get(0));
            if (h != null && !o.told.contains(h.getNationId())) {
                o.told.add(h.getNationId());
                message(cs, h, (o.kind == Offer.Kind.SHIP_SELL)
                    ? "offer.shipSell" : "offer.shipBuy", o, o.gold);
            }
            return;
        }
        for (Player h : humans()) {
            final String id = h.getNationId();
            if (o.told.contains(id) || !o.visibleTo(id, now())) continue;
            o.told.add(id);
            cs.add(See.only(h), new OfferMessage(o));
            message(cs, h, postedKey(o.kind), o, o.gold);
        }
    }

    private static String postedKey(Offer.Kind k) {
        switch (k) {
        case PEACE: return "offer.postedPeace";
        case WANT: return "offer.postedWant";
        case CALL_TO_ARMS: return "offer.postedCall";
        case ALLIANCE: return "offer.postedAlliance";
        case JOINT_WAR: return "offer.postedJointWar";
        case COALITION: return "offer.postedCoalition";
        case BUYOUT: return "offer.postedBuyout";
        case ULTIMATUM: return "offer.postedUltimatum";
        case AGREEMENT: return "offer.postedAgreement";
        case SALE: return "offer.postedSale";
        case TERMS: return "offer.postedTerms";
        default: return "offer.postedSell";
        }
    }

    private void message(ChangeSet cs, Player to, String key, Offer o,
                         int gold) {
        final GoodsType gt = (o.goods == null) ? null
            : game().getSpecification().getGoodsType(o.goods);
        ModelMessage mm = new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, key, me())
            .addStringTemplate("%nation%", me().getNationLabel())
            .addAmount("%amount%", o.amount)
            .addAmount("%gold%", gold)
            .addAmount("%turns%", Math.max(0, o.turnsLeft(now())))
            .addName("%colony%", o.placeName);
        if (gt != null) mm.addNamed("%goods%", gt);
        else mm.addName("%goods%", "");
        final Player enemy = (o.against == null) ? null
            : game().getPlayerByNationId(o.against);
        if (enemy != null) mm.addStringTemplate("%enemy%", enemy.getNationLabel());
        else mm.addName("%enemy%", "");
        cs.addMessage(to, mm);
    }


    // Making offers

    /**
     * A new offer, if it is time for one and the diplomat wants one.
     *
     * @param outlook The diplomat's outlook.
     * @return The new {@code Offer}, or null.
     */
    public Offer post(Outlook outlook) {
        final int now = now();
        for (Offer o : ai.getOffers()) {
            if (me().getNationId().equals(o.from) && now - o.posted < GAP) {
                return null;
            }
        }
        Offer o = peaceTerms(outlook);
        if (o == null) o = allianceInvite(outlook);
        if (o == null) o = voyage(outlook);
        if (o == null) o = agreementProposal(outlook);
        return (o != null) ? o : notice(outlook);
    }

    /** Peace terms to a human enemy, if the diplomat wants peace. */
    private Offer peaceTerms(Outlook outlook) {
        final int now = now();
        for (ScoredAction sa : outlook.ranked) {
            if (sa.action.kind != Action.Kind.MAKE_PEACE
                || sa.total < Diplomat.SMALL_THRESHOLD) continue;
            final Player h = game().getPlayerByNationId(sa.action.target);
            if (h == null || h.isAI() || !h.isEuropean() || h.isREF()
                || me().getStance(h) != Stance.WAR) continue;
            // Losing: sweeten the terms.
            boolean losing = outlook.view.exposure() > outlook.view.me.army;
            for (Goal g : outlook.situation.goals) {
                if (g.type == Goal.Type.MAKE_PEACE && h.getNationId().equals(g.target)) {
                    losing = true;
                }
            }
            final int gold = (losing)
                ? 50 * (Math.min(MAX_PEACE_GOLD, Math.max(0, me().getGold()) / 5) / 50)
                : 0;
            final Colony capital = biggest(me().getColonyList(), null);
            // We owe them for a ship and won the war: peace if they
            // cancel the debt (mod, ship loans).
            final int debt = new LoanDesk(ai).debtToCancel(h);
            Offer o = new Offer(id(now), Offer.Kind.PEACE, me().getNationId(),
                null, debt, (debt > 0) ? 0 : gold,
                (capital == null) ? null : capital.getId(),
                (capital == null) ? "" : capital.getName(), now, 0);
            o.audience.add(h.getNationId());
            return o;
        }
        return null;
    }

    /**
     * A trade voyage: the diplomat's best trade with a human nation,
     * carried by a free ship of ours (empty, with no job but hauling).
     */
    private Offer voyage(Outlook outlook) {
        final int now = now();
        for (ScoredAction sa : outlook.ranked) {
            final Action a = sa.action;
            if ((a.kind != Action.Kind.BUY_GOODS
                 && a.kind != Action.Kind.SELL_GOODS)
                || sa.total < Diplomat.SMALL_THRESHOLD) continue;
            final Player h = game().getPlayerByNationId(a.target);
            if (h == null || h.isAI() || h.isREF()
                || me().getStance(h) == Stance.WAR
                || me().getStance(h) == Stance.UNCONTACTED) continue;
            final GoodsType gt = game().getSpecification().getGoodsType(a.goods);
            if (gt == null) continue;
            final boolean sell = a.kind == Action.Kind.SELL_GOODS;
            final int gold = a.amount * a.price;
            if (!sell && me().getGold() < gold) continue;
            for (Unit u : new ArrayList<>(me().getUnitSet())) {
                if (!freeShip(u)) continue;
                final Colony home = u.getTile().getColony();
                if (sell && (home == null || home.getOwner() != me()
                             || home.getGoodsCount(gt) < a.amount)) continue;
                final Colony dest = nearestPort(h, u.getTile());
                if (dest == null) continue;
                if (sell) {
                    home.removeGoods(gt, a.amount);
                    u.addGoods(gt, a.amount);
                }
                Offer o = new Offer(id(now), (sell) ? Offer.Kind.SHIP_SELL
                    : Offer.Kind.SHIP_BUY, me().getNationId(), a.goods,
                    a.amount, gold, dest.getId(), dest.getName(), now, 0);
                o.audience.add(h.getNationId());
                final AIUnit aiu = ai.getAIMain().getAIUnit(u);
                aiu.changeMission(new TradeVoyageMission(ai.getAIMain(), aiu,
                                                         dest, o.id));
                return o;
            }
        }
        return null;
    }

    /** An empty ship of ours with nothing better to do than hauling. */
    public boolean freeShip(Unit u) {
        if (!u.isNaval() || !u.canCarryGoods() || !u.hasTile()
            || u.isDamaged() || u.getCargoSpaceTaken() > 0) return false;
        final AIUnit aiu = ai.getAIMain().getAIUnit(u);
        if (aiu == null) return false;
        final Mission m = aiu.getMission();
        return m == null || !m.isValid()
            || (m instanceof TransportMission && ((TransportMission)m).isEmpty());
    }

    /** A player's coastal colony nearest to a tile. */
    private static Colony nearestPort(Player p, Tile from) {
        Colony best = null;
        int bestD = Integer.MAX_VALUE;
        for (Colony c : p.getColonyList()) {
            if (!c.isConnectedPort()) continue;
            final int d = c.getTile().getDistanceTo(from);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** An invitation to an alliance, if the diplomat wants one. */
    private Offer allianceInvite(Outlook outlook) {
        final int now = now();
        for (ScoredAction sa : outlook.ranked) {
            if (sa.action.kind != Action.Kind.ALLIANCE
                || sa.total < Diplomat.THRESHOLD) continue;
            final Player h = game().getPlayerByNationId(sa.action.target);
            if (h == null || h.isAI() || h.isREF()
                || me().getStance(h) != Stance.PEACE) continue;
            Offer o = new Offer(id(now), Offer.Kind.ALLIANCE,
                me().getNationId(), null, 0, 0, null, "", now, 0);
            o.audience.add(h.getNationId());
            return o;
        }
        return null;
    }

    /** A notice from the diplomat's best trade move. */
    private Offer notice(Outlook outlook) {
        final int now = now();
        final List<String> audience = audience(outlook);
        boolean human = false;
        for (String a : audience) {
            final Player p = game().getPlayerByNationId(a);
            human |= p != null && !p.isAI();
        }
        if (!human) return null;
        for (ScoredAction sa : outlook.ranked) {
            final Action a = sa.action;
            if ((a.kind != Action.Kind.BUY_GOODS
                 && a.kind != Action.Kind.SELL_GOODS)
                || sa.total < Diplomat.SMALL_THRESHOLD) continue;
            final GoodsType gt = game().getSpecification().getGoodsType(a.goods);
            if (gt == null) continue;
            final int gold = a.amount * a.price;
            final boolean want = a.kind == Action.Kind.BUY_GOODS;
            if (want && me().getGold() < gold) continue;
            final Colony c = (want) ? biggest(ports(), null)
                : biggest(ports(), gt);
            if (c == null || (!want && c.getGoodsCount(gt) < a.amount)) continue;
            Offer o = new Offer(id(now), (want) ? Offer.Kind.WANT
                : Offer.Kind.SELL, me().getNationId(), a.goods, a.amount,
                gold, c.getId(), c.getName(), now,
                Offer.deadlineFor(sailTurns(c, audience)));
            o.audience.addAll(audience);
            return o;
        }
        return null;
    }

    /**
     * Who may see a notice: European nations we are not at war with
     * and feel at least neutral about, liked most first.
     */
    List<String> audience(Outlook outlook) {
        List<NationView> l = new ArrayList<>();
        for (NationView v : outlook.view.others.values()) {
            if (!v.european || v.atWar() || v.opinion <= Mood.NEUTRAL) continue;
            final Player p = game().getPlayerByNationId(v.id);
            if (p == null || p.isREF()) continue;
            l.add(v);
        }
        l.sort(Comparator.comparingDouble((NationView v) -> -v.opinion));
        List<String> ret = new ArrayList<>();
        for (NationView v : l) ret.add(v.id);
        return ret;
    }

    private List<Colony> ports() {
        List<Colony> ret = new ArrayList<>();
        for (Colony c : me().getColonyList()) {
            if (c.isConnectedPort()) ret.add(c);
        }
        return ret;
    }

    /** The biggest colony, or the one with most of some goods. */
    private static Colony biggest(List<Colony> colonies, GoodsType gt) {
        Colony best = null;
        for (Colony c : colonies) {
            if (best == null
                || ((gt == null) ? c.getUnitCount() > best.getUnitCount()
                    : c.getGoodsCount(gt) > best.getGoodsCount(gt))) best = c;
        }
        return best;
    }

    /** Sailing turns from the first human's nearest colony. */
    private int sailTurns(Colony c, List<String> audience) {
        for (String a : audience) {
            final Player p = game().getPlayerByNationId(a);
            if (p == null || p.isAI()) continue;
            int best = Integer.MAX_VALUE;
            for (Colony o : p.getColonyList()) {
                best = Math.min(best, o.getTile().getDistanceTo(c.getTile()));
            }
            if (best < Integer.MAX_VALUE) {
                return (best + SHIP_TILES - 1) / SHIP_TILES;
            }
        }
        return 2;
    }

    private String id(int now) {
        return "offer:" + me().getNationId() + ":" + now;
    }


    // Offers the players post

    /**
     * Is an offer a player posts possible?
     *
     * @param p The player.
     * @param o The {@code Offer}.
     * @return Null if fine, else why not.
     */
    public static String checkPosted(Player p, Offer o) {
        if (o.kind != Offer.Kind.WANT && o.kind != Offer.Kind.SELL) {
            return "Bad offer kind: " + o.kind;
        }
        if (o.amount <= 0 || o.amount > 300 || o.gold <= 0) {
            return "Bad offer terms";
        }
        final GoodsType gt = p.getSpecification().getGoodsType(o.goods);
        final Colony c = p.getGame().getFreeColGameObject(o.place, Colony.class);
        if (gt == null || c == null || c.getOwner() != p
            || !c.isConnectedPort()) return "Bad offer place or goods";
        if (o.kind == Offer.Kind.SELL && c.getGoodsCount(gt) < o.amount) {
            return "Not enough goods at " + c.getName();
        }
        if (o.kind == Offer.Kind.WANT && p.getGold() < o.gold) {
            return "Not enough gold";
        }
        return null;
    }

    /**
     * Weigh the offers players posted: take one that gains us enough,
     * if a ship is free, and send it.
     */
    private void considerPosted(Outlook outlook, ChangeSet cs, LogBuilder lb) {
        for (Offer o : new ArrayList<>(ai.getOffers())) {
            if (me().getNationId().equals(o.from) || o.state != Offer.State.OPEN
                || o.kind.envoy()) {
                continue;
            }
            final Player h = game().getPlayerByNationId(o.from);
            final NationView v = (h == null) ? null
                : outlook.view.others.get(h.getNationId());
            if (v == null || v.atWar() || v.opinion <= Mood.NEUTRAL) continue;
            final GoodsType gt = game().getSpecification().getGoodsType(o.goods);
            final Colony at = game().getFreeColGameObject(o.place, Colony.class);
            if (gt == null || at == null || at.getOwner() != h) {
                o.state = Offer.State.CANCELLED;
                continue;
            }
            // Their SELL is our purchase, their WANT our sale.
            final boolean buy = o.kind == Offer.Kind.SELL;
            final int value = net.sf.freecol.common.diplomacy.DealValue.goods(
                outlook.view, o.goods, o.amount, buy);
            if (value == net.sf.freecol.common.diplomacy.DealValue.NO_OPINION) continue;
            final int gain = (buy) ? value - o.gold : o.gold + value;
            if (gain < POSTED_GAIN * o.gold) continue;
            if (buy && me().getGold() < o.gold) continue;
            Unit ship = null;
            for (Unit u : new ArrayList<>(me().getUnitSet())) {
                if (!freeShip(u)) continue;
                final Colony home = u.getTile().getColony();
                if (!buy && (home == null || home.getOwner() != me()
                             || home.getGoodsCount(gt) < o.amount)) continue;
                ship = u;
                break;
            }
            if (ship == null) continue;
            if (!buy) {
                ship.getTile().getColony().removeGoods(gt, o.amount);
                ship.addGoods(gt, o.amount);
            }
            final Offer trip = new Offer(id(now()) + ":for:" + o.id,
                (buy) ? Offer.Kind.SHIP_BUY : Offer.Kind.SHIP_SELL,
                me().getNationId(), o.goods, o.amount, o.gold, at.getId(),
                at.getName(), now(), 0);
            trip.audience.add(h.getNationId());
            ai.getOffers().add(trip);
            final AIUnit aiu = ai.getAIMain().getAIUnit(ship);
            aiu.changeMission(new TradeVoyageMission(ai.getAIMain(), aiu, at,
                                                     trip.id));
            takenEverywhere(o.id, me().getNationId());
            message(cs, h, "offer.postTaken", o, o.gold);
            trip.told.add(h.getNationId()); // the message above says it
            lb.add("\n  Takes ", h.getDebugName(), "'s offer: ", o);
            return; // one a turn
        }
    }

    /** Mark a posted offer taken in every computer nation's copy. */
    private void takenEverywhere(String id, String by) {
        for (Player p : game().getLiveEuropeanPlayerList()) {
            final net.sf.freecol.server.ai.AIPlayer other
                = ai.getAIMain().getAIPlayer(p);
            if (!(other instanceof EuropeanAIPlayer)) continue;
            for (Offer x : ((EuropeanAIPlayer)other).getOffers()) {
                if (x.id.equals(id) && x.state == Offer.State.OPEN) {
                    x.state = Offer.State.TAKEN;
                    x.takenBy = by;
                    x.takenTurn = now();
                }
            }
        }
    }


    // Price agreements (step 4)

    /** Turns between agreements we propose. */
    public static final int AGREEMENT_GAP = 20;

    /** Sale questions made this turn (added to the book after the loop). */
    private final List<Offer> visits = new ArrayList<>();

    /** What we get for one unit in Europe, after tax. */
    private int net(GoodsType gt) {
        final net.sf.freecol.common.model.Market m = me().getMarket();
        return (m == null) ? 0 : net.sf.freecol.common.diplomacy.PriceAgreement
            .net(m.getPaidForSale(gt), me().getTax());
    }

    /** A running or proposed agreement with a player for a good. */
    private Offer agreementWith(String player, String goods) {
        for (Offer o : ai.getOffers()) {
            if (o.kind == Offer.Kind.AGREEMENT && goods.equals(o.goods)
                && (o.state == Offer.State.OPEN || o.state == Offer.State.TAKEN)
                && (player.equals(o.takenBy) || o.audience.contains(player))) {
                return o;
            }
        }
        return null;
    }

    /**
     * The player proposes a price agreement: we take it if we still
     * make a profit selling the good in Europe, answer with our own
     * price if asked too much, and refuse when we are boycotted, the
     * good is worth nothing to us, or we do not like the player.
     *
     * @param human The player.
     * @param p The proposal (goods, price per unit, turns).
     * @param cs A {@code ChangeSet} to update.
     * @return The running agreement or our counter offer, or null.
     */
    public Offer considerAgreement(ServerPlayer human, Offer p, ChangeSet cs) {
        final GoodsType gt = (p.goods == null) ? null
            : game().getSpecification().getGoodsType(p.goods);
        if (gt == null) return null;
        final int now = now();
        final int turns = net.sf.freecol.common.diplomacy.PriceAgreement
            .turns(p.deadline);
        final NationView v = ai.getOutlook().view.others.get(human.getNationId());
        final Offer probe = new Offer("probe", Offer.Kind.AGREEMENT,
            me().getNationId(), gt.getId(), 0, p.gold, null, "", now, turns);
        String why = null;
        if (v == null || v.atWar() || v.opinion <= Mood.NEUTRAL) {
            why = "agreement.refusedFriends";
        } else if (agreementWith(human.getNationId(), gt.getId()) != null) {
            why = "agreement.refusedRunning";
        } else if (!me().canTrade(gt)) {
            why = "agreement.refusedBoycott";
        }
        final int netPrice = net(gt);
        final int counter = net.sf.freecol.common.diplomacy.PriceAgreement.counter(netPrice);
        if (why == null && counter < 1) why = "agreement.refusedWorthless";
        if (why != null) {
            message(cs, human, why, probe, p.gold);
            return null;
        }
        final boolean yes = net.sf.freecol.common.diplomacy.PriceAgreement
            .accepts(p.gold, netPrice);
        final Offer a = new Offer("agree:" + me().getNationId() + ":"
            + human.getNationId() + ":" + Action.shortId(gt.getId()) + ":" + now,
            Offer.Kind.AGREEMENT, me().getNationId(), gt.getId(), 0,
            (yes) ? p.gold : counter, null, "", now, turns);
        a.audience.add(human.getNationId());
        ai.getOffers().add(a);
        if (yes) {
            a.state = Offer.State.TAKEN;
            a.takenBy = human.getNationId();
            a.takenTurn = now;
            a.told.add(human.getNationId());
            message(cs, human, "agreement.accepted", a, a.gold);
        } else {
            // Our price: a dialog now.
            a.told.add(human.getNationId());
            cs.add(See.only(human), new OfferMessage(a));
            message(cs, human, "agreement.counter", a, a.gold);
        }
        return a;
    }

    /**
     * We propose an agreement for a good we keep needing, to the player
     * we like most, at a little under what it costs us in Europe.
     */
    private Offer agreementProposal(Outlook outlook) {
        final int now = now();
        for (Offer o : ai.getOffers()) {
            if (o.kind == Offer.Kind.AGREEMENT && me().getNationId().equals(o.from)
                && now - o.posted < AGREEMENT_GAP) return null;
        }
        final net.sf.freecol.common.model.Market m = me().getMarket();
        if (m == null) return null;
        String to = null;
        for (String a : audience(outlook)) {
            final Player p = game().getPlayerByNationId(a);
            if (p != null && !p.isAI()) {
                to = a;
                break;
            }
        }
        if (to == null) return null;
        for (ScoredAction sa : outlook.ranked) {
            if (sa.action.kind != Action.Kind.BUY_GOODS
                || sa.total < Diplomat.SMALL_THRESHOLD) continue;
            final GoodsType gt = game().getSpecification()
                .getGoodsType(sa.action.goods);
            if (gt == null || agreementWith(to, gt.getId()) != null) continue;
            final int price = (int)Math.round(net.sf.freecol.common.diplomacy
                .PriceAgreement.NEED_SHARE * m.getCostToBuy(gt));
            if (price < 1) continue;
            final Offer o = new Offer("agree:" + me().getNationId() + ":" + to
                + ":" + Action.shortId(gt.getId()) + ":" + now,
                Offer.Kind.AGREEMENT, me().getNationId(), gt.getId(), 0,
                price, null, "", now, net.sf.freecol.common.diplomacy
                    .PriceAgreement.TURNS);
            o.audience.add(to);
            return o;
        }
        return null;
    }

    /**
     * A running agreement: over when its time is up or at war; a ship
     * of the player next to one of our coastal colonies with the good
     * is asked how many to sell.
     */
    private void followAgreement(Offer a, ChangeSet cs, LogBuilder lb) {
        final Player human = game().getPlayerByNationId(a.takenBy);
        final GoodsType gt = game().getSpecification().getGoodsType(a.goods);
        if (human == null || gt == null) {
            a.state = Offer.State.CANCELLED;
            return;
        }
        if (me().getStance(human) == Stance.WAR) {
            a.state = Offer.State.CANCELLED;
            message(cs, human, "agreement.war", a, a.gold);
            return;
        }
        final int now = now();
        if (a.turnsLeft(now) <= 0) {
            a.state = Offer.State.DONE;
            message(cs, human, "agreement.over", a, a.paid);
            return;
        }
        for (Colony c : me().getColonyList()) {
            // Ships and (mod) wagon trains next to any of our colonies.
            for (Tile t : c.getTile().getSurroundingTiles(1)) {
                for (Unit u : t.getUnitList()) {
                    if (u.getOwner() != human || !u.canCarryGoods()
                        || u.getGoodsCount(gt) <= 0 || askedLately(a, u)) continue;
                    final Offer q = saleVisit(a, human, u, c, cs);
                    visits.add(q);
                    lb.add("\n  Agreement: ", u, " at ", c.getName(), ", takes ", q.amount);
                }
            }
        }
    }

    /**
     * A carrier of the player with goods under a price agreement stands
     * next to one of our colonies: the "how many?" question (a SALE
     * offer), or "full" when we have no room.
     */
    private Offer saleVisit(Offer a, Player human, Unit u, Colony c,
                            ChangeSet cs) {
        final GoodsType gt = game().getSpecification().getGoodsType(a.goods);
        final int now = now();
        final int room = net.sf.freecol.common.diplomacy
            .PriceAgreement.room(c.getWarehouseCapacity(),
                c.getGoodsCount(gt), me().getGold(), a.gold);
        final Offer q = new Offer("sale#" + a.id + "#" + u.getId()
            + "#" + now, Offer.Kind.SALE, me().getNationId(),
            gt.getId(), room, a.gold, c.getId(), c.getName(), now, 0);
        q.audience.add(human.getNationId());
        if (room <= 0) { // Full: they say so, no question
            q.state = Offer.State.DECLINED;
            q.told.add(human.getNationId());
            message(cs, human, "agreement.full", q, a.gold);
        }
        return q;
    }

    /** Did we ask this ship about this agreement in the last two turns? */
    private boolean askedLately(Offer a, Unit u) {
        final String head = "sale#" + a.id + "#" + u.getId() + "#";
        for (List<Offer> l : java.util.Arrays.asList(ai.getOffers(), visits)) {
            for (Offer o : l) {
                if (o.id.startsWith(head) && (o.state == Offer.State.OPEN
                                              || now() - o.posted < 2)) return true;
            }
        }
        return false;
    }

    /** The player says how many to sell at this visit. */
    private boolean sell(Offer q, ServerPlayer human, boolean accept,
                         int amount, ChangeSet cs) {
        final String[] f = q.id.split("#");
        Offer a = null;
        if (f.length == 4) {
            for (Offer x : ai.getOffers()) if (x.id.equals(f[1])) a = x;
        }
        final Unit u = (f.length == 4)
            ? game().getFreeColGameObject(f[2], Unit.class) : null;
        final Colony c = game().getFreeColGameObject(q.place, Colony.class);
        final GoodsType gt = game().getSpecification().getGoodsType(q.goods);
        if (!accept || amount <= 0 || a == null || a.state != Offer.State.TAKEN
            || u == null || u.getOwner() != human || c == null
            || c.getOwner() != me() || gt == null || u.getTile() == null
            || u.getTile().getDistanceTo(c.getTile()) > 1) {
            q.state = Offer.State.DECLINED;
            return true;
        }
        final int room = net.sf.freecol.common.diplomacy.PriceAgreement.room(
            c.getWarehouseCapacity(), c.getGoodsCount(gt), me().getGold(), a.gold);
        final int n = Math.min(Math.min(amount, u.getGoodsCount(gt)), room);
        if (n <= 0) {
            q.state = Offer.State.DECLINED;
            message(cs, human, "agreement.full", q, a.gold);
            return true;
        }
        final int pay = n * a.gold;
        u.removeGoods(gt, n);
        c.addGoods(gt, n);
        gold(me(), -pay, cs);
        gold(human, pay, cs);
        cs.add(See.only(human), u);
        a.delivered += n;
        a.paid += pay;
        q.state = Offer.State.DONE;
        q.paid = pay;
        q.delivered = n;
        remember(Memory.Kind.TRADE, human);
        final Offer done = new Offer(q.id, q.kind, q.from, q.goods, n, a.gold,
            q.place, q.placeName, q.posted, 0);
        message(cs, human, "agreement.sold", done, pay);
        return true;
    }


    // Answers

    /**
     * The player answers our call to arms: join the war, or leave the
     * alliance (and we remember).
     */
    private boolean answerCall(Offer o, ServerPlayer human, boolean accept,
                               ChangeSet cs) {
        final Player enemy = game().getPlayerByNationId(o.against);
        if (accept && enemy != null && human.getStance(enemy) != Stance.WAR) {
            o.state = Offer.State.DONE;
            human.csChangeStance(Stance.WAR, enemy, true, cs);
            remember(Memory.Kind.HELPED_IN_WAR, human);
            return true;
        }
        if (accept) { // already at war with them: nothing to do
            o.state = Offer.State.DONE;
            return true;
        }
        o.state = Offer.State.DECLINED;
        if (me().getStance(human) == Stance.ALLIANCE) {
            ((ServerPlayer)me()).csChangeStance(Stance.PEACE, human, true, cs);
        }
        remember(Memory.Kind.DEAL_BROKEN, human);
        return true;
    }

    /**
     * A human player answers an offer.
     *
     * @param human The player.
     * @param id The offer id.
     * @param accept True to accept.
     * @param cs A {@code ChangeSet} to update.
     * @return True if the offer was ours and open to this player.
     */
    public boolean answer(ServerPlayer human, String id, boolean accept,
                          ChangeSet cs) {
        return answer(human, id, accept, -1, cs);
    }

    /**
     * A human player answers an offer, with an amount (a sale under a
     * price agreement).
     *
     * @param human The player.
     * @param id The offer id.
     * @param accept True to accept.
     * @param amount How many goods to sell, -1 if not asked.
     * @param cs A {@code ChangeSet} to update.
     * @return True if the offer was ours and open to this player.
     */
    public boolean answer(ServerPlayer human, String id, boolean accept,
                          int amount, ChangeSet cs) {
        Offer o = null;
        for (Offer x : ai.getOffers()) if (x.id.equals(id)) o = x;
        if (o == null || !o.visibleTo(human.getNationId(), now())) return false;
        if (o.kind == Offer.Kind.SALE) return sell(o, human, accept, amount, cs);
        if (o.kind == Offer.Kind.LOAN || o.kind == Offer.Kind.LOAN_DEFAULT) {
            return new LoanDesk(ai).answer(o, human, accept, amount, cs);
        }
        if (o.kind == Offer.Kind.TERMS) {
            return new EnvoyDesk(ai).answerTerms(o, human, accept, cs);
        }
        if (o.kind == Offer.Kind.AGREEMENT) {
            if (accept) {
                o.state = Offer.State.TAKEN;
                o.takenBy = human.getNationId();
                o.takenTurn = now();
                message(cs, human, "agreement.started", o, o.gold);
            } else {
                o.state = Offer.State.DECLINED;
            }
            return true;
        }
        if (o.kind == Offer.Kind.CALL_TO_ARMS) {
            return answerCall(o, human, accept, cs);
        }
        if (o.kind == Offer.Kind.ULTIMATUM) {
            final GrievancePlanner gp = new GrievancePlanner(ai);
            final net.sf.freecol.common.diplomacy.Grievance g
                = ai.getGrievances().get(human.getNationId());
            if (g == null || g.stage
                != net.sf.freecol.common.diplomacy.Grievance.Stage.ULTIMATUM) {
                o.state = Offer.State.EXPIRED;
            } else if (accept && human.getGold() >= g.demand) {
                o.state = Offer.State.DONE;
                o.takenBy = human.getNationId();
                o.takenTurn = now();
                o.paid = g.demand;
                gp.settle(g, human, cs);
            } else {
                o.state = Offer.State.DECLINED;
                gp.refused(human);
                message(cs, human, "grievance.refused", o, o.gold);
            }
            return true;
        }
        if (!accept) {
            if (o.kind.direct()) {
                o.state = Offer.State.DECLINED;
                remember(Memory.Kind.REFUSED_OFFER, human);
            }
            // A notice declined stays up for the others.
            return true;
        }
        if (o.kind == Offer.Kind.JOINT_WAR) {
            final Player z = game().getPlayerByNationId(o.against);
            if (z == null || me().getStance(z) != Stance.WAR) {
                o.state = Offer.State.EXPIRED;
                return true;
            }
            new AlliancePlanner(ai).accept(o, human, z, cs);
            return true;
        }
        if (o.kind == Offer.Kind.COALITION) {
            o.state = Offer.State.DONE;
            for (net.sf.freecol.common.diplomacy.Coalition c : ai.getCoalitions()) {
                if (c.target.equals(o.against)) {
                    new AlliancePlanner(ai).join(c, human, cs);
                }
            }
            return true;
        }
        if (o.kind == Offer.Kind.BUYOUT) {
            final net.sf.freecol.common.diplomacy.Coalition c
                = AlliancePlanner.coalitionOf(ai, me().getNationId(),
                                              human.getNationId());
            if (c == null) {
                o.state = Offer.State.EXPIRED;
                return true;
            }
            o.state = Offer.State.DONE;
            o.takenBy = human.getNationId();
            o.takenTurn = now();
            final int gold = Math.min(o.gold, Math.max(0, me().getGold()));
            gold(me(), -gold, cs);
            gold(human, gold, cs);
            o.paid = gold;
            AlliancePlanner.of(ai, c).leave(c, human, cs);
            return true;
        }
        if (o.kind == Offer.Kind.ALLIANCE) {
            o.state = Offer.State.DONE;
            if (me().getStance(human) == Stance.PEACE) {
                ((ServerPlayer)me()).csChangeStance(Stance.ALLIANCE, human, true, cs);
            }
            return true;
        }
        if (o.kind == Offer.Kind.PEACE) {
            o.state = Offer.State.DONE;
            if (me().getStance(human) == Stance.WAR) {
                ((ServerPlayer)me()).csChangeStance(Stance.PEACE, human, true, cs);
            }
            if (o.amount > 0) new LoanDesk(ai).cancelForPeace(human, cs);
            final int gold = Math.min(o.gold, Math.max(0, me().getGold()));
            gold(me(), -gold, cs);
            gold(human, gold, cs);
            o.paid = gold;
            return true;
        }
        o.state = Offer.State.TAKEN;
        o.takenBy = human.getNationId();
        o.takenTurn = now();
        message(cs, human, (o.kind == Offer.Kind.WANT) ? "offer.promised"
            : "offer.promisedFetch", o, o.gold);
        return true;
    }
}
