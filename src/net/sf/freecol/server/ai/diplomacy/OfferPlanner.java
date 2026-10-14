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
        final int now = now();
        final List<Offer> book = ai.getOffers();
        for (Iterator<Offer> it = book.iterator(); it.hasNext();) {
            final Offer o = it.next();
            if (o.state.over() && now - o.posted > KEEP) it.remove();
        }
        for (Offer o : book) {
            if (!me.getNationId().equals(o.from)) {
                if (o.stale(now)) o.state = Offer.State.EXPIRED;
            } else if (o.state == Offer.State.TAKEN) {
                followUp(o, cs);
            } else if (o.stale(now)) {
                o.state = Offer.State.EXPIRED;
            }
        }
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
            o.paid = price;
            final boolean late = price < o.gold;
            o.state = (late) ? Offer.State.LATE : Offer.State.DONE;
            remember(late ? Memory.Kind.DEAL_LATE : Memory.Kind.DEAL_KEPT, taker);
            remember(Memory.Kind.TRADE, taker);
            message(cs, taker, (o.kind == Offer.Kind.WANT)
                ? ((late) ? "offer.deliveredLate" : "offer.delivered")
                : "offer.fetched", o, price);
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

    /**
     * Complete a deal if a ship of the taker is next to the colony with
     * the goods (or the room and the gold).
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
                if (u.getOwner() != taker || !u.isNaval()
                    || !u.canCarryGoods()) continue;
                if (o.kind == Offer.Kind.WANT) {
                    if (u.getGoodsCount(gt) < o.amount) continue;
                    final int pay = Math.min(price, Math.max(0, me.getGold()));
                    u.removeGoods(gt, o.amount);
                    colony.addGoods(gt, o.amount);
                    gold(me, -pay, cs);
                    gold(taker, pay, cs);
                } else {
                    if (u.getSpaceLeft() * 100 < o.amount
                        || taker.getGold() < price) continue;
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
            message(cs, h, (o.kind == Offer.Kind.PEACE) ? "offer.postedPeace"
                : (o.kind == Offer.Kind.WANT) ? "offer.postedWant"
                : "offer.postedSell", o, o.gold);
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
        if (o == null) o = voyage(outlook);
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
            Offer o = new Offer(id(now), Offer.Kind.PEACE, me().getNationId(),
                null, 0, gold, (capital == null) ? null : capital.getId(),
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
            if (me().getNationId().equals(o.from) || o.state != Offer.State.OPEN) {
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


    // Answers

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
        Offer o = null;
        for (Offer x : ai.getOffers()) if (x.id.equals(id)) o = x;
        if (o == null || !o.visibleTo(human.getNationId(), now())) return false;
        if (!accept) {
            if (o.kind == Offer.Kind.PEACE) {
                o.state = Offer.State.DECLINED;
                remember(Memory.Kind.REFUSED_OFFER, human);
            }
            // A notice declined stays up for the others.
            return true;
        }
        if (o.kind == Offer.Kind.PEACE) {
            o.state = Offer.State.DONE;
            if (me().getStance(human) == Stance.WAR) {
                ((ServerPlayer)me()).csChangeStance(Stance.PEACE, human, true, cs);
            }
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
