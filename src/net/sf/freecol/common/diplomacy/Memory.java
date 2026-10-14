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
 * Something a nation remembers about another, with a weight that fades
 * to nothing over a number of turns.
 */
public final class Memory {

    /** The kinds of memory, with their weight and how long they last. */
    public enum Kind {
        WAR(-30, 30),
        COLONY_TAKEN(-60, 60),
        PEACE(10, 20),
        UNIT_LOST(-5, 10),
        SHIP_DAMAGED(-3, 10),
        SETTLEMENT_DESTROYED(-60, 80);

        public final int weight;
        public final int fade;

        Kind(int weight, int fade) {
            this.weight = weight;
            this.fade = fade;
        }
    }

    public final Kind kind;
    /** The nation it is about (id). */
    public final String about;
    /** The turn it happened. */
    public final int turn;
    /** How many times (e.g. units lost that turn). */
    public final int count;


    public Memory(Kind kind, String about, int turn, int count) {
        this.kind = kind;
        this.about = about;
        this.turn = turn;
        this.count = count;
    }

    /**
     * What this memory is worth now.
     *
     * @param now The current turn.
     * @return The faded weight (0 once forgotten, or for a future turn).
     */
    public double value(int now) {
        final int age = now - turn;
        if (age < 0 || age >= kind.fade) return 0;
        return kind.weight * count * (1.0 - age / (double)kind.fade);
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + " " + Action.shortId(about)
            + " t" + turn + ((count > 1) ? " x" + count : "");
    }
}
