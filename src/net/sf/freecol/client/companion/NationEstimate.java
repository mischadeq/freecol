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

package net.sf.freecol.client.companion;

import java.util.SortedMap;

import net.sf.freecol.common.diplomacy.Estimates;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;


/**
 * The companion page's name for {@link Estimates}, where the honest
 * estimates about other nations now live (shared with the diplomat).
 */
public final class NationEstimate {

    /** How many recorded turns to average over. */
    public static final int WINDOW = Estimates.WINDOW;

    /** Verdict words, see {@link Estimates#verdict}. */
    public static final String FAR_BELOW = Estimates.FAR_BELOW,
        BELOW = Estimates.BELOW, EVEN = Estimates.EVEN,
        ABOVE = Estimates.ABOVE, FAR_ABOVE = Estimates.FAR_ABOVE;

    private NationEstimate() {}

    public static double soldierPower(Specification spec) {
        return Estimates.soldierPower(spec);
    }

    public static double frigatePower(Specification spec) {
        return Estimates.frigatePower(spec);
    }

    public static double equivalent(int strength, double unitPower) {
        return Estimates.equivalent(strength, unitPower);
    }

    public static Double perTurn(SortedMap<Integer, Integer> byTurn,
                                 int window) {
        return Estimates.perTurn(byTurn, window);
    }

    public static double rebels(int solPercent, int colonists) {
        return Estimates.rebels(solPercent, colonists);
    }

    public static Double bellsPerTurn(SortedMap<Integer, Integer> rebelsByTurn,
                                      int window) {
        return Estimates.bellsPerTurn(rebelsByTurn, window);
    }

    public static double valuePerColonist(Player player) {
        return Estimates.valuePerColonist(player);
    }

    public static double colonyValue(Colony colony, Market market) {
        return Estimates.colonyValue(colony, market);
    }

    public static int output(int colonists, double perColonist) {
        return Estimates.output(colonists, perColonist);
    }

    public static String verdict(double theirs, double ours) {
        return Estimates.verdict(theirs, ours);
    }
}
