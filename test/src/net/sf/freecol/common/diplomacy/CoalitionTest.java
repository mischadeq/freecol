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
}
