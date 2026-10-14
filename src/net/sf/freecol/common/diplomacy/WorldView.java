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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.model.Stance;


/**
 * Everything one nation knows at the start of a turn: itself exactly,
 * the others as {@link NationView}s, and its memories.  The diplomat
 * decides from this alone, so whether it is honest depends only on how
 * it was built (see {@link WorldViewBuilder}).
 */
public final class WorldView {

    public final int turn;
    public final NationView me;
    public final NationCharacter character;
    /** The other nations it has met, by id. */
    public final Map<String, NationView> others = new LinkedHashMap<>();
    /** Its goods, by goods type id. */
    public final Map<String, GoodsView> goods = new LinkedHashMap<>();
    /** Attack of each of its land units that can attack, strongest first. */
    public double[] attackers = new double[0];
    /** Room on its ships, in units. */
    public int carrierSpace;
    /** Muskets and horses in store, and the price of muskets. */
    public int muskets, horses, musketPrice;
    /** The cheapest ship for an army on sale in Europe: price and room. */
    public int shipPrice, shipSpace;
    /** Colonists that could be armed (not needed to keep a colony). */
    public int armable;
    /** The soldier's attack (for guesses about other armies). */
    public double soldierAttack = 3;
    /** Its tax rate. */
    public int tax;
    /** The King's land army waiting in Europe, in soldiers' worth. */
    public double kingArmy;
    /** Its rebels, in percent. */
    public int rebels;
    /** Nations at war with it that have armed units near its colonies. */
    public final java.util.Set<String> enemiesAtGates = new java.util.HashSet<>();
    /** What it remembers. */
    public final List<Memory> memories = new ArrayList<>();
    /** Stances between other nations ("a|b" to stance); wars are public. */
    private final Map<String, Stance> between = new HashMap<>();


    public WorldView(int turn, NationView me, NationCharacter character) {
        this.turn = turn;
        this.me = me;
        this.character = character;
    }

    public void setStanceBetween(String a, String b, Stance s) {
        between.put(key(a, b), s);
    }

    /**
     * The stance between two other nations (or between me and one).
     *
     * @param a A nation id.
     * @param b Another nation id.
     * @return The stance, PEACE if unknown.
     */
    public Stance stanceBetween(String a, String b) {
        if (a.equals(me.id)) return stanceOf(b);
        if (b.equals(me.id)) return stanceOf(a);
        Stance s = between.get(key(a, b));
        return (s == null) ? Stance.PEACE : s;
    }

    private Stance stanceOf(String other) {
        NationView v = others.get(other);
        return (v == null) ? Stance.UNCONTACTED : v.stance;
    }

    private static String key(String a, String b) {
        return (a.compareTo(b) < 0) ? a + "|" + b : b + "|" + a;
    }

    /** The nations at war with me. */
    public List<NationView> enemies() {
        List<NationView> ret = new ArrayList<>();
        for (NationView v : others.values()) if (v.atWar()) ret.add(v);
        return ret;
    }

    /**
     * Exposure: the armies at war with me that can reach me.
     *
     * @return Soldiers' worth.
     */
    public double exposure() {
        double sum = 0;
        for (NationView v : enemies()) sum += Effects.threatOf(v);
        return sum;
    }

    /** The highest fear of any other nation. */
    public double maxFear() {
        double f = 0;
        for (NationView v : others.values()) f = Math.max(f, v.fear);
        return f;
    }

    /** My weakest colony (fewest defenders, then smallest defence). */
    public PlaceView weakestColony() {
        PlaceView best = null;
        for (PlaceView p : me.places) {
            if (best == null || strength(p) < strength(best)) best = p;
        }
        return best;
    }

    static double strength(PlaceView p) {
        double s = p.lastDefence;
        for (double d : p.defence) s += d;
        return s;
    }
}
