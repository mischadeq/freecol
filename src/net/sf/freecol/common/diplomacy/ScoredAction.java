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

import java.util.Locale;


/**
 * A move with its score and every part of the sum, so a report can say
 * why the diplomat likes it.
 */
public final class ScoredAction implements Comparable<ScoredAction> {

    public final Action action;
    /** Expected changes over the horizon, in points. */
    public double gold, trade, land, security, relations;
    /** Chance of failure times what is lost, in points. */
    public double risk;
    /** Goal and ambition bonus. */
    public double goal;
    /** The opinion term. */
    public double opinion;
    /** The total score. */
    public double total;
    /** A short explanation (which colony, what chance). */
    public String note = "";


    public ScoredAction(Action action) {
        this.action = action;
    }

    /**
     * Add up the score with a nation's weights.
     *
     * @param c The {@code NationCharacter}.
     * @return This, scored.
     */
    public ScoredAction score(NationCharacter c) {
        total = c.gold * gold + c.trade * trade + c.land * land
            + c.security * security + c.relations * relations
            + goal + opinion - c.rho * risk;
        return this;
    }

    @Override
    public int compareTo(ScoredAction o) {
        return Double.compare(o.total, total); // highest first
    }

    /** The parts, for a tooltip. */
    public String parts() {
        return String.format(Locale.ROOT,
            "gold %.1f, trade %.1f, land %.1f, security %.1f, relations %.1f,"
            + " goal %.0f, opinion %.1f, risk %.1f", gold, trade, land,
            security, relations, goal, opinion, risk);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s = %.1f", action.label(), total);
    }
}
