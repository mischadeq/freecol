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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.freecol.common.model.Stance;


/**
 * What every nation did between two saves, found by comparing their
 * snapshots.
 *
 * Who declared a war is not in a save.  A computer player goes to war
 * when its tension towards the other passes 1010 (hateful), and agrees
 * to a cease-fire below 590, so a side with such tension chose it.  A
 * war of the human player is his own choice only if he says so (the
 * {@code declaredByHuman} set); every other war nobody hated their way
 * into was the King's.
 */
public final class TurnDiff {

    /** Tension above which a computer player declares war. */
    public static final int WAR_TENSION = 1010;
    /** Tension at or below which it agrees to stop. */
    public static final int PEACE_TENSION = 590;
    /** Army growth (soldiers' worth) counted as arming. */
    public static final double ARM_STEP = 1.0;

    private TurnDiff() {}


    /**
     * Compare two snapshots.
     *
     * @param a The earlier {@code Snapshot}.
     * @param b The later {@code Snapshot}.
     * @param declaredByHuman Nations the human player says he declared
     *     war on himself.
     * @return What happened, per nation.
     */
    public static List<Observed> diff(Snapshot a, Snapshot b,
                                      Set<String> declaredByHuman) {
        List<Observed> ret = new ArrayList<>();
        // Wars and peace, once per pair.
        Set<String> done = new HashSet<>();
        for (Map.Entry<String, Stance> e : b.stances.entrySet()) {
            String[] ab = e.getKey().split("\\|");
            String x = ab[0], y = ab[1];
            String pair = (x.compareTo(y) < 0) ? x + y : y + x;
            if (!done.add(pair)) continue;
            Stance before = a.stance(x, y), after = e.getValue();
            if (before == after || before == Stance.UNCONTACTED) continue;
            if (after == Stance.WAR) {
                ret.add(war(a, b, x, y, declaredByHuman));
            } else if (before == Stance.WAR) {
                ret.add(peace(a, b, x, y));
            }
        }
        // Colonies and settlements.
        for (Snapshot.Place p : a.places.values()) {
            Snapshot.Place q = b.places.get(p.id);
            if (q == null) {
                String by = destroyer(a, b, p);
                if (p.nativeSettlement && by != null) {
                    ret.add(new Observed(Observed.What.DESTROY, by, p.owner,
                                         false, 0, p.name));
                } else {
                    ret.add(new Observed(Observed.What.COLONY_LOST, p.owner,
                                         by, false, 0, p.name));
                }
            } else if (!q.owner.equals(p.owner)) {
                ret.add(new Observed(Observed.What.CAPTURE, q.owner, p.owner,
                                     false, 0, p.name));
            } else if (p.nativeSettlement) {
                if (q.missionary != null && !q.missionary.equals(p.missionary)) {
                    ret.add(new Observed(Observed.What.MISSION, q.missionary,
                                         q.owner, false, 0, q.name));
                } else if (q.missionary == null && p.missionary != null) {
                    ret.add(new Observed(Observed.What.MISSION_GONE,
                                         p.missionary, p.owner, false, 0,
                                         p.name));
                }
            }
        }
        // Muskets and horses sold to tribes.
        Set<String> armedTribes = new HashSet<>();
        for (Map.Entry<String, Integer> e : b.armsSales.entrySet()) {
            Integer before = a.armsSales.get(e.getKey());
            if (before != null && before.equals(e.getValue())) continue;
            String[] f = e.getKey().split("\\|");
            Snapshot.Place p = b.places.get(f[1]);
            if (p == null || !armedTribes.add(f[0] + "|" + p.owner + "|" + f[2])) continue;
            ret.add(new Observed(Observed.What.ARM_TRIBE, f[0], p.owner, false,
                0, Action.shortId(f[2])
                + " at " + p.name));
        }
        // Troops lost and ships damaged.
        for (Snapshot.Nation n : a.nations.values()) {
            int lost = 0, damaged = 0;
            for (Snapshot.Troop t : a.troops.values()) {
                if (!t.owner.equals(n.id)) continue;
                Snapshot.Troop u = b.troops.get(t.id);
                if (u == null) {
                    if (atWar(a, b, n.id)) lost++;
                } else if (u.damaged && !t.damaged) {
                    damaged++;
                }
            }
            String enemy = onlyEnemy(a, b, n.id);
            if (lost > 0) {
                ret.add(new Observed(Observed.What.UNITS_LOST, n.id, enemy,
                                     false, lost, null));
            }
            if (damaged > 0) {
                ret.add(new Observed(Observed.What.SHIPS_DAMAGED, n.id, enemy,
                                     false, damaged, null));
            }
            Snapshot.Nation m = b.nations.get(n.id);
            if (m == null) continue;
            int armed = (int)Math.floor(m.army - n.army);
            if (n.european && armed >= ARM_STEP) {
                ret.add(new Observed(Observed.What.ARM, n.id, null, false,
                                     armed, null));
            }
            int sold = m.income - n.income;
            if (sold > 0) {
                ret.add(new Observed(Observed.What.SOLD_IN_EUROPE, n.id, null,
                                     false, sold, null));
            }
        }
        return ret;
    }

    private static Observed war(Snapshot a, Snapshot b, String x, String y,
                                Set<String> declaredByHuman) {
        // The human player's own word comes first: tension rises
        // after an attack, so it can not tell who started.
        String human = human(a, x, y);
        if (human != null) {
            String other = human.equals(x) ? y : x;
            if (declaredByHuman.contains(other)) {
                return new Observed(Observed.What.WAR, human, other, false,
                                    0, "you said so");
            }
        }
        final int tx = Math.max(a.tension(x, y), b.tension(x, y));
        final int ty = Math.max(a.tension(y, x), b.tension(y, x));
        if (tx >= WAR_TENSION || ty >= WAR_TENSION) {
            boolean xChose = tx >= ty;
            return new Observed(Observed.What.WAR, (xChose) ? x : y,
                                (xChose) ? y : x, false, 0,
                                "tension " + Math.max(tx, ty));
        }
        if (human != null) {
            String other = human.equals(x) ? y : x;
            return new Observed(Observed.What.WAR, human, other, true, 0,
                                "the King");
        }
        return new Observed(Observed.What.WAR, x, y, true, 0,
                            "nobody hateful: the King");
    }

    private static Observed peace(Snapshot a, Snapshot b, String x,
                                  String y) {
        int tx = min(a.tension(x, y), b.tension(x, y));
        int ty = min(a.tension(y, x), b.tension(y, x));
        if (tx >= 0 && tx <= PEACE_TENSION && (ty < 0 || tx <= ty)) {
            return new Observed(Observed.What.PEACE, x, y, false, 0,
                                "tension " + tx);
        }
        if (ty >= 0 && ty <= PEACE_TENSION) {
            return new Observed(Observed.What.PEACE, y, x, false, 0,
                                "tension " + ty);
        }
        String human = human(a, x, y);
        String actor = (human != null) ? human : x;
        return new Observed(Observed.What.PEACE, actor,
                            actor.equals(x) ? y : x, true, 0, "the King");
    }

    private static int min(int p, int q) {
        if (p < 0) return q;
        if (q < 0) return p;
        return Math.min(p, q);
    }

    private static String human(Snapshot a, String x, String y) {
        Snapshot.Nation nx = a.nations.get(x), ny = a.nations.get(y);
        if (nx != null && nx.european && !nx.ai) return x;
        if (ny != null && ny.european && !ny.ai) return y;
        return null;
    }

    /** Is a nation at war with anyone, before or after? */
    private static boolean atWar(Snapshot a, Snapshot b, String id) {
        for (Map.Entry<String, Stance> e : a.stances.entrySet()) {
            if (e.getKey().startsWith(id + "|") && e.getValue() == Stance.WAR) {
                return true;
            }
        }
        for (Map.Entry<String, Stance> e : b.stances.entrySet()) {
            if (e.getKey().startsWith(id + "|") && e.getValue() == Stance.WAR) {
                return true;
            }
        }
        return false;
    }

    /** The one nation it is at war with, or null if none or several. */
    private static String onlyEnemy(Snapshot a, Snapshot b, String id) {
        String found = null;
        for (Snapshot s : new Snapshot[] { a, b }) {
            for (Map.Entry<String, Stance> e : s.stances.entrySet()) {
                if (!e.getKey().startsWith(id + "|")
                    || e.getValue() != Stance.WAR) continue;
                String o = e.getKey().substring(id.length() + 1);
                if (found == null) found = o;
                else if (!found.equals(o)) return null;
            }
        }
        return found;
    }

    /** Who destroyed a place: a history event of that turn, if any. */
    private static String destroyer(Snapshot a, Snapshot b,
                                    Snapshot.Place p) {
        for (String ev : b.events) {
            if (a.events.contains(ev)) continue;
            String[] f = ev.split("\\|", -1);
            if (f.length < 4) continue;
            // A destroyed settlement is recorded by the destroyer about
            // itself (the tribe is only named in the text); a conquered
            // colony names the loser.
            if (p.nativeSettlement && "DESTROY_SETTLEMENT".equals(f[1])) {
                return f[0];
            }
            if (("DESTROY_NATION".equals(f[1])
                 || "CONQUER_COLONY".equals(f[1]))
                && p.owner.equals(f[2])) return f[0];
        }
        return null;
    }
}
