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

package net.sf.freecol.common.diplomacy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * What the diplomat works out once per turn before scoring: for every
 * other nation the best place it could take and the chance, how likely
 * that nation is to take my weakest colony, the threats and the goals.
 */
public final class Situation {

    /** The best attack on one nation. */
    public static final class Attack {
        public final PlaceView place;
        public final CaptureSim.Result result;
        /** Attackers committed. */
        public final int force;

        Attack(PlaceView place, CaptureSim.Result result, int force) {
            this.place = place;
            this.result = result;
            this.force = force;
        }

        /** Expected land gained, in points. */
        public double expected() {
            return result.chance * place.value();
        }
    }

    /**
     * The best attack on each enemy if we bought the ship on sale
     * (only where that is better than what we can do now).
     */
    public final Map<String, Attack> withShip = new HashMap<>();

    /** Share of a colony's value a tribe's raid costs. */
    public static final double NATIVE_RAID_SHARE = 0.25;

    public final WorldView w;
    public final Map<String, Attack> attacks = new HashMap<>();
    /** Chance each nation takes my weakest colony, times nearness. */
    public final Map<String, Double> counter = new HashMap<>();
    public final Map<String, Double> threats;
    public final String leader;
    public final List<Goal> goals;


    public Situation(WorldView w) {
        this.w = w;
        final Set<Integer> myLands = new HashSet<>();
        for (PlaceView p : w.me.places) myLands.add(p.land);
        final PlaceView weakest = w.weakestColony();
        Map<String, Double> take = new HashMap<>();
        for (NationView v : w.others.values()) {
            Attack best = bestAttack(w, v, myLands, w.carrierSpace);
            if (best != null) {
                attacks.put(v.id, best);
                take.put(v.id, best.result.chance);
            }
            if (w.shipSpace > 0 && v.atWar()) {
                Attack more = bestAttack(w, v, myLands,
                                         w.carrierSpace + w.shipSpace);
                if (more != null && (best == null
                        || more.expected() > best.expected() + 1e-9)) {
                    withShip.put(v.id, more);
                }
            }
            if (weakest != null && v.army > 0) {
                int n = (int)Math.round(v.army / 2);
                CaptureSim.Result r = CaptureSim.attack(
                    CaptureSim.force(n, w.soldierAttack), weakest,
                    seed(w, v.id, weakest.id));
                // Tribes raid and burn rather than take a colony:
                // count a quarter of its value.
                counter.put(v.id, r.chance * v.near
                            * ((v.european) ? 1.0 : NATIVE_RAID_SHARE));
            }
        }
        this.threats = Threat.all(w);
        this.leader = Threat.leaderToContain(w);
        this.goals = Goal.active(w, take);
    }

    /**
     * The place of a nation with the highest expected gain, attacked by
     * the stronger half of my land units (all of them if fewer than
     * two).  Across the sea only as many as the ships carry.
     */
    private static Attack bestAttack(WorldView w, NationView v,
                                     Set<Integer> myLands, int space) {
        if (w.attackers.length == 0) return null;
        final int half = Math.max(1, (w.attackers.length + 1) / 2);
        Attack best = null;
        for (PlaceView p : v.places) {
            int force = half;
            if (!myLands.contains(p.land)) {
                if (v.european || p.coastal) force = Math.min(force, space);
                else force = 0;
            }
            if (force <= 0) continue;
            double[] a = java.util.Arrays.copyOf(w.attackers, force);
            CaptureSim.Result r = CaptureSim.attack(a, p, seed(w, v.id, p.id));
            Attack at = new Attack(p, r, force);
            if (best == null || at.expected() > best.expected()) best = at;
        }
        return best;
    }

    private static long seed(WorldView w, String a, String b) {
        return 31L * (31L * w.turn + w.me.id.hashCode() + a.hashCode())
            + b.hashCode();
    }

    /** Does a goal of this type about this target exist? */
    public boolean hasGoal(Goal.Type type, String target) {
        for (Goal g : goals) {
            if (g.type == type && (target == null || target.equals(g.target))) {
                return true;
            }
        }
        return false;
    }
}
