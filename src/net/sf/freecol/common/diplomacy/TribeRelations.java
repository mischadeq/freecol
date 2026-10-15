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

import java.util.Collection;
import java.util.List;

import net.sf.freecol.common.model.Stance;


/**
 * How tribes feel about each other (mod, step 7a).  Tribes have lived
 * side by side forever: they know each other, neighbours rub, each has
 * an old rival, and a shared enemy brings them together.
 */
public final class TribeRelations {

    /** The old rival, in opinion points. */
    public static final double RIVAL = 30;
    /** Each pair of settlements near each other ("borders"). */
    public static final double NEIGHBOUR = 3;
    /** Borders counted at most. */
    public static final int MAX_BORDERS = 5;
    /** Settlements this close (in tiles) share a border. */
    public static final int NEAR = 4;
    /** Each European both hate (or fight). */
    public static final double SHARED_ENEMY = 15;
    /** Tension from which a tribe holds a grudge. */
    public static final int GRUDGE_TENSION = 600;
    /** Least turns of a war between tribes before peace. */
    public static final int MIN_WAR_TURNS = 15;

    private TribeRelations() {}

    /**
     * A tribe's opinion of another tribe, -100..100.  Tension between
     * tribes starts at nothing, so only grows worse: 0 is neutral.
     *
     * @param tension The game's tension towards it (0..1100).
     * @param memories What the tribe remembers.
     * @param about The other tribe's nation id.
     * @param now The turn.
     * @param rival True if it is the old rival.
     * @param borders Pairs of settlements near each other.
     * @param sharedEnemies Europeans both hate.
     * @return The opinion.
     */
    public static double opinion(int tension, Collection<Memory> memories,
                                 String about, int now, boolean rival,
                                 int borders, int sharedEnemies) {
        double o = -Math.min(Math.max(0, tension), Relations.TENSION_MAX) / 11.0;
        if (memories != null) {
            for (Memory m : memories) {
                if (about.equals(m.about)) o += m.value(now);
            }
        }
        if (rival) o -= RIVAL;
        o -= NEIGHBOUR * Math.min(MAX_BORDERS, Math.max(0, borders));
        o += SHARED_ENEMY * Math.max(0, sharedEnemies);
        return Math.max(-100, Math.min(100, o));
    }

    /**
     * A grudge: real tension, or something bad remembered.
     *
     * @param tension The tension towards it.
     * @param memories What the tribe remembers.
     * @param about The other tribe.
     * @param now The turn.
     * @return True if there is one.
     */
    public static boolean grudge(int tension, Collection<Memory> memories,
                                 String about, int now) {
        if (tension >= GRUDGE_TENSION) return true;
        if (memories != null) {
            for (Memory m : memories) {
                if (about.equals(m.about) && m.value(now) < 0) return true;
            }
        }
        return false;
    }

    /**
     * War between tribes stays rare: only on the old rival, with a
     * grudge, when the opinion is down to the character's war line.
     *
     * @param ch The tribe's character.
     * @param opinion Its opinion of the other.
     * @param rival True if the other is its old rival.
     * @param grudge True if it holds a grudge.
     * @return True for war.
     */
    public static boolean wantsWar(TribeCharacter ch, double opinion,
                                   boolean rival, boolean grudge) {
        return rival && grudge && opinion <= ch.warLine();
    }

    /**
     * Peace again, after a while, unless the hatred is complete.
     *
     * @param ch The tribe's character.
     * @param opinion Its opinion of the other.
     * @param warTurns Turns the war has lasted.
     * @return True for peace.
     */
    public static boolean wantsPeace(TribeCharacter ch, double opinion,
                                     int warTurns) {
        return warTurns >= Math.round(MIN_WAR_TURNS * ch.war)
            && opinion > -90;
    }

    /**
     * Tension added each turn towards an old rival next door.
     *
     * @param ch The tribe's character.
     * @param borders Pairs of settlements near each other.
     * @return The tension to add.
     */
    public static int rub(TribeCharacter ch, int borders) {
        return (int)Math.round(Math.min(MAX_BORDERS, Math.max(0, borders)) * ch.war);
    }

    /**
     * Turn what happened since the last turn into a tribe's memories:
     * the same as a European's, and a settlement destroyed, a mission
     * set up, arms sold to us (trade) or to our rival.
     *
     * @param mem The tribe's {@code Memories}.
     * @param me The tribe's nation id.
     * @param rival Its old rival, or null.
     * @param d What happened.
     * @param before The game before.
     * @param turn The turn to date the memories.
     */
    public static void remember(Memories mem, String me, String rival,
                                List<Observed> d, Snapshot before, int turn) {
        mem.remember(me, d, before, turn);
        for (Observed o : d) {
            switch (o.what) {
            case DESTROY:
                if (me.equals(o.other) && o.actor != null) {
                    mem.add(new Memory(Memory.Kind.SETTLEMENT_DESTROYED,
                                       o.actor, turn, 1));
                }
                break;
            case MISSION:
                if (me.equals(o.other) && o.actor != null) {
                    mem.add(new Memory(Memory.Kind.MISSION, o.actor, turn, 1));
                }
                break;
            case ARM_TRIBE:
                if (o.actor == null) break;
                if (me.equals(o.other)) {
                    mem.add(new Memory(Memory.Kind.TRADE, o.actor, turn, 1));
                } else if (o.other != null && o.other.equals(rival)) {
                    mem.add(new Memory(Memory.Kind.ARMED_RIVAL, o.actor, turn, 1));
                }
                break;
            case WAR:
                if (me.equals(o.other) && o.actor != null
                    && before.stance(me, o.actor) != Stance.WAR) {
                    mem.add(new Memory(Memory.Kind.WAR, o.actor, turn, 1));
                }
                break;
            default:
                break;
            }
        }
    }
}
