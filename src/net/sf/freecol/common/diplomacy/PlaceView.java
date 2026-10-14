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
 * A colony or native settlement as one nation sees it: where it is,
 * how big, and how hard it would be to take.
 */
public final class PlaceView {

    public final String id;
    public final String name;
    /** The owner's nation id. */
    public final String owner;
    public final int x, y;
    /** Land mass (the tile's contiguity). */
    public final int land;
    public final boolean coastal;
    /** Colonists (or settlement size). */
    public final int size;
    public final boolean nativeSettlement;
    /**
     * Defence of each armed defender, strongest first (estimated in
     * the honest view).  The place falls after all of them are beaten
     * and one more fight is won (an unarmed colonist defends last);
     * a native settlement falls when its last brave is beaten.
     */
    public final double[] defence;
    /** What a weak last defender has (an unarmed colonist). */
    public final double lastDefence;


    public PlaceView(String id, String name, String owner, int x, int y,
                     int land, boolean coastal, int size,
                     boolean nativeSettlement, double[] defence,
                     double lastDefence) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.x = x;
        this.y = y;
        this.land = land;
        this.coastal = coastal;
        this.size = size;
        this.nativeSettlement = nativeSettlement;
        this.defence = defence;
        this.lastDefence = lastDefence;
    }

    /**
     * What taking it is worth, in points: a colony 40 + 3 per colonist,
     * a native settlement 20 + 2 per inhabitant.
     *
     * @return The value.
     */
    public double value() {
        return (nativeSettlement) ? 20 + 2 * size : 40 + 3 * size;
    }

    /**
     * Distance in tiles (the larger of the two axis distances, a rough
     * measure on FreeCol's diamond map).
     *
     * @param o Another place.
     * @return The distance.
     */
    public int distance(PlaceView o) {
        return Math.max(Math.abs(x - o.x), Math.abs(y - o.y) / 2);
    }
}
