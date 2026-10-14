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


/**
 * Opinion, trust and fear of one nation towards another.
 */
public final class Relations {

    /** The game's highest tension (Tension.TENSION_MAX). */
    public static final int TENSION_MAX = 1100;

    /** Trust before any deal has been made or broken. */
    public static final int DEFAULT_TRUST = 50;

    private Relations() {}


    /**
     * Opinion of another nation, -100 (hates) to +100 (loves): the
     * game's tension turned around (0 tension is +100, the maximum is
     * -100) plus what is remembered.
     *
     * @param tension The tension towards it (0..1100), or negative if
     *     the game keeps none (a human player); counts as neutral.
     * @param memories Memories (only those about this nation count).
     * @param about The nation id.
     * @param now The current turn.
     * @return The opinion.
     */
    public static double opinion(int tension, Collection<Memory> memories,
                                 String about, int now) {
        double base = (tension < 0) ? 0
            : 100.0 - Math.min(tension, TENSION_MAX) * 200.0 / TENSION_MAX;
        if (memories != null) {
            for (Memory m : memories) {
                if (about.equals(m.about)) base += m.value(now);
            }
        }
        return Math.max(-100, Math.min(100, base));
    }

    /**
     * Trust in another nation, 0..100: {@link #DEFAULT_TRUST} plus what
     * kept and broken deals, gifts and help did to it.
     *
     * @param memories Memories (only those about this nation count).
     * @param about The nation id.
     * @param now The current turn.
     * @return The trust.
     */
    public static double trust(Collection<Memory> memories, String about,
                               int now) {
        double t = DEFAULT_TRUST;
        if (memories != null) {
            for (Memory m : memories) {
                if (about.equals(m.about)) t += m.trust(now);
            }
        }
        return Math.max(0, Math.min(100, t));
    }

    /**
     * Fear of another nation, 0..100: its share of the two armies,
     * times how near it is.
     *
     * @param myArmy My army in soldiers' worth.
     * @param theirArmy Their army.
     * @param near Nearness, 0..1.
     * @return The fear.
     */
    public static double fear(double myArmy, double theirArmy, double near) {
        if (theirArmy <= 0) return 0;
        return 100.0 * theirArmy / (Math.max(0, myArmy) + theirArmy) * near;
    }
}
