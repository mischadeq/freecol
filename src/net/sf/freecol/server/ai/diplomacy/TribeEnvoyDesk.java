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
import java.util.List;
import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.EnvoyRules;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.diplomacy.TribeEnvoyRules;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeSet.See;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.NativeAIPlayer;
import net.sf.freecol.server.model.LandClaims;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * The player's envoys to a tribe (step 8.3b, mod).  Like the nations'
 * {@link EnvoyDesk}: sent in the player's turn with the gold held,
 * answered on the tribe's turn, one per 5 turns.  The tribe judges by
 * {@link TribeEnvoyRules}: survival first.  Its terms may carry
 * conditions: give back the land taken (the tiles become the tribe's
 * again), abandon a colony on taken land (it is disbanded, the
 * colonists leave as free units), pull the soldiers out of its borders
 * (peace starts the turn none is inside).
 */
public final class TribeEnvoyDesk {

    private static final Logger logger = Logger.getLogger(TribeEnvoyDesk.class.getName());

    /** Tiles from a village within which the player's soldiers count. */
    public static final int NEAR = 4;
    /** Turns a lost village counts as "lately". */
    public static final int LATELY = 20;
    /** Turns a tribe waits for the soldiers to leave. */
    public static final int WITHDRAW_TURNS = 10;

    private final NativeAIPlayer ai;


    public TribeEnvoyDesk(NativeAIPlayer ai) {
        this.ai = ai;
    }

    private Player me() {
        return ai.getPlayer();
    }

    private int now() {
        return ai.getGame().getTurn().getNumber();
    }

    /**
     * Does a proposal fit how things stand with the tribe?
     *
     * @param kind The envoy kind.
     * @param stance The tribe's stance towards the player.
     * @return True if so.
     */
    public static boolean fits(Offer.Kind kind, Stance stance) {
        switch (kind) {
        case ENVOY_PEACE:
            return stance == Stance.WAR || stance == Stance.CEASE_FIRE;
        case ENVOY_GIFT:
            return stance != Stance.UNCONTACTED;
        default:
            return false;
        }
    }


    // Sending

    /**
     * The player sends an envoy to the tribe.
     *
     * @param human The player.
     * @param kind The envoy kind.
     * @param gold The gold he sends.
     * @param cs A {@code ChangeSet} to update.
     * @return Null if sent, else why not (a message key).
     */
    public String send(ServerPlayer human, Offer.Kind kind, int gold,
                       ChangeSet cs) {
        if (kind == null || !kind.envoy()
            || kind == Offer.Kind.ENVOY_ALLIANCE) return "envoy.error.kind";
        if (!me().isIndian() || human.isAI()) return "envoy.error.nation";
        if (!me().hasContacted(human)) return "envoy.error.contact";
        if (EnvoyRules.waitLeft(EnvoyDesk.lastSent(ai.getOffers(),
                    human.getNationId()), now()) > 0) {
            return "envoy.error.wait";
        }
        if (!fits(kind, me().getStance(human))) return "envoy.error.fits";
        if (gold < 0 || gold > human.getGold()
            || (kind == Offer.Kind.ENVOY_GIFT && gold <= 0)) {
            return "envoy.error.gold";
        }
        final Offer o = new Offer("envoy:" + human.getNationId() + ":"
            + me().getNationId() + ":" + now(), kind, human.getNationId(),
            null, 0, gold, null, "", now(), 0);
        o.audience.add(me().getNationId());
        ai.getOffers().add(o);
        gold(human, -gold, cs);
        logger.info(human.getName() + " sends an envoy to " + me().getName()
            + ": " + o);
        return null;
    }


    // Answering (the tribe's turn)

    /**
     * Answer the envoys waiting, and see whether soldiers have left
     * where peace waits for it.
     *
     * @param cs A {@code ChangeSet} to update.
     * @param lb A {@code LogBuilder} to log to.
     */
    public void answerAll(ChangeSet cs, LogBuilder lb) {
        for (Offer o : new ArrayList<>(ai.getOffers())) {
            final Player h = ai.getGame().getPlayerByNationId(
                (o.kind == Offer.Kind.TERMS) ? o.takenBy : o.from);
            if (o.kind == Offer.Kind.TERMS && o.state == Offer.State.TAKEN) {
                if (!(h instanceof ServerPlayer)) {
                    o.state = Offer.State.CANCELLED;
                } else if (!troopsInside(h)) {
                    o.state = Offer.State.DONE;
                    makePeace((ServerPlayer)h, cs);
                } else if (now() - o.takenTurn > WITHDRAW_TURNS) {
                    o.state = Offer.State.FAILED;
                    message(cs, h, "envoy.tribe.withdrawFailed", 0);
                }
                continue;
            }
            if (!o.kind.envoy() || o.state != Offer.State.OPEN) continue;
            if (!(h instanceof ServerPlayer) || h.isDead()) {
                o.state = Offer.State.CANCELLED;
                continue;
            }
            answer(o, (ServerPlayer)h, cs, lb);
        }
    }

    /**
     * What goes into judging an envoy.
     *
     * @param o The envoy.
     * @param h The player.
     * @return The {@code TribeEnvoyRules.Input}.
     */
    TribeEnvoyRules.Input input(Offer o, Player h) {
        final TribeEnvoyRules.Input in = new TribeEnvoyRules.Input();
        in.kind = o.kind;
        in.gold = o.gold;
        in.fits = fits(o.kind, me().getStance(h));
        in.braves = braves();
        in.soldiers = soldiersNear(h);
        in.lostVillage = lostVillage(h);
        in.tension = me().getTension(h).getValue();
        in.opinion = TribePlanner.opinion(ai, h);
        in.takenLand = !LandClaims.takenTiles(me(), h).isEmpty();
        in.colonyOnTakenLand = colonyOnTakenLand(h) != null;
        in.troopsInside = troopsInside(h);
        return in;
    }

    private void answer(Offer o, ServerPlayer h, ChangeSet cs,
                        LogBuilder lb) {
        final TribeEnvoyRules.Answer a = TribeEnvoyRules.judge(input(o, h));
        lb.add("\n  Envoy from ", h.getName(), ": ", o, " -> ", a);
        logger.info(me().getName() + " answers the envoy of " + h.getName()
            + ": " + o + " -> " + a);
        switch (a.verdict) {
        case ACCEPT:
            o.state = Offer.State.DONE;
            o.paid = o.gold;
            gold(me(), o.gold, cs);
            if (o.kind == Offer.Kind.ENVOY_GIFT) {
                gift(h, o.gold, cs);
            } else {
                makePeace(h, cs);
                if (a.standing == TribeEnvoyRules.Standing.LOSING) {
                    message(cs, h, "envoy.tribe.humble", 0);
                }
            }
            break;
        case COUNTER:
            o.state = Offer.State.DECLINED;
            gold(h, o.gold, cs);
            final Colony c = ((a.conditions & TribeEnvoyRules.COLONY) != 0)
                ? colonyOnTakenLand(h) : null;
            final Offer t = new Offer("terms:" + me().getNationId() + ":"
                + h.getNationId() + ":" + now(), Offer.Kind.TERMS,
                me().getNationId(), o.kind.name(), a.conditions, a.price,
                (c == null) ? null : c.getId(),
                (c == null) ? "" : c.getName(), now(), 0);
            t.audience.add(h.getNationId());
            ai.getOffers().add(t); // shown by TribePlanner.tellOffers
            break;
        default:
            o.state = Offer.State.DECLINED;
            gold(h, o.gold, cs);
            message(cs, h, "envoy.tribe.refused." + a.reason.name(), 0);
            break;
        }
    }

    /**
     * The player answers the tribe's terms: pays, gives back the land,
     * abandons the colony; peace now, or once his soldiers have left.
     *
     * @param o The {@code TERMS} offer.
     * @param h The player.
     * @param accept True if he takes them.
     * @param cs A {@code ChangeSet} to update.
     * @return True (handled).
     */
    public boolean answerTerms(Offer o, ServerPlayer h, boolean accept,
                               ChangeSet cs) {
        if (!accept) {
            o.state = Offer.State.DECLINED;
            return true;
        }
        if (!fits(Offer.Kind.ENVOY_PEACE, me().getStance(h))) {
            o.state = Offer.State.EXPIRED;
            message(cs, h, "envoy.terms.over", o.gold);
            return true;
        }
        if (h.getGold() < o.gold) {
            o.state = Offer.State.DECLINED;
            message(cs, h, "envoy.terms.noGold", o.gold);
            return true;
        }
        gold(h, -o.gold, cs);
        gold(me(), o.gold, cs);
        o.paid = o.gold;
        o.takenBy = h.getNationId();
        o.takenTurn = now();
        if ((o.amount & TribeEnvoyRules.COLONY) != 0 && o.place != null) {
            final Colony c = ai.getGame().getFreeColGameObject(o.place,
                                                               Colony.class);
            if (c != null && c.getOwner() == h) abandon(c, h, cs);
        }
        if ((o.amount & TribeEnvoyRules.LAND) != 0) giveBackLand(h, cs);
        if ((o.amount & TribeEnvoyRules.TROOPS) != 0 && troopsInside(h)) {
            o.state = Offer.State.TAKEN; // peace once they have left
            message(cs, h, "envoy.tribe.withdraw", 0);
        } else {
            o.state = Offer.State.DONE;
            makePeace(h, cs);
        }
        return true;
    }


    // Effects

    /**
     * Peace with the player: the stance, and the anger of the tribe and
     * its villages let go down to {@link TribeEnvoyRules#FREE_TENSION}.
     */
    void makePeace(ServerPlayer h, ChangeSet cs) {
        final ServerPlayer me = (ServerPlayer)me();
        final Tension t = me.getTension(h);
        if (t.getValue() > TribeEnvoyRules.FREE_TENSION) {
            t.setValue(TribeEnvoyRules.FREE_TENSION);
        }
        for (IndianSettlement is : me.getIndianSettlementList()) {
            final Tension a = is.getAlarm(h);
            if (a != null && a.getValue() > TribeEnvoyRules.FREE_TENSION) {
                a.setValue(TribeEnvoyRules.FREE_TENSION);
                cs.add(See.perhaps(), is);
            }
        }
        if (me.getStance(h) != Stance.PEACE) {
            me.csChangeStance(Stance.PEACE, h, true, cs);
        }
        message(cs, h, "envoy.tribe.peace", 0);
    }

    private void gift(ServerPlayer h, int gold, ChangeSet cs) {
        ((ServerPlayer)me()).csModifyTension(h,
            -EnvoyRules.giftTension(gold), cs);
        ai.getMemories().add(new Memory(Memory.Kind.GIFT, h.getNationId(),
                                        now(), 1));
        message(cs, h, "envoy.tribe.gift", gold);
    }

    /** The land taken from us goes back to the nearest of our villages. */
    void giveBackLand(Player h, ChangeSet cs) {
        for (Tile t : LandClaims.takenTiles(me(), h)) {
            if (t.hasSettlement()) continue; // a colony: only by abandoning
            final Colony c = (t.getOwningSettlement() instanceof Colony)
                ? (Colony)t.getOwningSettlement() : null;
            if (c != null && c.getColonyTile(t) != null) {
                for (Unit u : new ArrayList<>(c.getColonyTile(t).getUnitList())) {
                    u.setLocation(c.getTile());
                    u.setState(Unit.UnitState.ACTIVE);
                }
            }
            final IndianSettlement is = nearestVillage(t);
            t.cacheUnseen();
            t.changeOwnership(me(), is);
            LandClaims.forget(me(), t);
            cs.add(See.perhaps(), t);
            if (c != null) cs.add(See.only(h), c);
        }
    }

    /** A colony is disbanded: its colonists leave as free units. */
    void abandon(Colony c, ServerPlayer h, ChangeSet cs) {
        final Tile tile = c.getTile();
        for (Unit u : new ArrayList<>(c.getUnitList())) {
            u.setLocation(tile);
            u.setState(Unit.UnitState.ACTIVE);
        }
        message(cs, h, "envoy.tribe.abandoned", 0, c.getName());
        h.csDisposeSettlement(c, cs);
        final IndianSettlement is = nearestVillage(tile);
        tile.cacheUnseen();
        tile.changeOwnership(me(), is);
        LandClaims.forget(me(), tile);
        cs.add(See.perhaps(), tile);
        h.invalidateCanSeeTiles();
    }


    // What the tribe sees

    private int braves() {
        int n = 0;
        for (Unit u : me().getUnitSet()) {
            if (!u.isNaval() && u.getType().getOffence() > 0) n++;
        }
        return n;
    }

    private int soldiersNear(Player h) {
        int n = 0;
        for (Unit u : h.getUnitSet()) {
            if (u.isNaval() || !u.isOffensiveUnit() || u.getTile() == null) continue;
            for (IndianSettlement is : me().getIndianSettlementList()) {
                if (is.getTile().getDistanceTo(u.getTile()) <= NEAR) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    boolean troopsInside(Player h) {
        for (Unit u : h.getUnitSet()) {
            if (u.isNaval() || !u.isOffensiveUnit()) continue;
            final Tile t = u.getTile();
            if (t != null && t.getOwner() == me()) return true;
        }
        return false;
    }

    private boolean lostVillage(Player h) {
        for (Memory m : ai.getMemories().list()) {
            if (m.kind == Memory.Kind.SETTLEMENT_DESTROYED
                && h.getNationId().equals(m.about)
                && now() - m.turn <= LATELY) return true;
        }
        return false;
    }

    /** The player's colony on land taken from us nearest a village. */
    Colony colonyOnTakenLand(Player h) {
        Colony best = null;
        int bestD = Integer.MAX_VALUE;
        for (Colony c : h.getColonyList()) {
            if (!LandClaims.taken(me(), c.getTile(), h)) continue;
            final IndianSettlement is = nearestVillage(c.getTile());
            final int d = (is == null) ? Integer.MAX_VALUE
                : is.getTile().getDistanceTo(c.getTile());
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private IndianSettlement nearestVillage(Tile t) {
        IndianSettlement best = null;
        int bestD = Integer.MAX_VALUE;
        for (IndianSettlement is : me().getIndianSettlementList()) {
            final int d = is.getTile().getDistanceTo(t);
            if (d < bestD) {
                bestD = d;
                best = is;
            }
        }
        return best;
    }


    // Helpers

    private static void gold(Player p, int amount, ChangeSet cs) {
        if (amount == 0) return;
        p.modifyGold(amount);
        cs.addPartial(See.only(p), p, "gold", String.valueOf(p.getGold()),
                      "score", String.valueOf(p.getScore()));
    }

    private void message(ChangeSet cs, Player to, String key, int gold) {
        message(cs, to, key, gold, "");
    }

    private void message(ChangeSet cs, Player to, String key, int gold,
                         String colony) {
        cs.addMessage(to, new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, key, me())
            .addStringTemplate("%nation%", me().getNationLabel())
            .addAmount("%gold%", gold)
            .addName("%colony%", colony));
    }
}
