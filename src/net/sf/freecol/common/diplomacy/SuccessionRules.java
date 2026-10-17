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


/**
 * The War of Spanish Succession, closer to Utrecht (mod).  The game's
 * old event gave every colony of the weakest computer nation to the
 * strongest at once.  Now a war in Europe is announced first; a few
 * turns later the loser cedes only its colonies nearest the winner
 * (as France ceded Newfoundland and Acadia but kept Canada); small
 * settlements are abandoned (as Plaisance was), and a tenth of the
 * people of the bigger ones sail home rather than live under the new
 * flag (officials, officers, merchants: the skilled first).  The loser
 * stays in the game.
 *
 * Pure rules, no game objects.
 */
public final class SuccessionRules {

    /** Turns from the war to the treaty. */
    public static final int TREATY_TURNS = 6;
    /** Share of the loser's colonies ceded. */
    public static final double CEDED_SHARE = 0.4;
    /** Colonies smaller than this are abandoned, not ceded. */
    public static final int SMALL = 3;
    /** Share of a ceded colony's people that leave. */
    public static final double LEAVING = 0.10;

    private SuccessionRules() {}


    /**
     * How many colonies the loser cedes: about two in five, at least
     * one, and it always keeps one.
     *
     * @param colonies Its colonies.
     * @return The number to cede.
     */
    public static int ceded(int colonies) {
        if (colonies <= 1) return 0;
        return Math.min(colonies - 1,
                        Math.max(1, (int)Math.ceil(colonies * CEDED_SHARE)));
    }

    /**
     * Is a colony too small to cede (abandoned instead)?
     *
     * @param size Its colonists.
     * @return True if abandoned.
     */
    public static boolean abandoned(int size) {
        return size < SMALL;
    }

    /**
     * How many colonists of a ceded colony sail home.
     *
     * @param size Its colonists.
     * @return The number leaving (always fewer than the colony has).
     */
    public static int leaving(int size) {
        if (abandoned(size)) return size;
        return Math.min(size - 1, (int)Math.round(size * LEAVING));
    }

    /**
     * The pending war as a string, for the save.
     *
     * @param loser The loser's nation id.
     * @param winner The winner's nation id.
     * @param treatyTurn The turn of the treaty.
     * @return The string.
     */
    public static String pending(String loser, String winner, int treatyTurn) {
        return loser + "|" + winner + "|" + treatyTurn;
    }

    /**
     * Read a pending war.
     *
     * @param s The string ({@link #pending}).
     * @return {loser, winner, treaty turn}, or null if unreadable.
     */
    public static String[] parse(String s) {
        if (s == null) return null;
        final String[] f = s.split("\\|");
        if (f.length != 3) return null;
        try {
            Integer.parseInt(f[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        return f;
    }
}
