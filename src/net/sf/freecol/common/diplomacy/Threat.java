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

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * How threatening each European nation looks: its share of all armies
 * and of all output, the armed share counting more.
 */
public final class Threat {

    /** Weight of the army share; the rest is output. */
    public static final double ARMY_WEIGHT = 0.6;

    /** Above this, others start looking for allies against a nation. */
    public static final double COALITION_LINE = 0.35;

    private Threat() {}


    /**
     * The threat formula.
     *
     * @param army The nation's army.
     * @param armies All European armies together.
     * @param output The nation's output.
     * @param outputs All European outputs together.
     * @return The threat, 0..1.
     */
    public static double threat(double army, double armies, double output,
                                double outputs) {
        final double a = (armies <= 0) ? 0 : army / armies;
        final double o = (outputs <= 0) ? 0 : output / outputs;
        return ARMY_WEIGHT * a + (1 - ARMY_WEIGHT) * o;
    }

    /**
     * The threat of every European nation as one nation sees them.
     *
     * @param w The {@code WorldView}.
     * @return Nation id to threat.
     */
    public static Map<String, Double> all(WorldView w) {
        double armies = Math.max(0, w.me.army), outputs = Math.max(0, w.me.output);
        for (NationView v : w.others.values()) {
            if (!v.european) continue;
            armies += Math.max(0, v.army);
            outputs += Math.max(0, v.output);
        }
        Map<String, Double> ret = new LinkedHashMap<>();
        ret.put(w.me.id, threat(w.me.army, armies, w.me.output, outputs));
        for (NationView v : w.others.values()) {
            if (!v.european) continue;
            ret.put(v.id, threat(v.army, armies, v.output, outputs));
        }
        return ret;
    }

    /**
     * The leader other than me, if it is over the coalition line.
     *
     * @param w The {@code WorldView}.
     * @return The nation id, or null.
     */
    public static String leaderToContain(WorldView w) {
        String best = null;
        double top = COALITION_LINE;
        for (Map.Entry<String, Double> e : all(w).entrySet()) {
            if (e.getKey().equals(w.me.id)) continue;
            if (e.getValue() >= top) {
                top = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }
}
