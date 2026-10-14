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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;
import net.sf.freecol.common.model.Stance;


/**
 * War and peace choices hold: 10 turns, 30% better to change, unless
 * something shocking happens.
 */
public class CommitmentsTest extends TestCase {

    private static final String FRENCH = "model.nation.french",
        ENGLISH = "model.nation.english";


    public void testOnlyStrongWarPeaceAndAllianceMoves() {
        Commitments c = new Commitments();
        assertNull(c.decide(ENGLISH, Stance.PEACE,
                            ranked(scored(Action.war(ENGLISH), 15)), 100));
        assertNull("not for another nation", c.decide(ENGLISH, Stance.PEACE,
                   ranked(scored(Action.war(FRENCH), 50)), 100));
        assertNull("no peace while at peace", c.decide(ENGLISH, Stance.PEACE,
                   ranked(scored(Action.peace(ENGLISH), 50)), 100));
        assertNull("no alliance at war", c.decide(ENGLISH, Stance.WAR,
                   ranked(scored(Action.alliance(ENGLISH), 50)), 100));
        // The best fitting move wins.
        ScoredAction sa = c.decide(ENGLISH, Stance.PEACE, ranked(
            scored(Action.alliance(ENGLISH), 25),
            scored(Action.war(ENGLISH), 30)), 100);
        assertEquals(Action.Kind.DECLARE_WAR, sa.action.kind);
    }

    public void testAChoiceHolds() {
        Commitments c = new Commitments();
        c.chose(ENGLISH, scored(Action.war(ENGLISH), 30), 100);
        List<ScoredAction> peace40 = ranked(scored(Action.peace(ENGLISH), 40));
        List<ScoredAction> peace35 = ranked(scored(Action.peace(ENGLISH), 35));
        // Within 10 turns nothing changes, however good.
        assertNull(c.decide(ENGLISH, Stance.WAR,
            ranked(scored(Action.peace(ENGLISH), 90)), 105));
        assertNull(c.decide(ENGLISH, Stance.WAR, peace40, 109));
        // After that, a new choice must be 30% better (30 -> 39).
        assertNull(c.decide(ENGLISH, Stance.WAR, peace35, 110));
        assertNotNull(c.decide(ENGLISH, Stance.WAR, peace40, 110));
        // The same choice again is no change of mind.
        assertNotNull(c.decide(ENGLISH, Stance.PEACE,
            ranked(scored(Action.war(ENGLISH), 21)), 101));
    }

    public void testAShockLiftsTheHold() {
        Commitments c = new Commitments();
        c.chose(ENGLISH, scored(Action.peace(ENGLISH), 40), 100);
        List<ScoredAction> war = ranked(scored(Action.war(ENGLISH), 25));
        c.shock(100); // the same turn: not after the choice
        assertNull(c.decide(ENGLISH, Stance.PEACE, war, 102));
        c.shock(102);
        assertNotNull(c.decide(ENGLISH, Stance.PEACE, war, 102));
    }

    public void testWhatIsAShock() {
        Snapshot a = new Snapshot(10, "t10");
        List<Observed> d = new ArrayList<>();
        assertFalse(Commitments.isShock(FRENCH, d, a));
        d.add(new Observed(Observed.What.WAR, ENGLISH, FRENCH, true, 0, "the King"));
        assertFalse("the King's war is no betrayal",
                    Commitments.isShock(FRENCH, d, a));
        d.add(new Observed(Observed.What.WAR, ENGLISH, FRENCH, false, 0, ""));
        assertTrue(Commitments.isShock(FRENCH, d, a));
        assertFalse(Commitments.isShock(ENGLISH, d, a)); // it declared
        List<Observed> e = Arrays.asList(new Observed(Observed.What.CAPTURE,
            ENGLISH, FRENCH, false, 0, "Quebec"));
        assertTrue(Commitments.isShock(FRENCH, e, a));
        assertFalse(Commitments.isShock(ENGLISH, e, a));
    }

    public void testWhyInWords() {
        ScoredAction sa = scored(Action.war(ENGLISH), 30);
        sa.land = 20;
        sa.security = 5;
        assertEquals("wants their colonies", Commitments.why(sa));
        sa.goal = Goal.GOAL_BONUS;
        sa.note = "revenge english";
        assertEquals("revenge", Commitments.why(sa));
        ScoredAction p = scored(Action.peace(ENGLISH), 30);
        p.security = 10;
        p.gold = 3;
        assertEquals("war is too dangerous", Commitments.why(p));
        assertEquals("", Commitments.why(scored(Action.peace(ENGLISH), 0)));
    }

    public void testSerializeAndParse() {
        Commitments c = new Commitments();
        ScoredAction sa = scored(Action.war(ENGLISH), 31.25);
        sa.land = 10;
        c.chose(ENGLISH, sa, 100);
        c.chose(FRENCH, scored(Action.alliance(FRENCH), 22), 90);
        c.shock(95);
        Commitments d = Commitments.parse(c.serialize());
        assertEquals(2, d.size());
        assertEquals(95, d.lastShock());
        Commitments.Choice e = d.get(ENGLISH);
        assertEquals(Action.Kind.DECLARE_WAR, e.kind);
        assertEquals(100, e.turn);
        assertEquals(31.3, e.score, 1e-9);
        assertEquals("wants their colonies", e.why);
        assertEquals(Action.Kind.ALLIANCE, d.get(FRENCH).kind);
        // Local truces are kept too.
        c.truce(FRENCH, 12);
        Commitments e2 = Commitments.parse(c.serialize());
        assertTrue(e2.inTruce(FRENCH));
        assertFalse(e2.inTruce(ENGLISH));
        e2.endTruce(FRENCH);
        assertFalse(e2.inTruce(FRENCH));
        assertEquals(0, Commitments.parse(null).size());
        assertEquals(-1, Commitments.parse("bad;x:Y:1:2:3").lastShock());
    }


    private static ScoredAction scored(Action a, double total) {
        ScoredAction sa = new ScoredAction(a);
        sa.total = total;
        return sa;
    }

    private static List<ScoredAction> ranked(ScoredAction... sas) {
        List<ScoredAction> l = new ArrayList<>(Arrays.asList(sas));
        Collections.sort(l);
        return l;
    }
}
