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

import java.util.Map;
import java.util.SortedMap;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.UnitType;


/**
 * Honest estimates about other nations, from what a player can know
 * in the game: the Foreign Affairs figures (recorded turn by turn),
 * the colonies seen on the map, and the player's own colonies as a
 * yardstick.  Shared by the companion page and the diplomat.
 *
 * Nothing here looks at hidden data of other nations.
 */
public final class Estimates {

    /** Attack bonus every attacker gets (+50%). */
    private static final double ATTACK_BONUS = 1.5;

    /** How many recorded turns to average over. */
    public static final int WINDOW = 5;

    private Estimates() {}


    /**
     * The attack power of one soldier (a free colonist with muskets),
     * the unit military strength is expressed in.
     *
     * @param spec The {@code Specification}.
     * @return The attack power.
     */
    public static double soldierPower(Specification spec) {
        final UnitType colonist = spec.getDefaultUnitType();
        final Role soldier = spec.getRole("model.role.soldier");
        final double p = ATTACK_BONUS * (colonist.getOffence()
            + ((soldier == null) ? 2 : soldier.getOffence()));
        return (p <= 0) ? 3 : p;
    }

    /**
     * The attack power of one frigate, the unit naval strength is
     * expressed in.
     *
     * @param spec The {@code Specification}.
     * @return The attack power.
     */
    public static double frigatePower(Specification spec) {
        final UnitType frigate = spec.getUnitType("model.unit.frigate");
        final double p = ATTACK_BONUS * ((frigate == null) ? 16
            : frigate.getOffence());
        return (p <= 0) ? 24 : p;
    }

    /**
     * Express a strength in a number of units, to one decimal.
     *
     * @param strength The strength (sum of attack powers), or negative
     *     if unknown.
     * @param unitPower The power of one unit.
     * @return The number of units, or -1 if unknown.
     */
    public static double equivalent(int strength, double unitPower) {
        if (strength < 0 || unitPower <= 0) return -1;
        return Math.round(10.0 * strength / unitPower) / 10.0;
    }

    /**
     * Average change per turn of a figure over the last turns recorded.
     *
     * @param byTurn Turn number to value (negative values are unknown).
     * @param window How many turns back to look at most.
     * @return The average change per turn, or null if fewer than two
     *     known values.
     */
    public static Double perTurn(SortedMap<Integer, Integer> byTurn,
                                 int window) {
        if (byTurn.isEmpty()) return null;
        final int last = byTurn.lastKey();
        Integer firstTurn = null, firstVal = null, lastVal = null;
        for (Map.Entry<Integer, Integer> e
                 : byTurn.tailMap(last - window).entrySet()) {
            if (e.getValue() == null || e.getValue() < 0) continue;
            if (firstTurn == null) {
                firstTurn = e.getKey();
                firstVal = e.getValue();
            }
            lastVal = e.getValue();
        }
        if (firstTurn == null || firstTurn == last
            || byTurn.get(last) == null || byTurn.get(last) < 0) return null;
        return (lastVal - firstVal) / (double)(last - firstTurn);
    }

    /**
     * Rebels in a nation: the rebel share times the colonists.
     *
     * @param solPercent The share of rebels, in percent.
     * @param colonists The colonists in colonies.
     * @return The number of rebels (fractional), or -1 if unknown.
     */
    public static double rebels(int solPercent, int colonists) {
        if (solPercent < 0 || colonists < 0) return -1;
        return solPercent * colonists / 100.0;
    }

    /**
     * Estimate the liberty bells a nation makes per turn from how its
     * number of rebels grows: every rebel takes
     * {@link Colony#LIBERTY_PER_REBEL} bells.  Bells that only keep up
     * with new colonists are counted too, as the share is applied to
     * the colonists of each turn.
     *
     * @param rebelsByTurn Turn to rebels (times 10, to keep a decimal;
     *     negative unknown).
     * @param window How many turns back to look.
     * @return Bells per turn (never negative), or null if unknown.
     */
    public static Double bellsPerTurn(SortedMap<Integer, Integer> rebelsByTurn,
                                      int window) {
        Double d = perTurn(rebelsByTurn, window);
        if (d == null) return null;
        return Math.max(0, d / 10.0 * Colony.LIBERTY_PER_REBEL);
    }

    /**
     * What one of the player's own colonists makes per turn, in gold at
     * the player's Europe prices: the yardstick for other nations.
     * Only surpluses of goods that can be sold count (what is used up
     * in the colony is not counted twice).
     *
     * @param player The client {@code Player}.
     * @return Gold per colonist per turn, or 0 if unknown.
     */
    public static double valuePerColonist(Player player) {
        final Market market = player.getMarket();
        if (market == null) return 0;
        int colonists = 0;
        double value = 0;
        for (Colony c : player.getColonyList()) {
            colonists += c.getUnitCount();
            value += colonyValue(c, market);
        }
        return (colonists == 0) ? 0 : value / colonists;
    }

    /**
     * What a colony makes per turn, in gold at Europe prices (its
     * surpluses of goods that can be sold).
     *
     * @param colony The {@code Colony}.
     * @param market The {@code Market} for prices.
     * @return Gold per turn.
     */
    public static double colonyValue(Colony colony, Market market) {
        if (market == null) return 0;
        double value = 0;
        for (GoodsType gt : colony.getSpecification().getStorableGoodsTypeList()) {
            final int net = colony.getNetProductionOf(gt);
            if (net <= 0) continue;
            value += net * (double)market.getPaidForSale(gt);
        }
        return value;
    }

    /**
     * Estimate a nation's output: its colonists times what one of ours
     * makes.
     *
     * @param colonists Their colonists in colonies (seen on the map).
     * @param perColonist Our gold per colonist per turn.
     * @return Gold per turn, or -1 if unknown.
     */
    public static int output(int colonists, double perColonist) {
        if (colonists < 0 || perColonist <= 0) return -1;
        return (int)Math.round(colonists * perColonist);
    }

    /** How another nation compares to us, in words (see verdict). */
    public static final String FAR_BELOW = "farBelow", BELOW = "below",
        EVEN = "even", ABOVE = "above", FAR_ABOVE = "farAbove";

    /**
     * Compare a figure of another nation with ours, the way the
     * Relations panel says "happy" instead of a number: far below
     * (under half), below (under 80%), even (within a quarter), above
     * (up to double) or far above (more than double).
     *
     * @param theirs Their figure (negative if unknown).
     * @param ours Our figure.
     * @return The verdict, or null if unknown.
     */
    public static String verdict(double theirs, double ours) {
        if (theirs < 0 || ours < 0) return null;
        if (ours == 0) return (theirs == 0) ? EVEN : FAR_ABOVE;
        final double r = theirs / ours;
        return (r < 0.5) ? FAR_BELOW
            : (r < 0.8) ? BELOW
            : (r <= 1.25) ? EVEN
            : (r <= 2.0) ? ABOVE
            : FAR_ABOVE;
    }
}
