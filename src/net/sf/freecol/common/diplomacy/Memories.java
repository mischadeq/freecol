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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import net.sf.freecol.common.model.Stance;


/**
 * What one nation remembers of the others, during a game: the losses
 * it suffered, deals kept and broken, gifts and trades, help in a war.
 * Wars declared, peace made and colonies conquered come from the game's
 * own history (see {@link WorldViewBuilder}) and are not kept here.
 *
 * Only what the nation itself saw happen: its own losses, wars on it,
 * colonies changing hands (every nation hears of that).
 */
public final class Memories {

    /** Turns a peace must hold before it counts as a kept deal. */
    public static final int PROBATION = 20;

    private final List<Memory> list = new ArrayList<>();


    public Memories() {}


    public int size() {
        return list.size();
    }

    public List<Memory> list() {
        return Collections.unmodifiableList(list);
    }

    public void add(Memory m) {
        list.add(m);
    }

    /**
     * The memories about a nation that still count, strongest first.
     *
     * @param about The nation id.
     * @param now The current turn.
     * @param memories The memories to choose from.
     * @return The memories.
     */
    public static List<Memory> about(String about, int now,
                                     List<Memory> memories) {
        List<Memory> ret = new ArrayList<>();
        for (Memory m : memories) {
            if (about.equals(m.about) && m.kind.weight != 0
                && (m.value(now) != 0 || m.trust(now) != 0)) ret.add(m);
        }
        ret.sort(Comparator.comparingDouble((Memory m) ->
                -Math.abs(m.value(now)) - Math.abs(m.trust(now)))
            .thenComparingInt(m -> -m.turn));
        return ret;
    }

    /**
     * Forget what no longer counts, and settle peace treaties that held.
     *
     * @param now The current turn.
     */
    public void fade(int now) {
        List<Memory> kept = new ArrayList<>();
        for (Iterator<Memory> it = list.iterator(); it.hasNext();) {
            Memory m = it.next();
            final int age = now - m.turn;
            if (m.kind == Memory.Kind.TREATY && age >= PROBATION) {
                it.remove();
                kept.add(new Memory(Memory.Kind.DEAL_KEPT, m.about, now, 1));
            } else if (age >= m.kind.lasts()) {
                it.remove();
            }
        }
        list.addAll(kept);
    }

    /**
     * Turn what happened between two snapshots into memories of one
     * nation.  The same for the replay and a live game.
     *
     * @param me The nation that remembers.
     * @param d What happened ({@link TurnDiff#diff}).
     * @param before The earlier {@code Snapshot} (for stances).
     * @param turn The turn to date the memories.
     */
    public void remember(String me, List<Observed> d, Snapshot before,
                         int turn) {
        for (Observed o : d) {
            switch (o.what) {
            case UNITS_LOST:
                if (me.equals(o.actor) && o.other != null) {
                    add(new Memory(Memory.Kind.UNIT_LOST, o.other, turn,
                                   Math.max(1, o.amount)));
                }
                break;
            case SHIPS_DAMAGED:
                if (me.equals(o.actor) && o.other != null) {
                    add(new Memory(Memory.Kind.SHIP_DAMAGED, o.other, turn,
                                   Math.max(1, o.amount)));
                }
                break;
            case COLONY_LOST: // destroyed, not conquered
                if (me.equals(o.actor) && o.other != null) {
                    add(new Memory(Memory.Kind.COLONY_TAKEN, o.other, turn, 1));
                }
                break;
            case CAPTURE: case DESTROY:
                // Our own conquered colonies are in the history.  A
                // colony taken from our enemy is help.
                if (!me.equals(o.actor) && o.other != null
                    && before.stance(me, o.other) == Stance.WAR) {
                    add(new Memory(Memory.Kind.HELPED_IN_WAR, o.actor, turn, 1));
                }
                break;
            case WAR:
                if (!o.forced && !me.equals(o.other) && !me.equals(o.actor)
                    && o.other != null
                    && before.stance(o.actor, o.other) == Stance.ALLIANCE) {
                    // Everyone sees a nation turn on its ally.
                    add(new Memory(Memory.Kind.BETRAYER, o.actor, turn, 1));
                }
                if (me.equals(o.other) || me.equals(o.actor)) {
                    final String them = me.equals(o.actor) ? o.other : o.actor;
                    final boolean treaty = removeTreaty(them);
                    if (!o.forced && me.equals(o.other)
                        && (treaty
                            || before.stance(me, them) == Stance.ALLIANCE)) {
                        add(new Memory(Memory.Kind.DEAL_BROKEN, them, turn, 1));
                    }
                }
                break;
            case PEACE:
                // Our ally made peace with our common enemy, alone.
                for (String[] p : new String[][] { { o.actor, o.other },
                                                   { o.other, o.actor } }) {
                    if (p[0] == null || p[1] == null || me.equals(p[0])
                        || me.equals(p[1])) continue;
                    if (before.stance(me, p[0]) == Stance.ALLIANCE
                        && before.stance(me, p[1]) == Stance.WAR) {
                        add(new Memory(Memory.Kind.DEAL_BROKEN, p[0], turn, 1));
                    }
                }
                if (me.equals(o.other) || me.equals(o.actor)) {
                    final String them = me.equals(o.actor) ? o.other : o.actor;
                    removeTreaty(them);
                    add(new Memory(Memory.Kind.TREATY, them, turn, 1));
                }
                break;
            default:
                break;
            }
        }
    }

    private boolean removeTreaty(String about) {
        return list.removeIf(m -> m.kind == Memory.Kind.TREATY
                             && about.equals(m.about));
    }


    // Serialization: "KIND:nation:turn:count" records.

    /**
     * One line for an XML attribute.
     *
     * @param sep The record separator.
     * @return The line (empty if nothing is remembered).
     */
    public String serialize(char sep) {
        StringBuilder sb = new StringBuilder();
        for (Memory m : list) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(m.kind.name()).append(':').append(m.about)
                .append(':').append(m.turn).append(':').append(m.count);
        }
        return sb.toString();
    }

    /**
     * Read memories back; bad records are skipped.
     *
     * @param s The line from {@link #serialize}, may be null.
     * @return The {@code Memories}.
     */
    public static Memories parse(String s) {
        Memories ret = new Memories();
        if (s == null || s.isEmpty()) return ret;
        for (String rec : s.split("[;,]")) {
            String[] f = rec.split(":");
            if (f.length != 4) continue;
            try {
                ret.add(new Memory(Memory.Kind.valueOf(f[0]), f[1],
                                   Integer.parseInt(f[2]),
                                   Integer.parseInt(f[3])));
            } catch (IllegalArgumentException e) {} // skip
        }
        return ret;
    }
}
