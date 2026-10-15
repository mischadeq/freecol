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
 * The character of a native tribe (mod, step 7a, decided with Mischa):
 * warlike Apache, Sioux, Aztec and Inca; peaceful Arawak; trading
 * Tupi; the others (Cherokee, Iroquois) in the middle.
 */
public enum TribeCharacter {

    //        war  gifts help  word
    WARLIKE(  1.4, 0.7, 1.2, "warlike"),
    MIDDLING( 1.0, 1.0, 1.0, "proud"),
    TRADERS(  0.8, 1.2, 0.9, "traders"),
    PEACEFUL( 0.6, 1.5, 0.8, "peaceful");

    /** How quickly grudges grow into war (and old rivals rub). */
    public final double war;
    /** How often they bring gifts. */
    public final double gifts;
    /** How readily they answer a call for help. */
    public final double help;
    /** In words, for the companion page. */
    public final String word;

    TribeCharacter(double war, double gifts, double help, String word) {
        this.war = war;
        this.gifts = gifts;
        this.help = help;
        this.word = word;
    }

    /**
     * The character of a tribe.
     *
     * @param nationId The nation id, e.g. "model.nation.sioux".
     * @return Its {@code TribeCharacter}.
     */
    public static TribeCharacter of(String nationId) {
        if (nationId == null) return MIDDLING;
        switch (nationId) {
        case "model.nation.apache": case "model.nation.sioux":
        case "model.nation.aztec": case "model.nation.inca":
            return WARLIKE;
        case "model.nation.arawak":
            return PEACEFUL;
        case "model.nation.tupi":
            return TRADERS;
        default:
            return MIDDLING;
        }
    }

    /**
     * The opinion at or under which a tribe goes to war with its old
     * rival (when it also holds a grudge).
     *
     * @return The war line, -100..0.
     */
    public double warLine() {
        return Math.max(-95, -55 / war);
    }
}
