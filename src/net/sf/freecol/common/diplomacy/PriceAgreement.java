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
 * Price agreements (mod, step 4): a nation buys a good at a fixed price
 * for a number of turns, as much as the other brings by ship to its
 * coastal colonies.  The buyer must still make a profit selling it in
 * Europe, so the price stays under its own Europe price after tax.
 */
public final class PriceAgreement {

    /** Shortest and longest agreement, in turns. */
    public static final int MIN_TURNS = 5, MAX_TURNS = 30;
    /** Default length. */
    public static final int TURNS = 20;
    /** The highest share of its net Europe price a buyer agrees to. */
    public static final double MAX_SHARE = 0.85;
    /** The share it answers with when asked too much. */
    public static final double COUNTER_SHARE = 0.75;
    /** The share it offers when it proposes for goods it needs. */
    public static final double NEED_SHARE = 0.85;
    /** Most goods one colony takes in one visit. */
    public static final int MAX_PER_VISIT = 200;

    private PriceAgreement() {}

    /**
     * What a buyer gets for one unit in Europe, after tax.
     *
     * @param paidForSale Its market's price per unit.
     * @param tax Its tax rate in percent.
     * @return The net price per unit.
     */
    public static int net(int paidForSale, int tax) {
        return Math.max(0, paidForSale * (100 - Math.max(0, tax)) / 100);
    }

    /**
     * Does a buyer agree to a price?
     *
     * @param price The price per unit asked.
     * @param net Its net Europe price per unit.
     * @return True if it still makes a profit.
     */
    public static boolean accepts(int price, int net) {
        return price > 0 && price <= Math.floor(MAX_SHARE * net);
    }

    /**
     * The price a buyer answers with when asked too much.
     *
     * @param net Its net Europe price per unit.
     * @return The counter price, 0 if the good is worth nothing to it.
     */
    public static int counter(int net) {
        return (int)Math.round(COUNTER_SHARE * net);
    }

    /**
     * How many goods a colony takes in one visit: what its warehouse
     * still holds, at most {@link #MAX_PER_VISIT}, and what it can pay.
     *
     * @param capacity Its warehouse capacity.
     * @param have What it holds of the good.
     * @param gold The buyer's gold.
     * @param price The price per unit.
     * @return The most it takes, 0 when full.
     */
    public static int room(int capacity, int have, int gold, int price) {
        final int space = Math.max(0, Math.min(MAX_PER_VISIT, capacity - have));
        return (price <= 0) ? space : Math.min(space, Math.max(0, gold) / price);
    }

    /**
     * Clamp an agreement's length.
     *
     * @param turns The turns asked.
     * @return The turns, between {@link #MIN_TURNS} and {@link #MAX_TURNS}.
     */
    public static int turns(int turns) {
        return Math.max(MIN_TURNS, Math.min(MAX_TURNS, turns));
    }
}
