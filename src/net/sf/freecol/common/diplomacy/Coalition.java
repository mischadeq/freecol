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
    /** The joint plan: the colony all members strike (id), its name,
        and the turn they land together; null / -1 without a plan. */
    public String jointTarget;
    public String jointName = "";
    public int strikeTurn = -1;


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

    // Buy-outs: the target pays a member to leave.

    /** Synergy every member starts with, in points. */
    public static final double BASE_SYNERGY = 5;
    /** Least synergy, in points. */
    public static final double MIN_SYNERGY = 2;
    /** Gold asked per point of synergy (1 point is 100 gold, plus a margin). */
    public static final double GOLD_PER_POINT = 150;
    /** Least price, in gold. */
    public static final int MIN_PRICE = 300;

    /**
     * How tightly a member is bound to its coalition, in points: what
     * leaving would cost it.
     *
     * @param opinion Its average opinion of the other members (-100..100).
     * @param trust Its average trust in them (0..100).
     * @param threat The target's threat as it sees it (0..1).
     * @param turns Turns the coalition has lasted.
     * @param sharedEnemies Nations it fights together with another member.
     * @return The synergy, at least {@link #MIN_SYNERGY}.
     */
    public static double synergy(double opinion, double trust, double threat,
                                 int turns, int sharedEnemies) {
        final double s = BASE_SYNERGY
            + opinion / 10.0                          // -10..10
            + (trust - Relations.DEFAULT_TRUST) / 10.0 // -5..5
            + 20.0 * Math.max(0, threat)              // fear: 0.35 is 7
            + Math.min(20, Math.max(0, turns)) / 4.0  // 0..5
            + 3.0 * sharedEnemies;
        return Math.max(MIN_SYNERGY, s);
    }

    /**
     * The gold a member asks to leave.
     *
     * @param synergy Its synergy.
     * @return The price, rounded to 50 gold.
     */
    public static int price(double synergy) {
        final int p = (int)Math.round(synergy * GOLD_PER_POINT / 50.0) * 50;
        return Math.max(MIN_PRICE, p);
    }

    /**
     * Does a computer member take the gold?  The gold, as its
     * character values it, must outweigh the synergy.  At the asking
     * price a nation that values gold at 2/3 or more takes it; one
     * that values security or conquest over gold holds out.
     *
     * @param goldWeight Its character's weight for gold.
     * @param gold The gold offered.
     * @param synergy Its synergy.
     * @return True if it leaves.
     */
    public static boolean takes(double goldWeight, int gold, double synergy) {
        return goldWeight * gold / 100.0 >= synergy;
    }

    // Joint attacks: the members strike the same colony together.

    /** Fewest and most turns to muster for a joint strike. */
    public static final int MIN_MUSTER = 2, MAX_MUSTER = 10;
    /** Turns after the strike turn a plan still holds. */
    public static final int STRIKE_WINDOW = 5;

    /**
     * The turn to strike together: when the slowest member can be there.
     *
     * @param now The turn.
     * @param travel Each member's turns to get there.
     * @return The strike turn.
     */
    public static int strikeTurn(int now, int[] travel) {
        int most = 0;
        for (int t : travel) most = Math.max(most, t);
        return now + Math.max(MIN_MUSTER, Math.min(MAX_MUSTER, most));
    }

    /**
     * When a member sets off, to land on the strike turn.
     *
     * @param travel Its turns to get there.
     * @return The turn to launch.
     */
    public int launchTurn(int travel) {
        return strikeTurn - Math.max(0, travel);
    }

    /**
     * Is there a plan that still holds?
     *
     * @param now The turn.
     * @return True if so.
     */
    public boolean hasPlan(int now) {
        return jointTarget != null && strikeTurn >= 0
            && now <= strikeTurn + STRIKE_WINDOW;
    }

    /** Drop the plan. */
    public void clearPlan() {
        jointTarget = null;
        jointName = "";
        strikeTurn = -1;
    }

    public String serialize() {
        return target + "|" + since + "|" + calmSince + "|"
            + String.join(",", members)
            + ((jointTarget == null) ? "" : "|" + jointTarget + "~"
               + strikeTurn + "~" + jointName.replaceAll("[|;~,]", " "));
    }

    public static Coalition parse(String s) {
        final String[] f = s.split("\\|", -1);
        if (f.length != 4 && f.length != 5) return null;
        try {
            Coalition c = new Coalition(f[0], Integer.parseInt(f[1]));
            c.calmSince = Integer.parseInt(f[2]);
            if (!f[3].isEmpty()) {
                for (String m : f[3].split(",")) c.members.add(m);
            }
            if (f.length == 5) {
                final String[] p = f[4].split("~", -1);
                if (p.length == 3) {
                    c.jointTarget = p[0];
                    c.strikeTurn = Integer.parseInt(p[1]);
                    c.jointName = p[2];
                }
            }
            return c;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
