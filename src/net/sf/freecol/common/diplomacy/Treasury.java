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
 * A computer nation's treasury (mod): gold kept back from shopping in
 * Europe so it can pay for deals (price agreements, notices, peace,
 * buy-outs, tribes, ultimatums).  Diplomacy may spend it, shopping may
 * not; a nation losing a war dips into it for soldiers.
 */
public final class Treasury {

    /** The reserve every nation keeps... */
    public static final int BASE = 300;
    /** ... plus this much per colony ... */
    public static final int PER_COLONY = 50;
    /** ... up to this much (before its character and promises). */
    public static final int MAX_BASE = 2000;
    /** Goods in one typical load under a price agreement. */
    public static final int LOAD = 100;
    /** Loads a running agreement keeps gold for. */
    public static final int LOADS = 2;

    private Treasury() {}

    /**
     * How much a character keeps back.
     *
     * @param ch The {@code NationCharacter}.
     * @return The multiplier.
     */
    public static double share(NationCharacter ch) {
        if (ch == null) return 1.0;
        switch (ch) {
        case TRADING_EMPIRE: return 1.5;
        case INDEPENDENCE_FIRST: return 1.2;
        case CONQUEROR: return 0.7;
        default: return 1.0;
        }
    }

    /**
     * The reserve.
     *
     * @param colonies Its colonies.
     * @param ch Its character.
     * @param promised Gold it has promised in open deals.
     * @param losing True when it is losing a war: then only the
     *     promises are kept, the rest buys soldiers.
     * @return The gold to keep back from shopping.
     */
    public static int reserve(int colonies, NationCharacter ch, int promised,
                              boolean losing) {
        final int base = (int)Math.round(Math.min(MAX_BASE,
            BASE + PER_COLONY * Math.max(0, colonies)) * share(ch));
        return Math.max(0, promised) + ((losing) ? 0 : base);
    }

    /**
     * What a running price agreement keeps back: a couple of loads.
     *
     * @param price The price per unit.
     * @return The gold.
     */
    public static int forAgreement(int price) {
        return LOADS * LOAD * Math.max(0, price);
    }

    /**
     * Gold free for shopping.
     *
     * @param gold The gold it has.
     * @param reserve Its reserve.
     * @return The gold above the reserve, at least 0.
     */
    public static int spendable(int gold, int reserve) {
        return Math.max(0, gold - Math.max(0, reserve));
    }
}
