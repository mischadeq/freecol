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

import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.common.model.Stance;


/**
 * What one nation knows of another (or of itself, then exact).
 */
public final class NationView {

    public final String id;
    public final boolean european;
    /** True when every figure is exact (itself, or hindsight). */
    public final boolean exact;

    /** The observer's stance towards it. */
    public Stance stance = Stance.PEACE;
    /** Army in soldiers' worth, navy in frigates' worth. */
    public double army, navy;
    /** Output in gold a turn. */
    public double output;
    public int gold;
    /** Colonists in the colonies seen. */
    public int colonists;
    /** Number of colonies or settlements (Foreign Affairs). */
    public int settlements;
    /** The colonies or settlements seen. */
    public final List<PlaceView> places = new ArrayList<>();

    /** The observer's tension towards it (or theirs, mirrored), -1 none. */
    public int tension = -1;
    /** Opinion, -100..100 (tension and memories). */
    public double opinion;
    /** Trust, 0..100. */
    public double trust = Relations.DEFAULT_TRUST;
    /** How near it is to the observer, 0..1. */
    public double near;
    /** Fear of it, 0..100. */
    public double fear;
    /** Where it probably is, from sightings (null if never seen). */
    public Bearings.Bearing bearing;
    /** Gold of my trade with it over the last 20 turns (tribes). */
    public double tradeWithMe;


    public NationView(String id, boolean european, boolean exact) {
        this.id = id;
        this.european = european;
        this.exact = exact;
    }

    public boolean atWar() {
        return stance == Stance.WAR;
    }
}
