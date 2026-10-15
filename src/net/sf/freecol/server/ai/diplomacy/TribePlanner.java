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
        meetTribes(game());
        pickRival();
        remember();
        landTaken();
        rub();
        tribeStances(lb);
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
                if (TribeRelations.wantsWar(ch, o, id.equals(ai.getRival()),
                                            grudge)) {
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

    private void changeStance(Stance stance, Player other) {
        ai.getAIMain().getFreeColServer().getInGameController()
            .changeStance(me(), stance, other, true);
    }
}
