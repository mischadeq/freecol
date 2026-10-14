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

import java.util.Arrays;
import java.util.Random;


/**
 * The chance an attack force takes a colony or settlement, by playing
 * the fights out many times.  Each fight is won by the attacker with
 * probability attack / (attack + defence) (the game's rule).  The
 * strongest attacker left fights the strongest defender left.  An
 * attacker that loses is out (it loses its muskets or dies); a defender
 * that loses is out too.  A colony falls when its armed defenders are
 * beaten and one more fight is won against an unarmed colonist; a
 * native settlement falls when its last brave is beaten.
 */
public final class CaptureSim {

    /** How many times each attack is played out. */
    public static final int RUNS = 400;

    /** The chance of taking it, and the attackers lost on average. */
    public static final class Result {
        public final double chance;
        public final double lost;

        Result(double chance, double lost) {
            this.chance = chance;
            this.lost = lost;
        }
    }

    private CaptureSim() {}


    /**
     * Play out an attack.
     *
     * @param attack Attack of each attacker (any order).
     * @param target The place attacked.
     * @param seed Seed, so the same question always gets the same answer.
     * @return The {@code Result}.
     */
    public static Result attack(double[] attack, PlaceView target,
                                long seed) {
        if (attack.length == 0) return new Result(0, 0);
        final double[] a = attack.clone();
        Arrays.sort(a); // weakest first; we use it from the end
        final double[] d = target.defence.clone();
        Arrays.sort(d);
        final Random random = new Random(seed);
        int taken = 0;
        long lost = 0;
        for (int run = 0; run < RUNS; run++) {
            int ai = a.length - 1, di = d.length - 1;
            boolean last = !target.nativeSettlement; // unarmed defender left
            boolean won = false;
            while (ai >= 0) {
                double def;
                if (di >= 0) def = d[di];
                else if (last) def = target.lastDefence;
                else { won = true; break; }
                if (random.nextDouble() < CombatMath.winChance(a[ai], def)) {
                    if (di >= 0) di--;
                    else { won = true; break; } // the unarmed one
                } else {
                    ai--;
                    lost++;
                }
            }
            if (won) taken++;
        }
        return new Result(taken / (double)RUNS, lost / (double)RUNS);
    }

    /**
     * A force of n equal attackers.
     *
     * @param n How many.
     * @param attack The attack of each.
     * @return The attack array.
     */
    public static double[] force(int n, double attack) {
        double[] ret = new double[Math.max(0, n)];
        Arrays.fill(ret, attack);
        return ret;
    }
}
