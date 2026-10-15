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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.Observed;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.diplomacy.Relations;
import net.sf.freecol.common.diplomacy.Snapshot;
import net.sf.freecol.common.diplomacy.TribeCharacter;
import net.sf.freecol.common.diplomacy.TribeRelations;
import net.sf.freecol.common.diplomacy.TurnDiff;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.NativeAIPlayer;


/**
 * The diplomacy of a native tribe (mod, step 7): it knows every other
 * tribe, has an old rival, remembers what Europeans did to it, and
 * (rarely) goes to war with its rival.
 */
public final class TribePlanner {

    /** Colonies this close (tiles) to a settlement take its land. */
    public static final int LAND_RADIUS = 2;

    /** What happened since last turn, worked out once for all tribes. */
    public static final class TurnRecord {
        public final int turn;
        public final Snapshot now;
        public final Snapshot before;
        public final List<Observed> diff;

        TurnRecord(int turn, Snapshot now, Snapshot before,
                   List<Observed> diff) {
            this.turn = turn;
            this.now = now;
            this.before = before;
            this.diff = diff;
        }
    }

    private static final Map<Game, TurnRecord> RECORDS
        = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * This turn's record, shared by the tribes.
     *
     * @param game The {@code Game}.
     * @return The {@code TurnRecord}.
     */
    public static TurnRecord record(Game game) {
        synchronized (RECORDS) {
            final int now = game.getTurn().getNumber();
            final TurnRecord old = RECORDS.get(game);
            if (old != null && old.turn == now) return old;
            final Snapshot s = Snapshot.of(game);
            final TurnRecord r = (old == null || old.turn > now)
                ? new TurnRecord(now, s, null, new ArrayList<>())
                : new TurnRecord(now, s, old.now,
                    TurnDiff.diff(old.now, s, Collections.emptySet()));
            RECORDS.put(game, r);
            return r;
        }
    }

    private final NativeAIPlayer ai;


    public TribePlanner(NativeAIPlayer ai) {
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

    /**
     * This turn's thinking, before the units move.
     *
     * @param lb A {@code LogBuilder} to log to.
     */
    public void plan(LogBuilder lb) {
        final net.sf.freecol.common.networking.ChangeSet cs
            = new net.sf.freecol.common.networking.ChangeSet();
        meetTribes(game());
        pickRival();
        remember();
        landTaken();
        rub();
        tribeStances(lb);
        answerCalls(lb);
        trackMissions();
        settlementsBreakOff(cs, lb);
        resent();
        takeBack(cs, lb);
        defendAllies(lb);
        tribeAlliances(cs, lb);
        allyWars(lb);
        callAllies(cs, lb);
        pontiac(lb);
        unrest(cs, lb);
        tellOffers(cs);
        ai.setMood(mood());
        ((net.sf.freecol.server.model.ServerGame)game()).sendToAll(cs);
    }


    // Knowing each other

    /**
     * Tribes have lived here forever: every pair of tribes knows each
     * other (at peace) from the start, also in an older save.
     *
     * @param game The {@code Game}.
     */
    public static void meetTribes(Game game) {
        final List<Player> tribes = game.getLiveNativePlayerList();
        for (Player a : tribes) {
            for (Player b : tribes) {
                if (a != b && a.getStance(b) == Stance.UNCONTACTED) {
                    a.setStance(b, Stance.PEACE);
                }
            }
        }
    }

    /** The old rival: the nearest tribe, picked once. */
    private void pickRival() {
        final String r = ai.getRival();
        final Player rp = (r == null) ? null : game().getPlayerByNationId(r);
        if (rp != null && !rp.isDead()) return;
        ai.setRival(nearestTribe(me()));
    }

    /**
     * The tribe whose settlements come nearest ours.
     *
     * @param me The tribe.
     * @return Its nation id, or null if alone.
     */
    public static String nearestTribe(Player me) {
        String best = null;
        int top = Integer.MAX_VALUE;
        for (Player p : me.getGame().getLiveNativePlayerList()) {
            if (p == me) continue;
            final int d = distance(me, p);
            if (d < top || (d == top && best != null
                            && p.getNationId().compareTo(best) < 0)) {
                top = d;
                best = p.getNationId();
            }
        }
        return best;
    }

    private static int distance(Player a, Player b) {
        int d = Integer.MAX_VALUE;
        for (IndianSettlement x : a.getIndianSettlementList()) {
            for (IndianSettlement y : b.getIndianSettlementList()) {
                d = Math.min(d, x.getTile().getDistanceTo(y.getTile()));
            }
        }
        return d;
    }

    /**
     * Pairs of settlements of two tribes near each other.
     *
     * @param a A tribe.
     * @param b Another tribe.
     * @return The number of borders.
     */
    public static int borders(Player a, Player b) {
        int n = 0;
        for (IndianSettlement x : a.getIndianSettlementList()) {
            for (IndianSettlement y : b.getIndianSettlementList()) {
                if (x.getTile().getDistanceTo(y.getTile()) <= TribeRelations.NEAR) n++;
            }
        }
        return n;
    }

    /**
     * Europeans two tribes both hate or both fight.
     *
     * @param a A tribe.
     * @param b Another tribe.
     * @return The number of shared enemies.
     */
    public static int sharedEnemies(Player a, Player b) {
        final int line = Tension.Level.DISPLEASED.getLimit();
        int n = 0;
        for (Player e : a.getGame().getLiveEuropeanPlayerList()) {
            if (e.isREF()) continue;
            final boolean ah = a.getStance(e) == Stance.WAR
                || a.getTension(e).getValue() >= line;
            final boolean bh = b.getStance(e) == Stance.WAR
                || b.getTension(e).getValue() >= line;
            if (ah && bh) n++;
        }
        return n;
    }


    // Opinions

    /**
     * What a tribe thinks of another nation: a tribe by the rules
     * between tribes, a European by tension and memories.
     *
     * @param tribe The tribe's AI player.
     * @param other The other nation.
     * @return The opinion, -100..100.
     */
    public static double opinion(NativeAIPlayer tribe, Player other) {
        final Player me = tribe.getPlayer();
        final int now = me.getGame().getTurn().getNumber();
        final int tension = me.getTension(other).getValue();
        final List<Memory> mem = tribe.getMemories().list();
        if (other.isIndian()) {
            return TribeRelations.opinion(tension, mem, other.getNationId(),
                now, other.getNationId().equals(tribe.getRival()),
                borders(me, other), sharedEnemies(me, other));
        }
        return Relations.opinion(tension, mem, other.getNationId(), now);
    }


    // Memories

    private void remember() {
        final TurnRecord r = record(game());
        if (r.before == null || ai.getRemembered() >= r.turn) return;
        ai.setRemembered(r.turn);
        TribeRelations.remember(ai.getMemories(), me().getNationId(),
                                ai.getRival(), r.diff, r.before, r.before.turn);
        ai.getMemories().fade(now());
    }

    /** New colonies on our lands are remembered. */
    private void landTaken() {
        final Set<String> near = new HashSet<>();
        final Map<String, String> owner = new java.util.HashMap<>();
        for (Player p : game().getLiveEuropeanPlayerList()) {
            for (Colony c : p.getColonyList()) {
                for (IndianSettlement is : me().getIndianSettlementList()) {
                    if (c.getTile().getDistanceTo(is.getTile()) <= LAND_RADIUS) {
                        near.add(c.getId());
                        owner.put(c.getId(), p.getNationId());
                        break;
                    }
                }
            }
        }
        final Set<String> old = ai.getNearColonies();
        if (old != null) {
            for (String id : near) {
                if (!old.contains(id)) {
                    ai.getMemories().add(new Memory(Memory.Kind.LAND_TAKEN,
                                                    owner.get(id), now(), 1));
                }
            }
        }
        ai.setNearColonies(near);
    }

    /** An old rival next door: the tension grows slowly. */
    private void rub() {
        final String r = ai.getRival();
        final Player rp = (r == null) ? null : game().getPlayerByNationId(r);
        if (rp == null) return;
        final int add = TribeRelations.rub(ai.getCharacter(), borders(me(), rp));
        if (add > 0) me().getTension(rp).modify(add);
    }


    // War and peace between tribes

    private void tribeStances(LogBuilder lb) {
        final Player me = me();
        final TribeCharacter ch = ai.getCharacter();
        for (Player p : game().getLiveNativePlayerList()) {
            if (p == me) continue;
            final String id = p.getNationId();
            final double o = opinion(ai, p);
            final Stance s = me.getStance(p);
            if (s != Stance.WAR) {
                final boolean grudge = TribeRelations.grudge(
                    me.getTension(p).getValue(), ai.getMemories().list(),
                    id, now());
                // Hatred at the top (a war of old, or paid for by a
                // European) means war on any tribe (mod, step 6).
                final boolean hatred = me.getTension(p).getValue()
                    >= Tension.Level.HATEFUL.getLimit();
                if (hatred || TribeRelations.wantsWar(ch, o,
                        id.equals(ai.getRival()), grudge)) {
                    changeStance(Stance.WAR, p);
                    // Braves attack only who they are angry with.
                    final int hate = Tension.Level.ANGRY.getLimit();
                    for (Tension t : new Tension[] { me.getTension(p),
                                                     p.getTension(me) }) {
                        if (t.getValue() < hate) t.setValue(hate);
                    }
                    ai.getMemories().add(new Memory(Memory.Kind.WAR, id, now(), 1));
                    lb.add("\n  War on the old rival ", p.getDebugName());
                }
            } else if (TribeRelations.wantsPeace(ch, o, warTurns(id))) {
                changeStance(Stance.PEACE, p);
                me.getTension(p).setValue(me.getTension(p).getValue() / 2);
                lb.add("\n  Peace with ", p.getDebugName());
            }
        }
    }

    /** Turns since we went to war with a tribe (from our memory). */
    private int warTurns(String id) {
        int since = -1;
        for (Memory m : ai.getMemories().list()) {
            if (m.kind == Memory.Kind.WAR && id.equals(m.about)) {
                since = Math.max(since, m.turn);
            }
        }
        return (since < 0) ? Integer.MAX_VALUE : now() - since;
    }

    // Calls for help (7b)

    /** Turns before the same call is heard again. */
    public static final int CALL_GAP = 10;
    /** Kinship: what any tribe owes another, in opinion points. */
    public static final double KIN = 30;
    /** Help from this score up. */
    public static final double HELP_LINE = 10;
    /** A rival this low sides with the attacker... */
    public static final double SIDE_LINE = -50;
    /** ... if it likes the attacker this much. */
    public static final double SIDE_LIKE = 20;
    /** Braves sent to help. */
    public static final int WAR_PARTY = 2;
    /** Tiles from our settlements we watch enemy soldiers in. */
    public static final int WATCH = 6;

    /** A tribe calling for help against a European. */
    public static final class Call {
        public final Player caller, enemy;
        public final Tile where;
        public final String why;

        Call(Player caller, Player enemy, Tile where, String why) {
            this.caller = caller;
            this.enemy = enemy;
            this.where = where;
            this.why = why;
        }
    }

    /**
     * The calls for help this turn: a tribe that lost a settlement to
     * a European since last turn, or whose capital has enemy soldiers
     * next to it.
     *
     * @param game The {@code Game}.
     * @return The calls.
     */
    public static List<Call> calls(Game game) {
        final List<Call> ret = new ArrayList<>();
        final TurnRecord r = record(game);
        for (Observed o : r.diff) {
            if (o.what != Observed.What.DESTROY) continue;
            final Player x = game.getPlayerByNationId(o.other);
            final Player e = game.getPlayerByNationId(o.actor);
            if (x == null || e == null || !x.isIndian() || !e.isEuropean()
                || x.isDead()) continue;
            final IndianSettlement near = x.getIndianSettlementList().isEmpty()
                ? null : x.getIndianSettlementList().get(0);
            ret.add(new Call(x, e, (near == null) ? null : near.getTile(),
                             o.detail));
        }
        for (Player x : game.getLiveNativePlayerList()) {
            for (IndianSettlement is : x.getIndianSettlementList()) {
                if (!is.isCapital()) continue;
                for (Tile t : is.getTile().getSurroundingTiles(1)) {
                    final Unit u = t.getFirstUnit();
                    if (u == null || !u.getOwner().isEuropean()
                        || !u.isOffensiveUnit()
                        || x.getStance(u.getOwner()) != Stance.WAR) continue;
                    boolean dup = false;
                    for (Call c : ret) dup |= c.caller == x && c.enemy == u.getOwner();
                    if (!dup) ret.add(new Call(x, u.getOwner(), is.getTile(),
                                               is.getName()));
                    break;
                }
            }
        }
        return ret;
    }

    /**
     * Should we help?  Friends help, kin a little; liking the attacker,
     * fearing it and distance hold us back.
     *
     * @param ch Our character.
     * @param ofCaller Our opinion of the caller.
     * @param ofEnemy Our opinion of the attacker.
     * @param fear How much we fear the attacker (0..30 or so).
     * @param distance Tiles from the trouble to our nearest settlement.
     * @return The score; help from {@link #HELP_LINE}.
     */
    public static double helpScore(TribeCharacter ch, double ofCaller,
                                   double ofEnemy, double fear, int distance) {
        return ch.help * (ofCaller + KIN) - 0.3 * Math.max(0, ofEnemy)
            - fear - 2.0 * Math.max(0, distance - 5);
    }

    /** Fear of a European: its soldiers near our lands. */
    private double fear(Player e) {
        int n = 0;
        for (IndianSettlement is : me().getIndianSettlementList()) {
            for (Tile t : is.getTile().getSurroundingTiles(WATCH)) {
                for (Unit u : t.getUnitList()) {
                    if (u.getOwner() == e && u.isOffensiveUnit()) n++;
                }
            }
        }
        return 5.0 * Math.min(6, n);
    }

    private int distanceTo(Tile t) {
        int d = Integer.MAX_VALUE;
        if (t == null) return d;
        for (IndianSettlement is : me().getIndianSettlementList()) {
            d = Math.min(d, is.getTile().getDistanceTo(t));
        }
        return d;
    }

    /** Answer this turn's calls for help. */
    private void answerCalls(LogBuilder lb) {
        final Player me = me();
        if (me.getIndianSettlementList().isEmpty()) return;
        for (Call c : calls(game())) {
            if (c.caller == me || me.getStance(c.enemy) == Stance.WAR) continue;
            final String key = c.caller.getNationId() + "|" + c.enemy.getNationId();
            final Integer heard = ai.getCallsHeard().get(key);
            if (heard != null && now() - heard < CALL_GAP) continue;
            ai.getCallsHeard().put(key, now());
            final double ofCaller = opinion(ai, c.caller);
            final double ofEnemy = opinion(ai, c.enemy);
            final boolean rival = c.caller.getNationId().equals(ai.getRival());
            if (rival && ofCaller <= SIDE_LINE && ofEnemy >= SIDE_LIKE
                && me.getStance(c.caller) != Stance.WAR) {
                changeStance(Stance.WAR, c.caller);
                anger(me, c.caller);
                news(net.sf.freecol.server.model.TribeNews.Kind.SIDED_WITH, c);
                tell(c, "tribe.sidesWith");
                lb.add("\n  Sides with ", c.enemy.getDebugName(),
                       " against ", c.caller.getDebugName());
                continue;
            }
            final double score = helpScore(ai.getCharacter(), ofCaller,
                ofEnemy, fear(c.enemy), distanceTo(c.where));
            final NativeAIPlayer callerAI = ai.getAIMain()
                .getAIPlayer(c.caller) instanceof NativeAIPlayer
                ? (NativeAIPlayer)ai.getAIMain().getAIPlayer(c.caller) : null;
            if (score >= HELP_LINE) {
                changeStance(Stance.WAR, c.enemy);
                anger(me, c.enemy);
                final int sent = ai.sendWarParty(c.enemy, c.where, WAR_PARTY);
                if (callerAI != null) {
                    callerAI.getMemories().add(new Memory(Memory.Kind.CAME_TO_HELP,
                        me.getNationId(), now(), 1));
                }
                news(net.sf.freecol.server.model.TribeNews.Kind.HELP, c);
                tell(c, "tribe.help");
                lb.add("\n  Helps ", c.caller.getDebugName(), " against ",
                       c.enemy.getDebugName(), " (", sent, " braves)");
            } else if (ofCaller >= net.sf.freecol.common.diplomacy.Mood.FRIENDLY
                       && callerAI != null) {
                // A friend that stays home is remembered.
                callerAI.getMemories().add(new Memory(Memory.Kind.LEFT_US_ALONE,
                    me.getNationId(), now(), 1));
                news(net.sf.freecol.server.model.TribeNews.Kind.STAYED_HOME, c);
            }
        }
    }

    /** Angry enough with someone for the braves to attack. */
    static void anger(Player me, Player other) {
        final int hate = Tension.Level.ANGRY.getLimit();
        final Tension t = me.getTension(other);
        if (t.getValue() < hate) t.setValue(hate);
    }

    private void news(net.sf.freecol.server.model.TribeNews.Kind k, Call c) {
        net.sf.freecol.server.model.TribeNews.add(game(),
            new net.sf.freecol.server.model.TribeNews.News(now(), k,
                me().getNationId(), c.caller.getNationId(),
                c.enemy.getNationId(), c.why));
    }

    /** The European hears of it in its turn report. */
    private void tell(Call c, String key) {
        if (c.enemy.isAI()) return;
        final net.sf.freecol.common.networking.ChangeSet cs
            = new net.sf.freecol.common.networking.ChangeSet();
        cs.addMessage(c.enemy, new net.sf.freecol.common.model.ModelMessage(
            net.sf.freecol.common.model.ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            key, me())
            .addStringTemplate("%nation%", me().getNationLabel())
            .addStringTemplate("%caller%", c.caller.getNationLabel()));
        ((net.sf.freecol.server.model.ServerGame)game()).sendToAll(cs);
    }

    // Allied settlements (7d) and tribes (7e)

    /** Turns a mission must last before its settlement may break off. */
    public static final int MISSION_TURNS = 20;
    /** Turns before asking the same thing again. */
    public static final int ASK_GAP = 10;
    /** Tension each turn towards the European an allied settlement serves... */
    public static final int RESENT = 5;
    /** ... and from the tribes next to it. */
    public static final int NEIGHBOUR_RESENT = 2;
    /** Share of a tribe's settlements with a happy mission to join. */
    public static final double TRIBE_SHARE = 2.0 / 3;
    /** Tiles around an allied settlement its braves defend. */
    public static final int DEFEND_RADIUS = 4;

    /** Note when each settlement's mission began. */
    private void trackMissions() {
        final java.util.Map<String, String> since = ai.getMissionSince();
        final Set<String> live = new HashSet<>();
        for (IndianSettlement is : me().getIndianSettlementList()) {
            live.add(is.getId());
            final Unit m = is.getMissionary();
            if (m == null) {
                since.remove(is.getId());
                continue;
            }
            final String n = m.getOwner().getNationId();
            final String old = since.get(is.getId());
            if (old == null || !old.startsWith(n + ":")) {
                since.put(is.getId(), n + ":" + now());
            }
        }
        since.keySet().retainAll(live);
    }

    /** Turns the settlement has had a mission of this nation. */
    private int missionTurns(IndianSettlement is, Player p) {
        final String v = ai.getMissionSince().get(is.getId());
        if (v == null || !v.startsWith(p.getNationId() + ":")) return 0;
        try {
            return now() - Integer.parseInt(v.substring(v.indexOf(':') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Happy with a European: its alarm at the lowest level. */
    static boolean happy(IndianSettlement is, Player p) {
        final Tension t = is.getAlarm(p);
        return t != null && t.getLevel() == Tension.Level.HAPPY;
    }

    /**
     * Our tribe is weak, or angry with the European: at war with it,
     * lost a settlement to it, lost its capital, or down to two
     * settlements.
     */
    private boolean weakOrAngry(Player p) {
        final Player me = me();
        if (me.getStance(p) == Stance.WAR) return true;
        for (Memory m : ai.getMemories().list()) {
            if (m.kind == Memory.Kind.SETTLEMENT_DESTROYED
                && p.getNationId().equals(m.about)) return true;
        }
        boolean capital = false;
        for (IndianSettlement is : me.getIndianSettlementList()) {
            capital |= is.isCapital();
        }
        return !capital || me.getIndianSettlementList().size() <= 2;
    }

    /**
     * Would this settlement break off and join the European whose
     * mission it has?
     *
     * @param is The {@code IndianSettlement}.
     * @return The European, or null.
     */
    public Player breaksOffTo(IndianSettlement is) {
        if (is.getAlliedPlayer() != null || is.getMissionary() == null) return null;
        final Player p = is.getMissionary().getOwner();
        if (!p.isEuropean() || p.isREF()) return null;
        return (missionTurns(is, p) >= MISSION_TURNS && happy(is, p)
                && weakOrAngry(p)) ? p : null;
    }

    private boolean askedLately(Offer.Kind kind, String place, String to) {
        for (Offer o : ai.getOffers()) {
            if (o.kind == kind && o.audience.contains(to)
                && (place == null || place.equals(o.place))
                && (o.state == Offer.State.OPEN || now() - o.posted < ASK_GAP)) {
                return true;
            }
        }
        return false;
    }

    private void settlementsBreakOff(net.sf.freecol.common.networking.ChangeSet cs,
                                     LogBuilder lb) {
        for (IndianSettlement is : me().getIndianSettlementList()) {
            final Player p = breaksOffTo(is);
            if (p == null || askedLately(Offer.Kind.SETTLEMENT_JOINS,
                                         is.getId(), p.getNationId())) continue;
            lb.add("\n  ", is.getName(), " breaks off to ", p.getDebugName());
            if (p.isAI()) {
                ally(is, p, cs);
                continue;
            }
            final Offer o = new Offer("joins:" + is.getId() + ":" + now(),
                Offer.Kind.SETTLEMENT_JOINS, me().getNationId(), null, 0, 0,
                is.getId(), is.getName(), now(), 0);
            o.audience.add(p.getNationId());
            ai.getOffers().add(o);
        }
    }

    /**
     * A settlement breaks off and becomes the European's ally: it stays
     * native, under its protection.  Its tribe resents it.
     *
     * @param is The {@code IndianSettlement}.
     * @param p The European.
     * @param cs A {@code ChangeSet} to update.
     */
    public void ally(IndianSettlement is, Player p,
                     net.sf.freecol.common.networking.ChangeSet cs) {
        is.setAlliedPlayer(p);
        me().getTension(p).modify(Tension.TENSION_ADD_NORMAL);
        cs.add(net.sf.freecol.common.networking.ChangeSet.See.perhaps(), is.getTile());
        net.sf.freecol.server.model.TribeNews.add(game(),
            new net.sf.freecol.server.model.TribeNews.News(now(),
                net.sf.freecol.server.model.TribeNews.Kind.JOINED,
                me().getNationId(), null, p.getNationId(), is.getName()));
        if (!p.isAI()) {
            cs.addMessage(p, new net.sf.freecol.common.model.ModelMessage(
                net.sf.freecol.common.model.ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                "tribe.settlementJoined", me())
                .addName("%settlement%", is.getName())
                .addStringTemplate("%nation%", me().getNationLabel()));
        }
    }

    /** An allied settlement is resented by its tribe and neighbours. */
    private void resent() {
        for (IndianSettlement is : me().getIndianSettlementList()) {
            final Player p = is.getAlliedPlayer();
            if (p == null) continue;
            if (p.isDead()) {
                is.setAlliedPlayer(null);
                continue;
            }
            me().getTension(p).modify(RESENT);
            for (Player q : game().getLiveNativePlayerList()) {
                if (q == me()) continue;
                for (IndianSettlement x : q.getIndianSettlementList()) {
                    if (x.getTile().getDistanceTo(is.getTile()) <= TribeRelations.NEAR) {
                        q.getTension(p).modify(NEIGHBOUR_RESENT);
                        break;
                    }
                }
            }
        }
    }

    /** An allied settlement's braves attack its ally's enemies nearby. */
    private void defendAllies(LogBuilder lb) {
        for (IndianSettlement is : me().getIndianSettlementList()) {
            final Player p = is.getAlliedPlayer();
            if (p == null) continue;
            for (Tile t : is.getTile().getSurroundingTiles(DEFEND_RADIUS)) {
                final Unit u = t.getFirstUnit();
                if (u == null || u.isNaval() || u.getOwner() == me()
                    || u.getOwner() == p
                    || p.getStance(u.getOwner()) != Stance.WAR) continue;
                final int n = ai.sendWarParty(u.getOwner(), t, 1, is);
                if (n > 0) lb.add("\n  ", is.getName(), " defends ",
                                  p.getDebugName(), " against ",
                                  u.getOwner().getDebugName());
                break;
            }
        }
    }

    /**
     * Would the whole tribe ally with a European: two thirds of its
     * settlements with a happy mission of it and liking it, or (as the
     * Tlaxcala) both at war with our old rival?
     *
     * @param p The European.
     * @return True if so.
     */
    public boolean joins(Player p) {
        final Player me = me();
        if (!p.isEuropean() || p.isREF() || me.getStance(p) != Stance.PEACE) {
            return false;
        }
        final List<IndianSettlement> all = me.getIndianSettlementList();
        if (all.isEmpty()) return false;
        int n = 0;
        for (IndianSettlement is : all) {
            if (is.getMissionary() != null
                && is.getMissionary().getOwner() == p && happy(is, p)) n++;
        }
        final double o = opinion(ai, p);
        if (n >= TRIBE_SHARE * all.size()
            && o >= net.sf.freecol.common.diplomacy.Mood.FRIENDLY) return true;
        final Player r = (ai.getRival() == null) ? null
            : game().getPlayerByNationId(ai.getRival());
        return r != null && me.getStance(r) == Stance.WAR
            && p.getStance(r) == Stance.WAR && o >= 0;
    }

    private void tribeAlliances(net.sf.freecol.common.networking.ChangeSet cs,
                                LogBuilder lb) {
        for (Player p : game().getLiveEuropeanPlayerList()) {
            if (!joins(p) || askedLately(Offer.Kind.ALLIANCE, null,
                                         p.getNationId())) continue;
            lb.add("\n  Offers alliance to ", p.getDebugName());
            if (p.isAI()) {
                changeStance(Stance.ALLIANCE, p);
                news(net.sf.freecol.server.model.TribeNews.Kind.JOINED,
                     new Call(me(), p, null, ""));
                continue;
            }
            final Offer o = new Offer("tribe:" + me().getNationId() + ":"
                + p.getNationId() + ":" + now(), Offer.Kind.ALLIANCE,
                me().getNationId(), null, 0, 0, null, "", now(), 0);
            o.audience.add(p.getNationId());
            ai.getOffers().add(o);
        }
    }

    /** Our European allies' wars are ours. */
    private void allyWars(LogBuilder lb) {
        final Player me = me();
        for (Player p : game().getLiveEuropeanPlayerList()) {
            if (me.getStance(p) != Stance.ALLIANCE) continue;
            for (Player e : game().getLivePlayerList()) {
                if (e == me || e == p || p.getStance(e) != Stance.WAR
                    || me.getStance(e) == Stance.WAR
                    || me.getStance(e) == Stance.ALLIANCE
                    || e.isUnknownEnemy() || e.isUndead()) continue;
                changeStance(Stance.WAR, e);
                anger(me, e);
                lb.add("\n  Joins ally ", p.getDebugName(), "'s war on ",
                       e.getDebugName());
            }
        }
    }

    /** We call for help: our European allies must answer. */
    private void callAllies(net.sf.freecol.common.networking.ChangeSet cs,
                            LogBuilder lb) {
        final Player me = me();
        for (Call c : calls(game())) {
            if (c.caller != me) continue;
            for (Player p : game().getLiveEuropeanPlayerList()) {
                if (me.getStance(p) != Stance.ALLIANCE || p == c.enemy
                    || p.getStance(c.enemy) == Stance.WAR) continue;
                final String key = "ally|" + p.getNationId() + "|"
                    + c.enemy.getNationId();
                final Integer heard = ai.getCallsHeard().get(key);
                if (heard != null && now() - heard < CALL_GAP) continue;
                ai.getCallsHeard().put(key, now());
                if (!p.isAI()) {
                    final Offer o = new Offer("tcall:" + me.getNationId() + ":"
                        + p.getNationId() + ":" + now(), Offer.Kind.CALL_TO_ARMS,
                        me.getNationId(), null, 0, 0, null, c.why, now(), 0);
                    o.against = c.enemy.getNationId();
                    o.audience.add(p.getNationId());
                    ai.getOffers().add(o);
                    continue;
                }
                final net.sf.freecol.server.ai.AIPlayer pa
                    = ai.getAIMain().getAIPlayer(p);
                boolean join = false;
                if (pa instanceof net.sf.freecol.server.ai.EuropeanAIPlayer) {
                    final net.sf.freecol.common.diplomacy.Outlook po
                        = ((net.sf.freecol.server.ai.EuropeanAIPlayer)pa).getOutlook();
                    join = po.view.others.containsKey(c.enemy.getNationId())
                        && net.sf.freecol.common.diplomacy.Diplomat.score(
                            net.sf.freecol.common.diplomacy.Action.joinWar(
                                c.enemy.getNationId(), me.getNationId()),
                            po.situation).total >= 0;
                }
                if (join) {
                    ((net.sf.freecol.server.model.ServerPlayer)p)
                        .csChangeStance(Stance.WAR, c.enemy, true, cs);
                    ai.getMemories().add(new Memory(Memory.Kind.CAME_TO_HELP,
                        p.getNationId(), now(), 1));
                } else {
                    leaveAlliance(p, cs);
                }
                lb.add("\n  Calls ally ", p.getDebugName(), ": ",
                       (join) ? "joins" : "refuses");
            }
        }
    }

    /** An ally that would not help: the alliance is over, remembered. */
    private void leaveAlliance(Player p,
                               net.sf.freecol.common.networking.ChangeSet cs) {
        ((net.sf.freecol.server.model.ServerPlayer)me())
            .csChangeStance(Stance.PEACE, p, true, cs);
        ai.getMemories().add(new Memory(Memory.Kind.DEAL_BROKEN,
                                        p.getNationId(), now(), 1));
    }


    // Taking back a breakaway settlement (after step 7)

    /** Tension with the ally at which the tribe tries to take it back. */
    public static final int TAKE_BACK_TENSION = 800;

    /**
     * When its anger with the ally runs high, the tribe marches on its
     * breakaway settlement: the ally must defend it (war with the
     * tribe) or let it go back.  A computer ally defends it when its
     * army is not weaker than the tribe's.
     */
    private void takeBack(net.sf.freecol.common.networking.ChangeSet cs,
                          LogBuilder lb) {
        final Player me = me();
        for (IndianSettlement is : me.getIndianSettlementList()) {
            final Player p = is.getAlliedPlayer();
            if (p == null || me.getStance(p) == Stance.WAR
                || me.getTension(p).getValue() < TAKE_BACK_TENSION
                || askedLately(Offer.Kind.DEFEND_SETTLEMENT, is.getId(),
                               p.getNationId())) continue;
            lb.add("\n  Marches on ", is.getName(), " to take it back from ",
                   p.getDebugName());
            final Offer o = new Offer("raid:" + is.getId() + ":" + now(),
                Offer.Kind.DEFEND_SETTLEMENT, me.getNationId(), null, 0, 0,
                is.getId(), is.getName(), now(), 0);
            o.against = me.getNationId();
            o.audience.add(p.getNationId());
            ai.getOffers().add(o);
            if (!p.isAI()) continue;
            if (p.calculateStrength(false) >= me.calculateStrength(false)) {
                o.state = Offer.State.DONE;
                ((net.sf.freecol.server.model.ServerPlayer)p)
                    .csChangeStance(Stance.WAR, me, true, cs);
            } else {
                o.state = Offer.State.DECLINED;
                giveBack(o, cs);
            }
        }
    }

    /** The ally did not defend it: the settlement is the tribe's again. */
    private void giveBack(Offer o, net.sf.freecol.common.networking.ChangeSet cs) {
        final IndianSettlement is = game().getFreeColGameObject(o.place,
            IndianSettlement.class);
        if (is == null || is.getOwner() != me() || is.getAlliedPlayer() == null) {
            return;
        }
        final Player p = is.getAlliedPlayer();
        is.setAlliedPlayer(null);
        me().getTension(p).modify(-Tension.TENSION_ADD_NORMAL);
        cs.add(net.sf.freecol.common.networking.ChangeSet.See.perhaps(), is.getTile());
        if (!p.isAI()) {
            cs.addMessage(p, new net.sf.freecol.common.model.ModelMessage(
                net.sf.freecol.common.model.ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                "tribe.settlementLost", me())
                .addName("%settlement%", is.getName())
                .addStringTemplate("%nation%", me().getNationLabel()));
        }
    }

    // Offers to the players

    private static String postedKey(Offer o) {
        switch (o.kind) {
        case SETTLEMENT_JOINS: return "offer.postedSettlementJoins";
        case DEFEND_SETTLEMENT: return "offer.postedDefendSettlement";
        case CALL_TO_ARMS: return "offer.postedTribeCall";
        default: return "offer.postedTribeAlliance";
        }
    }

    /** Show open offers to the players once; forget old ones. */
    private void tellOffers(net.sf.freecol.common.networking.ChangeSet cs) {
        final int now = now();
        ai.getOffers().removeIf(o -> o.state.over()
                                && now - o.posted > OfferPlanner.KEEP);
        for (Offer o : ai.getOffers()) {
            if (o.stale(now)) {
                o.state = Offer.State.EXPIRED;
                if (o.kind == Offer.Kind.DEFEND_SETTLEMENT) giveBack(o, cs);
                continue;
            }
            for (String id : o.audience) {
                final Player h = game().getPlayerByNationId(id);
                if (h == null || h.isAI() || o.told.contains(id)
                    || !o.visibleTo(id, now)) continue;
                o.told.add(id);
                cs.add(net.sf.freecol.common.networking.ChangeSet.See.only(h),
                       new net.sf.freecol.common.networking.OfferMessage(o));
                final Player enemy = (o.against == null) ? null
                    : game().getPlayerByNationId(o.against);
                final net.sf.freecol.common.model.ModelMessage mm
                    = new net.sf.freecol.common.model.ModelMessage(
                        net.sf.freecol.common.model.ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                        postedKey(o), me())
                    .addStringTemplate("%nation%", me().getNationLabel())
                    .addName("%settlement%", o.placeName);
                if (enemy != null) mm.addStringTemplate("%enemy%", enemy.getNationLabel());
                else mm.addName("%enemy%", "");
                cs.addMessage(h, mm);
            }
        }
    }

    /**
     * A player answers one of our offers.
     *
     * @param human The player.
     * @param id The offer id.
     * @param accept True to accept.
     * @param cs A {@code ChangeSet} to update.
     * @return True if the offer was ours and open to this player.
     */
    public boolean answer(net.sf.freecol.server.model.ServerPlayer human,
                          String id, boolean accept,
                          net.sf.freecol.common.networking.ChangeSet cs) {
        Offer o = null;
        for (Offer x : ai.getOffers()) if (x.id.equals(id)) o = x;
        if (o == null || !o.visibleTo(human.getNationId(), now())) return false;
        switch (o.kind) {
        case SETTLEMENT_JOINS:
            final IndianSettlement is = game().getFreeColGameObject(o.place,
                IndianSettlement.class);
            if (is == null || is.getOwner() != me()) {
                o.state = Offer.State.EXPIRED;
            } else if (accept) {
                o.state = Offer.State.DONE;
                ally(is, human, cs);
            } else {
                o.state = Offer.State.DECLINED;
            }
            return true;
        case ALLIANCE:
            o.state = (accept) ? Offer.State.DONE : Offer.State.DECLINED;
            if (accept && me().getStance(human) != Stance.WAR) {
                ((net.sf.freecol.server.model.ServerPlayer)me())
                    .csChangeStance(Stance.ALLIANCE, human, true, cs);
            } else if (!accept) {
                ai.getMemories().add(new Memory(Memory.Kind.REFUSED_OFFER,
                    human.getNationId(), now(), 1));
            }
            return true;
        case DEFEND_SETTLEMENT:
            if (accept) {
                o.state = Offer.State.DONE;
                if (human.getStance(me()) != Stance.WAR) {
                    human.csChangeStance(Stance.WAR, me(), true, cs);
                }
            } else {
                o.state = Offer.State.DECLINED;
                giveBack(o, cs);
            }
            return true;
        case CALL_TO_ARMS:
            final Player enemy = game().getPlayerByNationId(o.against);
            if (accept) {
                o.state = Offer.State.DONE;
                if (enemy != null && human.getStance(enemy) != Stance.WAR) {
                    human.csChangeStance(Stance.WAR, enemy, true, cs);
                }
                ai.getMemories().add(new Memory(Memory.Kind.CAME_TO_HELP,
                    human.getNationId(), now(), 1));
            } else {
                o.state = Offer.State.DECLINED;
                if (me().getStance(human) == Stance.ALLIANCE) leaveAlliance(human, cs);
            }
            return true;
        default:
            return false;
        }
    }

    // Uprisings (7f)

    /** Opinion at or under which a tribe hates a European. */
    public static final double HATE_LINE = -40;
    /** Tribes that must hate the same European for unrest. */
    public static final int UPRISING_TRIBES = 2;
    /** Braves each risen tribe sends. */
    public static final int UPRISING_PARTY = 3;
    /** Tiles from our settlements within which a friend's loss counts. */
    public static final int PONTIAC_RADIUS = 6;

    /**
     * Turns of unrest before an uprising: 5 on medium, shorter on the
     * harder levels.
     *
     * @param difficulty The difficulty level id, may be null.
     * @return The turns.
     */
    public static int unrestTurns(String difficulty) {
        if (difficulty == null) return 5;
        switch (difficulty) {
        case "model.difficulty.veryEasy": return 7;
        case "model.difficulty.easy": return 6;
        case "model.difficulty.hard": return 4;
        case "model.difficulty.veryHard": return 3;
        default: return 5;
        }
    }

    private int unrestTurns() {
        final net.sf.freecol.common.option.OptionGroup dl
            = game().getSpecification().getDifficultyOptionGroup();
        return unrestTurns((dl == null) ? null : dl.getId());
    }

    /**
     * Does a tribe hate a European enough to rise: a low opinion and a
     * real grievance (a settlement destroyed, land taken, or high
     * tension)?
     *
     * @param tribe The tribe's AI player.
     * @param e The European.
     * @return True if so.
     */
    public static boolean hates(NativeAIPlayer tribe, Player e) {
        if (!e.isEuropean() || e.isREF()) return false;
        if (opinion(tribe, e) > HATE_LINE) return false;
        final Player me = tribe.getPlayer();
        if (me.getTension(e).getValue() >= Tension.Level.DISPLEASED.getLimit()) {
            return true;
        }
        for (Memory m : tribe.getMemories().list()) {
            if (e.getNationId().equals(m.about)
                && (m.kind == Memory.Kind.SETTLEMENT_DESTROYED
                    || m.kind == Memory.Kind.LAND_TAKEN)) return true;
        }
        return false;
    }

    /** Other tribes (not our old rival) that hate the same European. */
    private int alsoHate(Player e) {
        int n = 0;
        for (Player q : game().getLiveNativePlayerList()) {
            if (q == me() || q.getNationId().equals(ai.getRival())) continue;
            final net.sf.freecol.server.ai.AIPlayer qa = ai.getAIMain().getAIPlayer(q);
            if (qa instanceof NativeAIPlayer && hates((NativeAIPlayer)qa, e)) n++;
        }
        return n;
    }

    /**
     * Unrest first, the uprising after: when we and at least one more
     * tribe (not our old rival) hate the same European, the tribes
     * speak of war; if it lasts, we rise.  An old rival that already
     * rose keeps us out.
     */
    private void unrest(net.sf.freecol.common.networking.ChangeSet cs,
                        LogBuilder lb) {
        final Player me = me();
        final java.util.Map<String, Integer> u = ai.getUnrest();
        for (Player e : game().getLiveEuropeanPlayerList()) {
            final String id = e.getNationId();
            final boolean angry = hates(ai, e)
                && alsoHate(e) + 1 >= UPRISING_TRIBES;
            if (!angry || me.getStance(e) == Stance.WAR) {
                if (u.remove(id) != null && !angry) {
                    lb.add("\n  Unrest against ", e.getDebugName(), " calms");
                }
                continue;
            }
            final Integer since = u.get(id);
            if (since == null) {
                u.put(id, now());
                lb.add("\n  Unrest against ", e.getDebugName());
                warn(e, cs);
                continue;
            }
            if (now() - since < unrestTurns()) continue;
            if (rivalRose(e)) {
                lb.add("\n  Stays out: our old rival rose against ",
                       e.getDebugName());
                continue;
            }
            u.remove(id);
            changeStance(Stance.WAR, e);
            anger(me, e);
            final Colony c = nearestColony(e);
            ai.sendWarParty(e, (c == null) ? null : c.getTile(), UPRISING_PARTY);
            net.sf.freecol.server.model.TribeNews.add(game(),
                new net.sf.freecol.server.model.TribeNews.News(now(),
                    net.sf.freecol.server.model.TribeNews.Kind.UPRISING,
                    me.getNationId(), null, id, (c == null) ? "" : c.getName()));
            if (!e.isAI()) {
                cs.addMessage(e, new net.sf.freecol.common.model.ModelMessage(
                    net.sf.freecol.common.model.ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                    "tribe.uprising", me)
                    .addStringTemplate("%nation%", me.getNationLabel()));
            }
            lb.add("\n  Rises against ", e.getDebugName());
        }
    }

    /** Did our old rival rise against this European lately? */
    private boolean rivalRose(Player e) {
        final String r = ai.getRival();
        if (r == null) return false;
        for (net.sf.freecol.server.model.TribeNews.News n
                 : net.sf.freecol.server.model.TribeNews.recent(game())) {
            if (n.kind == net.sf.freecol.server.model.TribeNews.Kind.UPRISING
                && r.equals(n.tribe) && e.getNationId().equals(n.european)
                && now() - n.turn <= unrestTurns()) return true;
        }
        return false;
    }

    /** One warning a turn per European, whichever tribe speaks first. */
    private void warn(Player e, net.sf.freecol.common.networking.ChangeSet cs) {
        boolean told = false;
        for (net.sf.freecol.server.model.TribeNews.News n
                 : net.sf.freecol.server.model.TribeNews.recent(game())) {
            told |= n.kind == net.sf.freecol.server.model.TribeNews.Kind.UNREST
                && n.turn == now() && e.getNationId().equals(n.european);
        }
        net.sf.freecol.server.model.TribeNews.add(game(),
            new net.sf.freecol.server.model.TribeNews.News(now(),
                net.sf.freecol.server.model.TribeNews.Kind.UNREST,
                me().getNationId(), null, e.getNationId(), ""));
        if (told || e.isAI()) return;
        cs.addMessage(e, new net.sf.freecol.common.model.ModelMessage(
            net.sf.freecol.common.model.ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            "tribe.unrest", me())
            .addAmount("%turns%", unrestTurns()));
    }

    private Colony nearestColony(Player e) {
        Colony best = null;
        int top = Integer.MAX_VALUE;
        for (Colony c : e.getColonyList()) {
            final int d = distanceTo(c.getTile());
            if (d < top) {
                top = d;
                best = c;
            }
        }
        return best;
    }

    /**
     * Pontiac's rule: when our European friend loses a colony near us
     * to another European, we turn on the winner.
     */
    private void pontiac(LogBuilder lb) {
        final Player me = me();
        final TurnRecord r = record(game());
        for (Observed o : r.diff) {
            if (o.what != Observed.What.CAPTURE) continue;
            final Player winner = game().getPlayerByNationId(o.actor);
            final Player friend = game().getPlayerByNationId(o.other);
            if (winner == null || friend == null || !winner.isEuropean()
                || !friend.isEuropean() || winner == friend
                || me.getStance(winner) == Stance.WAR) continue;
            final boolean friendly = me.getStance(friend) == Stance.ALLIANCE
                || opinion(ai, friend) >= net.sf.freecol.common.diplomacy.Mood.FRIENDLY;
            if (!friendly) continue;
            boolean near = false;
            for (Colony c : winner.getColonyList()) {
                if (c.getName().equals(o.detail)
                    && distanceTo(c.getTile()) <= PONTIAC_RADIUS) near = true;
            }
            if (!near) continue;
            changeStance(Stance.WAR, winner);
            anger(me, winner);
            ai.getMemories().add(new Memory(Memory.Kind.WAR,
                                            winner.getNationId(), now(), 1));
            lb.add("\n  Turns on ", winner.getDebugName(), " for taking ",
                   o.detail, " from our friend ", friend.getDebugName());
        }
    }


    // The mood, for the companion page (7g)

    private net.sf.freecol.common.diplomacy.TribeMood mood() {
        final net.sf.freecol.common.diplomacy.TribeMood m
            = new net.sf.freecol.common.diplomacy.TribeMood(me().getNationId(),
                now(), ai.getCharacter(), ai.getRival(), unrestTurns());
        for (Player p : game().getLivePlayerList(me())) {
            if (p.isREF() || p.isUnknownEnemy() || p.isUndead()) continue;
            m.opinions.put(p.getNationId(), opinion(ai, p));
            if (me().getStance(p) == Stance.WAR) m.atWar.add(p.getNationId());
            if (p.isEuropean()) {
                m.trust.put(p.getNationId(), Relations.trust(
                    ai.getMemories().list(), p.getNationId(), now()));
            }
        }
        for (java.util.Map.Entry<String, Integer> e : ai.getUnrest().entrySet()) {
            m.unrest.put(e.getKey(), now() - e.getValue());
        }
        m.memories.addAll(ai.getMemories().list());
        return m;
    }

    private void changeStance(Stance stance, Player other) {
        ai.getAIMain().getFreeColServer().getInGameController()
            .changeStance(me(), stance, other, true);
    }
}
