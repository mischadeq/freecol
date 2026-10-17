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

import junit.framework.TestCase;


/**
 * How a tribe judges the player's envoy: survival first (step 8.3b).
 */
public class TribeEnvoyRulesTest extends TestCase {

    private static TribeEnvoyRules.Input peace(int braves, int soldiers) {
        TribeEnvoyRules.Input in = new TribeEnvoyRules.Input();
        in.kind = Offer.Kind.ENVOY_PEACE;
        in.braves = braves;
        in.soldiers = soldiers;
        in.tension = 900;
        in.opinion = -60;
        return in;
    }

    public void testStanding() {
        assertEquals(TribeEnvoyRules.Standing.LOSING,
                     TribeEnvoyRules.standing(2, 6, false));
        assertEquals(TribeEnvoyRules.Standing.EVEN,
                     TribeEnvoyRules.standing(5, 5, false));
        assertEquals(TribeEnvoyRules.Standing.STRONG,
                     TribeEnvoyRules.standing(9, 3, false));
        assertEquals("a village lost lately", TribeEnvoyRules.Standing.LOSING,
                     TribeEnvoyRules.standing(9, 3, true));
    }

    public void testLosingTribeMakesPeaceHumbly() {
        TribeEnvoyRules.Answer a = TribeEnvoyRules.judge(peace(2, 8));
        assertEquals(EnvoyRules.Verdict.ACCEPT, a.verdict);
        assertEquals(TribeEnvoyRules.Standing.LOSING, a.standing);
    }

    public void testEvenTribeWantsGold() {
        TribeEnvoyRules.Input in = peace(5, 5);
        TribeEnvoyRules.Answer a = TribeEnvoyRules.judge(in);
        assertEquals(EnvoyRules.Verdict.COUNTER, a.verdict);
        assertEquals(300, a.price); // (900 - 300) / 2
        assertEquals("no conditions when even", 0, a.conditions);
        in.gold = 300;
        assertEquals(EnvoyRules.Verdict.ACCEPT, TribeEnvoyRules.judge(in).verdict);
    }

    public void testStrongTribeSetsConditions() {
        TribeEnvoyRules.Input in = peace(9, 3);
        in.takenLand = true;
        in.colonyOnTakenLand = true;
        in.troopsInside = true;
        in.gold = 5000;
        TribeEnvoyRules.Answer a = TribeEnvoyRules.judge(in);
        assertEquals(EnvoyRules.Verdict.COUNTER, a.verdict);
        assertEquals(600, a.price); // twice as much
        assertEquals(TribeEnvoyRules.LAND | TribeEnvoyRules.COLONY
                     | TribeEnvoyRules.TROOPS, a.conditions);
        // Nothing to ask back: gold is enough.
        TribeEnvoyRules.Input clean = peace(9, 3);
        clean.gold = 600;
        assertEquals(EnvoyRules.Verdict.ACCEPT,
                     TribeEnvoyRules.judge(clean).verdict);
        // A strong tribe that likes us asks no conditions.
        in.opinion = 10;
        assertEquals(0, TribeEnvoyRules.judge(in).conditions);
    }

    public void testRevenge() {
        TribeEnvoyRules.Input in = peace(9, 3);
        in.opinion = -95;
        assertEquals(TribeEnvoyRules.Reason.REVENGE,
                     TribeEnvoyRules.judge(in).reason);
        // But a losing tribe still takes peace.
        TribeEnvoyRules.Input lose = peace(1, 9);
        lose.opinion = -100;
        assertEquals(EnvoyRules.Verdict.ACCEPT,
                     TribeEnvoyRules.judge(lose).verdict);
    }

    public void testGiftAndFit() {
        TribeEnvoyRules.Input g = peace(9, 1);
        g.kind = Offer.Kind.ENVOY_GIFT;
        g.opinion = -100;
        assertEquals(EnvoyRules.Verdict.ACCEPT, TribeEnvoyRules.judge(g).verdict);
        TribeEnvoyRules.Input f = peace(1, 9);
        f.fits = false;
        assertEquals(TribeEnvoyRules.Reason.NOT_NOW,
                     TribeEnvoyRules.judge(f).reason);
    }

    /** War against a nation: dear (the old incite price); against a
        tribe cheaper, cheapest against the old rival (8.2). */
    public void testWarPrice() {
        assertEquals(5000, TribeEnvoyRules.warPrice(true, false, 0, 0));
        assertTrue(TribeEnvoyRules.warPrice(true, false, 400, 100)
                   >= 10000);
        assertTrue(TribeEnvoyRules.warPrice(false, true, 0, 0)
                   < TribeEnvoyRules.warPrice(false, false, 0, 0));
        assertEquals(300, TribeEnvoyRules.warPrice(false, true, 0, 1000));
        assertEquals(0, TribeEnvoyRules.warPrice(false, false, 130, 0) % 50);
    }

    /** An ally fights for free; a friend for gold and an alliance; no
        if it dislikes the player (8.2). */
    public void testJudgeWar() {
        TribeEnvoyRules.Answer a = TribeEnvoyRules.judgeWar(-10, true,
                                                            false, 5000);
        assertEquals(EnvoyRules.Verdict.ACCEPT, a.verdict);
        a = TribeEnvoyRules.judgeWar(-10, false, false, 5000);
        assertEquals(EnvoyRules.Verdict.REFUSE, a.verdict);
        assertEquals(TribeEnvoyRules.Reason.TRUST, a.reason);
        a = TribeEnvoyRules.judgeWar(20, false, false, 5000);
        assertEquals(EnvoyRules.Verdict.COUNTER, a.verdict);
        assertEquals(5000, a.price);
        assertEquals(TribeEnvoyRules.ALLY, a.conditions);
        a = TribeEnvoyRules.judgeWar(20, false, true, 5000);
        assertEquals("fighting it already", EnvoyRules.Verdict.ACCEPT,
                     a.verdict);
    }
}
