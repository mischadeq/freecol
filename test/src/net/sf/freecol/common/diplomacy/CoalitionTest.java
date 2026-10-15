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
import net.sf.freecol.common.model.Stance;


/**
 * Coalitions: the line by difficulty, when they end, saving.
 */
public class CoalitionTest extends TestCase {

    private static final String DUTCH = "model.nation.dutch",
        FRENCH = "model.nation.french", ENGLISH = "model.nation.english",
        SPANISH = "model.nation.spanish";

    public void testLineByDifficulty() {
        assertEquals(0.40, Coalition.line("model.difficulty.veryEasy"), 1e-9);
        assertEquals(0.35, Coalition.line("model.difficulty.medium"), 1e-9);
        assertEquals(0.30, Coalition.line("model.difficulty.veryHard"), 1e-9);
        assertTrue(Coalition.line("model.difficulty.hard")
                   < Coalition.line("model.difficulty.easy"));
    }

    public void testLineDecidesTheLeader() {
        NationView me = new NationView(DUTCH, true, true);
        me.army = 10;
        me.output = 100;
        WorldView w = new WorldView(100, me, NationCharacter.BALANCE_KEEPER);
        for (String id : new String[] { FRENCH, SPANISH }) {
            NationView v = new NationView(id, true, false);
            v.army = 10;
            v.output = 100;
            v.stance = Stance.PEACE;
            w.others.put(id, v);
        }
        NationView en = new NationView(ENGLISH, true, false);
        en.army = 18; // threat about 0.34
        en.output = 130;
        en.stance = Stance.PEACE;
        w.others.put(ENGLISH, en);
        w.coalitionLine = 0.35;
        assertNull(Threat.leaderToContain(w));
        w.coalitionLine = 0.30; // very hard: a coalition sooner
        assertEquals(ENGLISH, Threat.leaderToContain(w));
    }

    public void testEndsWhenCalm() {
        Coalition c = new Coalition(ENGLISH, 100);
        c.members.add(FRENCH);
        assertFalse("alone, waiting for answers", c.over(0.5, 102));
        assertTrue("nobody came", c.over(0.5, 100 + Coalition.INVITE_TURNS));
        c.members.add(DUTCH);
        assertFalse(c.over(0.5, 110));
        assertFalse(c.over(0.2, 111)); // calm starts
        assertFalse(c.over(0.3, 115)); // not calm: start again
        assertFalse(c.over(0.2, 116));
        assertTrue(c.over(0.2, 116 + Coalition.CALM_TURNS));
    }

    public void testSerializeAndParse() {
        Coalition c = new Coalition(ENGLISH, 100);
        c.members.add(FRENCH);
        c.members.add(DUTCH);
        c.calmSince = 104;
        Coalition d = Coalition.parse(c.serialize());
        assertEquals(c.serialize(), d.serialize());
        assertEquals(2, d.members.size());
        assertNull(Coalition.parse("junk"));
    }

    /** The tighter the bond, the higher the price to leave. */
    public void testSynergyPrice() {
        final double cold = Coalition.synergy(-50, 30, 0.3, 1, 0);
        final double warm = Coalition.synergy(60, 80, 0.3, 1, 0);
        final double old = Coalition.synergy(60, 80, 0.3, 30, 0);
        final double comrades = Coalition.synergy(60, 80, 0.3, 30, 2);
        assertTrue(cold < warm);
        assertTrue(warm < old);
        assertTrue(old < comrades);
        assertEquals("never below the least", Coalition.MIN_SYNERGY,
                     Coalition.synergy(-100, 0, 0, 0, 0), 1e-9);
        // A neutral member, threat 0.35, ten turns: 5 + 7 + 2.5 = 14.5.
        assertEquals(14.5, Coalition.synergy(0, 50, 0.35, 10, 0), 1e-9);
        assertEquals(2200, Coalition.price(14.5)); // 2175, to 50s
        assertTrue(Coalition.price(cold) < Coalition.price(comrades));
        assertEquals(Coalition.MIN_PRICE, Coalition.price(0));
        // At the asking price: traders take it, balance keepers do not.
        final int p = Coalition.price(14.5);
        assertTrue(Coalition.takes(NationCharacter.TRADING_EMPIRE.gold, p, 14.5));
        assertTrue(Coalition.takes(NationCharacter.SETTLER.gold, p, 14.5));
        assertFalse(Coalition.takes(NationCharacter.BALANCE_KEEPER.gold, p, 14.5));
        assertFalse(Coalition.takes(NationCharacter.TRADING_EMPIRE.gold, p / 2, 14.5));
    }

    public void testJointPlan() {
        assertEquals(100 + Coalition.MIN_MUSTER, Coalition.strikeTurn(100, new int[] { 0, 1 }));
        assertEquals(106, Coalition.strikeTurn(100, new int[] { 6, 3 }));
        assertEquals(100 + Coalition.MAX_MUSTER, Coalition.strikeTurn(100, new int[] { 40 }));
        Coalition c = new Coalition(ENGLISH, 90);
        c.members.add(FRENCH);
        c.members.add(DUTCH);
        assertFalse(c.hasPlan(100));
        c.jointTarget = "colony:7";
        c.jointName = "Jamestown|;~,";
        c.strikeTurn = 106;
        assertEquals(102, c.launchTurn(4));
        assertTrue(c.hasPlan(100));
        assertTrue(c.hasPlan(106 + Coalition.STRIKE_WINDOW));
        assertFalse(c.hasPlan(107 + Coalition.STRIKE_WINDOW));
        Coalition d = Coalition.parse(c.serialize());
        assertEquals("colony:7", d.jointTarget);
        assertEquals(106, d.strikeTurn);
        assertEquals(2, d.members.size());
        // An older save without a plan still reads.
        assertNull(Coalition.parse("model.nation.english|90|-1|a,b").jointTarget);
        c.clearPlan();
        assertFalse(c.hasPlan(100));
    }
}
