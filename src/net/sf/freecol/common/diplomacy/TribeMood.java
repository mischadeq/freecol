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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * How a tribe feels, as of its last turn (mod, step 7g), for the
 * companion page.  It holds everything; the page shows only what the
 * player could know.
 */
public final class TribeMood {

    public final String nation;
    public final int turn;
    public final TribeCharacter character;
    /** Its old rival, or null. */
    public final String rival;
    /** Opinion of each nation (tribes and Europeans). */
    public final Map<String, Double> opinions = new LinkedHashMap<>();
    /** Trust in each European. */
    public final Map<String, Double> trust = new LinkedHashMap<>();
    /** European to the turns of unrest so far against it. */
    public final Map<String, Integer> unrest = new LinkedHashMap<>();
    /** Turns of unrest before an uprising. */
    public final int unrestTurns;
    /** The nations it is at war with (wars are public). */
    public final java.util.Set<String> atWar = new java.util.HashSet<>();
    /** What it remembers, strongest first. */
    public final List<Memory> memories = new ArrayList<>();


    public TribeMood(String nation, int turn, TribeCharacter character,
                     String rival, int unrestTurns) {
        this.nation = nation;
        this.turn = turn;
        this.character = character;
        this.rival = rival;
        this.unrestTurns = unrestTurns;
    }

    /**
     * Its friends among the tribes: opinion at {@link Mood#FRIENDLY}
     * or more.
     *
     * @param tribes The nation ids of the tribes.
     * @return The friends.
     */
    public List<String> friends(java.util.Collection<String> tribes) {
        List<String> ret = new ArrayList<>();
        for (String t : tribes) {
            final Double o = opinions.get(t);
            if (o != null && o >= Mood.FRIENDLY && !t.equals(rival)) ret.add(t);
        }
        return ret;
    }
}
