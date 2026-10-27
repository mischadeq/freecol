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
        case ENVOY_WAR:
            return stance == Stance.PEACE || stance == Stance.ALLIANCE;
        case ENVOY_RAID:
            return stance == Stance.ALLIANCE;
        default:
            return false;
        }
    }

    /** Braves a breakaway tribe sends at its protector's order, at most. */
    public static final int ORDER_PARTY = 5;

    /**
     * The colony a raid order is against, if the tribe would go: one of
     * an enemy of the player (not the tribe, not its ally), and the
     * player is the tribe's protector.
     *
     * @param h The player.
     * @param colonyId The colony id.
     * @return The {@code Colony}, or null.
     */
    public net.sf.freecol.common.model.Colony raidTarget(Player h, String colonyId) {
        if (!h.getNationId().equals(me().getProtector())) return null;
        final net.sf.freecol.common.model.Colony c = (colonyId == null) ? null
            : ai.getGame().getFreeColGameObject(colonyId,
                net.sf.freecol.common.model.Colony.class);
        if (c == null || c.isDisposed() || c.getOwner() == me()
            || h.getStance(c.getOwner()) != Stance.WAR
            || me().getStance(c.getOwner()) == Stance.ALLIANCE) return null;
        return c;
    }

    /**
     * Carry out a raid order: a war party against the colony (going to
     * war with its owner if not yet at war).
     */
    private void raid(Offer o, ServerPlayer h, ChangeSet cs, LogBuilder lb) {
        final net.sf.freecol.common.model.Colony c = raidTarget(h, o.against);
        if (c == null) {
            o.state = Offer.State.FAILED;
            message(cs, h, "envoy.tribe.raidGone", 0, o.placeName);
            return;
        }
        final Player x = c.getOwner();
        if (me().getStance(x) != Stance.WAR) goToWar(h, x, cs);
        final int n = ai.sendWarParty(x, c.getTile(), ORDER_PARTY);
        o.state = Offer.State.DONE;
        lb.add("\n  Raid order from ", h.getName(), ": ", n, " braves to ",
               c.getName());
        cs.addMessage(h, new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            (n > 0) ? "envoy.tribe.raid" : "envoy.tribe.raidNone", me())
            .addStringTemplate("%nation%", me().getNationLabel())
            .addAmount("%amount%", n)
            .addName("%colony%", c.getName())
            .addStringTemplate("%enemy%", x.getNationLabel()));
    }

    /**
     * The enemy of a war envoy (8.2), if the tribe could fight it: the
     * player is at war with it, it is not the tribe nor the tribe's ally.
     *
     * @param h The player.
     * @param against The enemy nation id.
     * @return The enemy, or null.
     */
    Player enemy(Player h, String against) {
        final Player x = (against == null) ? null
            : ai.getGame().getPlayerByNationId(against);
        if (x == null || x.isDead() || x == me() || x == h || x.isREF()
            || h.getStance(x) != Stance.WAR
            || me().getStance(x) == Stance.ALLIANCE
            || x.isUnknownEnemy() || x.isUndead()) return null;
        return x;
    }

    /**
     * What the tribe asks to fight the player's enemy.
     *
     * @param h The player.
     * @param x The enemy.
     * @return The price.
     */
    int warPrice(Player h, Player x) {
        return TribeEnvoyRules.warPrice(braves(), x.isEuropean(),
            x.getNationId().equals(ai.getRival()),
            me().getTension(h).getValue(), me().getTension(x).getValue());
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
        return send(human, kind, gold, null, cs);
    }

    /**
     * The player sends an envoy to the tribe.
     *
     * @param human The player.
     * @param kind The envoy kind.
     * @param gold The gold he sends.
     * @param against The enemy for {@code ENVOY_WAR}, else null.
     * @param cs A {@code ChangeSet} to update.
     * @return Null if sent, else why not (a message key).
     */
    public String send(ServerPlayer human, Offer.Kind kind, int gold,
                       String against, ChangeSet cs) {
        if (kind == null || !kind.envoy()
            || kind == Offer.Kind.ENVOY_ALLIANCE
            || kind == Offer.Kind.ENVOY_LEAVE) return "envoy.error.kind";
        if (!me().isIndian() || human.isAI()) return "envoy.error.nation";
        if (!me().hasContacted(human)) return "envoy.error.contact";
        if (EnvoyRules.waitLeft(EnvoyDesk.lastSent(ai.getOffers(),
                    human.getNationId()), now()) > 0) {
            return "envoy.error.wait";
        }
        if (!fits(kind, me().getStance(human))) return "envoy.error.fits";
        if (kind == Offer.Kind.ENVOY_WAR && enemy(human, against) == null) {
            return "envoy.error.enemy";
        }
        final net.sf.freecol.common.model.Colony raid
            = (kind == Offer.Kind.ENVOY_RAID) ? raidTarget(human, against) : null;
        if (kind == Offer.Kind.ENVOY_RAID && raid == null) {
            return "envoy.error.enemy";
        }
        if (gold < 0 || gold > human.getGold()
            || (kind == Offer.Kind.ENVOY_GIFT && gold <= 0)) {
            return "envoy.error.gold";
        }
        final Offer o = new Offer("envoy:" + human.getNationId() + ":"
            + me().getNationId() + ":" + now(), kind, human.getNationId(),
            null, 0, gold, null, (raid == null) ? "" : raid.getName(), now(), 0);
        o.against = (kind == Offer.Kind.ENVOY_WAR
            || kind == Offer.Kind.ENVOY_RAID) ? against : null;
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

    /**
     * Judge a war envoy (8.2).
     *
     * @param o The envoy.
     * @param h The player.
     * @return The {@code TribeEnvoyRules.Answer}.
     */
    TribeEnvoyRules.Answer judgeWar(Offer o, Player h) {
        final Player x = enemy(h, o.against);
        if (x == null || !fits(o.kind, me().getStance(h))) {
            return TribeEnvoyRules.notNow();
        }
        return TribeEnvoyRules.judgeWar(TribePlanner.opinion(ai, h),
            me().getStance(h) == Stance.ALLIANCE,
            me().getStance(x) == Stance.WAR, warPrice(h, x));
    }

    private void answer(Offer o, ServerPlayer h, ChangeSet cs,
                        LogBuilder lb) {
        if (o.kind == Offer.Kind.ENVOY_RAID) {
            raid(o, h, cs, lb);
            return;
        }
        final TribeEnvoyRules.Answer a = (o.kind == Offer.Kind.ENVOY_WAR)
            ? judgeWar(o, h) : TribeEnvoyRules.judge(input(o, h));
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
            } else if (o.kind == Offer.Kind.ENVOY_WAR) {
                goToWar(h, enemy(h, o.against), cs);
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
            t.against = o.against;
            t.audience.add(h.getNationId());
            ai.getOffers().add(t); // shown by TribePlanner.tellOffers
            break;
        default:
            o.state = Offer.State.DECLINED;
            gold(h, o.gold, cs);
            message(cs, h, "envoy.tribe.refused." + a.reason.name(), 0, "",
                    o.against);
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
        if (Offer.Kind.ENVOY_WAR.name().equals(o.goods)) {
            return answerWarTerms(o, h, cs);
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


    /** The player takes the tribe's price to fight for him (8.2). */
    private boolean answerWarTerms(Offer o, ServerPlayer h, ChangeSet cs) {
        final Player x = enemy(h, o.against);
        if (x == null || !fits(Offer.Kind.ENVOY_WAR, me().getStance(h))) {
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
        o.state = Offer.State.DONE;
        if ((o.amount & TribeEnvoyRules.ALLY) != 0
            && me().getStance(h) != Stance.ALLIANCE) {
            ((ServerPlayer)me()).csChangeStance(Stance.ALLIANCE, h, true, cs);
        }
        goToWar(h, x, cs);
        return true;
    }


    // Effects

    /** The tribe takes up the player's war. */
    void goToWar(ServerPlayer h, Player x, ChangeSet cs) {
        if (x == null) return;
        final ServerPlayer me = (ServerPlayer)me();
        if (me.getStance(x) != Stance.WAR) {
            me.csChangeStance(Stance.WAR, x, true, cs);
        }
        message(cs, h, "envoy.tribe.war", 0, "", x.getNationId());
    }

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
        message(cs, to, key, gold, colony, null);
    }

    private void message(ChangeSet cs, Player to, String key, int gold,
                         String colony, String against) {
        final ModelMessage m = new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, key, me())
            .addStringTemplate("%nation%", me().getNationLabel())
            .addAmount("%gold%", gold)
            .addName("%colony%", colony);
        final Player x = (against == null) ? null
            : ai.getGame().getPlayerByNationId(against);
        if (x != null) m.addStringTemplate("%enemy%", x.getNationLabel());
        cs.addMessage(to, m);
    }
}
