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


/**
 * A coalition: nations allied against one that grows too strong (a
 * threat over the coalition line).  Kept by the nation that started it.
 */
public final class Coalition {

    /** Threat under which the target no longer worries anyone... */
    public static final double CALM_LINE = 0.25;
    /** ... for this many turns: then the coalition ends. */
    public static final int CALM_TURNS = 10;
    /** Turns a coalition of one waits for answers to its invitations. */
    public static final int INVITE_TURNS = 5;
    /** Combined army over the target's to go to war together. */
    public static final double STRENGTH = 1.2;

    public final String target;
    /** Members, the founder first. */
    public final List<String> members = new ArrayList<>();
    public final int since;
    /** First turn of the current calm stretch, -1 if not calm. */
    public int calmSince = -1;


    public Coalition(String target, int since) {
        this.target = target;
        this.since = since;
    }

    /**
     * The coalition line for a difficulty level: coalitions against a
     * strong player form sooner on harder levels.
     *
     * @param difficulty The difficulty level id, may be null.
     * @return The threat at which a coalition forms.
     */
    public static double line(String difficulty) {
        if (difficulty == null) return Threat.COALITION_LINE;
        switch (difficulty) {
        case "model.difficulty.veryEasy": return 0.40;
        case "model.difficulty.easy": return 0.38;
        case "model.difficulty.hard": return 0.32;
        case "model.difficulty.veryHard": return 0.30;
        default: return Threat.COALITION_LINE;
        }
    }

    /**
     * Note the target's threat this turn.
     *
     * @param threat The target's threat.
     * @param now The turn.
     * @return True when the coalition is no longer needed.
     */
    public boolean over(double threat, int now) {
        // Alone: waiting for answers a few turns, then given up.
        if (members.size() < 2) return now - since >= INVITE_TURNS;
        if (threat >= CALM_LINE) {
            calmSince = -1;
            return false;
        }
        if (calmSince < 0) calmSince = now;
        return now - calmSince >= CALM_TURNS;
    }

    public String serialize() {
        return target + "|" + since + "|" + calmSince + "|"
            + String.join(",", members);
    }

    public static Coalition parse(String s) {
        final String[] f = s.split("\\|", -1);
        if (f.length != 4) return null;
        try {
            Coalition c = new Coalition(f[0], Integer.parseInt(f[1]));
            c.calmSince = Integer.parseInt(f[2]);
            if (!f[3].isEmpty()) {
                for (String m : f[3].split(",")) c.members.add(m);
            }
            return c;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
