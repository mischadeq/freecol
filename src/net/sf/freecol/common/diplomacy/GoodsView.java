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
 * One kind of goods as a nation holds it: in store, made per turn, room
 * left, and its price in that nation's Europe market.
 */
public final class GoodsView {

    public final String id;
    /** In store, over all colonies. */
    public final int stock;
    /** Net made per turn, over all colonies. */
    public final int net;
    /** Most any one colony holds above 80% of its warehouse. */
    public final int overflow;
    /** Turns until the first colony runs out (Integer.MAX_VALUE never). */
    public final int turnsLeft;
    /** Paid per unit when selling in Europe; cost when buying. */
    public final int sellPrice, buyPrice;
    /** Boycotted in Europe. */
    public final boolean boycotted;


    public GoodsView(String id, int stock, int net, int overflow,
                     int turnsLeft, int sellPrice, int buyPrice,
                     boolean boycotted) {
        this.id = id;
        this.stock = stock;
        this.net = net;
        this.overflow = overflow;
        this.turnsLeft = turnsLeft;
        this.sellPrice = sellPrice;
        this.buyPrice = buyPrice;
        this.boycotted = boycotted;
    }

    /** A surplus worth offering to another nation. */
    public boolean surplus() {
        return overflow > 0 || (boycotted && stock > 100);
    }

    /** About to run short (within 3 turns). */
    public boolean short3() {
        return net < 0 && turnsLeft <= 3;
    }
}
