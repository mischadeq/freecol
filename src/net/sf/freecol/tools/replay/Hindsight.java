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

package net.sf.freecol.tools.replay;

import java.util.List;

import net.sf.freecol.common.diplomacy.NationCharacter;
import net.sf.freecol.common.diplomacy.Snapshot;


/**
 * How things turned out for a nation over the turns after a move,
 * compared with how things usually go for it: the figures of the next
 * {@link #TURNS} saves, weighed by the nation's own character, minus its
 * average over the whole game.  Positive means better than usual.
 */
public final class Hindsight {

    /** Turns looked ahead. */
    public static final int TURNS = 10;

    private Hindsight() {}


    /**
     * What a nation gained from turn index i to i + TURNS, in points.
     *
     * @param snaps The snapshots in turn order.
     * @param i The index of the starting snapshot.
     * @param id The nation id.
     * @param c Its {@code NationCharacter}.
     * @return The points, or null if not enough turns follow or the
     *     nation is gone.
     */
    public static Double gained(List<Snapshot> snaps, int i, String id,
                                NationCharacter c) {
        if (i + TURNS >= snaps.size()) return null;
        Snapshot.Nation a = snaps.get(i).nations.get(id);
        Snapshot.Nation b = snaps.get(i + TURNS).nations.get(id);
        if (a == null || b == null) return null;
        return c.gold * (b.gold - a.gold) / 100.0
            + c.trade * (b.income - a.income) / 100.0
            + c.land * (40.0 * (b.colonies - a.colonies)
                        + 3.0 * (b.colonists - a.colonists))
            + c.security * 5.0 * (b.army - a.army);
    }

    /**
     * The average gain of a nation over all windows of the game.
     *
     * @param snaps The snapshots.
     * @param id The nation id.
     * @param c Its character.
     * @return The average, 0 if none.
     */
    public static double usual(List<Snapshot> snaps, String id,
                               NationCharacter c) {
        double sum = 0;
        int n = 0;
        for (int i = 0; i < snaps.size(); i++) {
            Double g = gained(snaps, i, id, c);
            if (g == null) continue;
            sum += g;
            n++;
        }
        return (n == 0) ? 0 : sum / n;
    }

    /**
     * How a move at snapshot i turned out: gain minus the usual.
     *
     * @param snaps The snapshots.
     * @param i The index.
     * @param id The nation id.
     * @param c Its character.
     * @param usual Its usual gain (see {@link #usual}).
     * @return The difference, or null if unknown.
     */
    public static Double outcome(List<Snapshot> snaps, int i, String id,
                                 NationCharacter c, double usual) {
        Double g = gained(snaps, i, id, c);
        return (g == null) ? null : g - usual;
    }
}
