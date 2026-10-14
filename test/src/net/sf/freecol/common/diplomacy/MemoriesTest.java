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

import java.util.HashSet;
import java.util.List;

import junit.framework.TestCase;
import net.sf.freecol.common.model.Stance;


/**
 * Live memories: fading, saving, trust, and what a turn's changes
 * leave behind.
 */
public class MemoriesTest extends TestCase {

    private static final String DUTCH = "model.nation.dutch",
        FRENCH = "model.nation.french", ENGLISH = "model.nation.english",
        SPANISH = "model.nation.spanish";


    public void testFadeForgetsOldMemories() {
        Memories m = new Memories();
        m.add(new Memory(Memory.Kind.UNIT_LOST, ENGLISH, 100, 2));
        m.add(new Memory(Memory.Kind.DEAL_BROKEN, ENGLISH, 100, 1));
        assertEquals(-10.0, m.list().get(0).value(100), 1e-9);
        assertEquals(-5.0, m.list().get(0).value(105), 1e-9);
        m.fade(109);
        assertEquals(2, m.size());
        m.fade(110); // units lost last 10 turns
        assertEquals(1, m.size());
        // A broken deal: the feeling goes after 60 turns, the distrust
        // lasts twice as long.
        Memory broken = m.list().get(0);
        assertEquals(0.0, broken.value(160), 1e-9);
        assertTrue(broken.trust(160) < 0);
        m.fade(160);
        assertEquals(1, m.size());
        m.fade(220);
        assertEquals(0, m.size());
    }

    public void testPeaceThatHoldsIsAKeptDeal() {
        Memories m = new Memories();
        m.add(new Memory(Memory.Kind.TREATY, ENGLISH, 100, 1));
        assertEquals(0.0, m.list().get(0).value(100), 1e-9);
        m.fade(100 + Memories.PROBATION - 1);
        assertEquals(Memory.Kind.TREATY, m.list().get(0).kind);
        m.fade(100 + Memories.PROBATION);
        assertEquals(1, m.size());
        assertEquals(Memory.Kind.DEAL_KEPT, m.list().get(0).kind);
        assertEquals(120, m.list().get(0).turn);
    }

    public void testSerializeAndParse() {
        Memories m = new Memories();
        m.add(new Memory(Memory.Kind.SHIP_DAMAGED, ENGLISH, 100, 3));
        m.add(new Memory(Memory.Kind.GIFT, DUTCH, 104, 1));
        m.add(new Memory(Memory.Kind.TREATY, SPANISH, 90, 1));
        String s = m.serialize(';');
        assertEquals(m.list(), Memories.parse(s).list());
        // Bad records are skipped, good ones kept.
        Memories p = Memories.parse("NOSUCHKIND:" + DUTCH + ":1:1;"
            + "GIFT:" + DUTCH + ":x:1;" + "TRADE:" + DUTCH + ":5:1");
        assertEquals(1, p.size());
        assertEquals(Memory.Kind.TRADE, p.list().get(0).kind);
        assertEquals(0, Memories.parse(null).size());
        assertEquals(0, Memories.parse("").size());
    }

    public void testTrustGoesUpAndDown() {
        Memories m = new Memories();
        assertEquals(Relations.DEFAULT_TRUST,
                     Relations.trust(m.list(), ENGLISH, 100), 1e-9);
        m.add(new Memory(Memory.Kind.DEAL_KEPT, ENGLISH, 100, 1));
        m.add(new Memory(Memory.Kind.TRADE, ENGLISH, 100, 1));
        double up = Relations.trust(m.list(), ENGLISH, 100);
        assertTrue("kept deals raise trust: " + up,
                   up > Relations.DEFAULT_TRUST);
        m.add(new Memory(Memory.Kind.DEAL_BROKEN, ENGLISH, 101, 1));
        double down = Relations.trust(m.list(), ENGLISH, 101);
        assertTrue("one broken deal outweighs a kept one: " + down,
                   down < Relations.DEFAULT_TRUST);
        // Others are not affected, and trust stays in 0..100.
        assertEquals(Relations.DEFAULT_TRUST,
                     Relations.trust(m.list(), DUTCH, 101), 1e-9);
        m.add(new Memory(Memory.Kind.DEAL_BROKEN, ENGLISH, 101, 5));
        assertEquals(0.0, Relations.trust(m.list(), ENGLISH, 101), 1e-9);
        // It comes back slowly.
        assertTrue(Relations.trust(m.list(), ENGLISH, 200)
                   > Relations.trust(m.list(), ENGLISH, 150));
    }

    public void testLossesInAWarAreRemembered() {
        Snapshot a = snap(10), b = snap(11);
        war(a, FRENCH, ENGLISH);
        war(b, FRENCH, ENGLISH);
        a.troops.put("u1", new Snapshot.Troop("u1", FRENCH, false, false));
        a.troops.put("u2", new Snapshot.Troop("u2", FRENCH, false, false));
        a.troops.put("s1", new Snapshot.Troop("s1", FRENCH, true, false));
        b.troops.put("s1", new Snapshot.Troop("s1", FRENCH, true, true));
        Memories french = remember(FRENCH, a, b);
        assertEquals(2, french.size());
        assertHas(french, Memory.Kind.UNIT_LOST, ENGLISH, 2);
        assertHas(french, Memory.Kind.SHIP_DAMAGED, ENGLISH, 1);
        // The English lost nothing, and do not see the French losses.
        assertEquals(0, remember(ENGLISH, a, b).size());
    }

    public void testBrokenPeace() {
        // Peace between the French and the English...
        Snapshot a = snap(10), b = snap(11), c = snap(15);
        war(a, FRENCH, ENGLISH);
        peace(b, FRENCH, ENGLISH);
        Memories french = remember(FRENCH, a, b);
        assertHas(french, Memory.Kind.TREATY, ENGLISH, 1);
        // ...broken by the English a few turns later (they hated).
        war(c, FRENCH, ENGLISH);
        c.tensions.put(Snapshot.key(ENGLISH, FRENCH), 1100);
        french.remember(FRENCH, TurnDiff.diff(b, c, new HashSet<>()), b, b.turn);
        assertHas(french, Memory.Kind.DEAL_BROKEN, ENGLISH, 1);
        for (Memory m : french.list()) {
            assertFalse("treaty is over", m.kind == Memory.Kind.TREATY);
        }
    }

    public void testDiplomatsWarBreaksTheDeal() {
        // No hatred, but the English diplomat chose it: not the King.
        Snapshot a = snap(10), b = snap(11);
        peace(a, FRENCH, ENGLISH);
        war(b, FRENCH, ENGLISH);
        b.declared.add(Snapshot.key(ENGLISH, FRENCH));
        Memories french = new Memories();
        french.add(new Memory(Memory.Kind.TREATY, ENGLISH, 5, 1));
        french.remember(FRENCH, TurnDiff.diff(a, b, new HashSet<>()), a, a.turn);
        assertHas(french, Memory.Kind.DEAL_BROKEN, ENGLISH, 1);
    }

    public void testKingsWarBreaksNoDeal() {
        Snapshot a = snap(10), b = snap(11);
        peace(a, FRENCH, ENGLISH);
        war(b, FRENCH, ENGLISH); // nobody hateful: the King
        Memories french = new Memories();
        french.add(new Memory(Memory.Kind.TREATY, ENGLISH, 5, 1));
        french.remember(FRENCH, TurnDiff.diff(a, b, new HashSet<>()), a, a.turn);
        assertEquals(0, french.size());
    }

    public void testAColonyTakenFromOurEnemyIsHelp() {
        Snapshot a = snap(10), b = snap(11);
        war(a, FRENCH, SPANISH);
        war(b, FRENCH, SPANISH);
        war(a, ENGLISH, SPANISH);
        war(b, ENGLISH, SPANISH);
        peace(a, DUTCH, SPANISH);
        peace(b, DUTCH, SPANISH);
        a.places.put("c1", new Snapshot.Place("c1", "Havana", SPANISH, 3,
                                              false, null));
        b.places.put("c1", new Snapshot.Place("c1", "Havana", ENGLISH, 3,
                                              false, null));
        Memories french = remember(FRENCH, a, b);
        assertEquals(1, french.size());
        assertHas(french, Memory.Kind.HELPED_IN_WAR, ENGLISH, 1);
        // Not at war with Spain: no help; the taker itself: nothing.
        assertEquals(0, remember(DUTCH, a, b).size());
        assertEquals(0, remember(ENGLISH, a, b).size());
        // The loser has it from the game's history already.
        assertEquals(0, remember(SPANISH, a, b).size());
    }

    public void testStrongestFirst() {
        Memories m = new Memories();
        m.add(new Memory(Memory.Kind.SHIP_DAMAGED, ENGLISH, 100, 1));
        m.add(new Memory(Memory.Kind.DEAL_BROKEN, ENGLISH, 95, 1));
        m.add(new Memory(Memory.Kind.TREATY, ENGLISH, 100, 1));
        m.add(new Memory(Memory.Kind.GIFT, DUTCH, 100, 1));
        List<Memory> l = Memories.about(ENGLISH, 100, m.list());
        assertEquals(2, l.size()); // no treaty on probation, no Dutch
        assertEquals(Memory.Kind.DEAL_BROKEN, l.get(0).kind);
    }

    public void testMoodWords() {
        assertEquals("friendly", Mood.moodWord(50));
        assertEquals("neutral", Mood.moodWord(0));
        assertEquals("wary", Mood.moodWord(-20));
        assertEquals("hostile", Mood.moodWord(-60));
        assertEquals("trusts", Mood.trustWord(80));
        assertEquals("careful", Mood.trustWord(Relations.DEFAULT_TRUST));
        assertEquals("distrusts", Mood.trustWord(10));
    }

    public void testMoodFromOutlook() {
        NationView me = new NationView(FRENCH, true, true);
        WorldView w = new WorldView(100, me, NationCharacter.TRADING_EMPIRE);
        w.memories.add(new Memory(Memory.Kind.UNIT_LOST, ENGLISH, 99, 12));
        NationView en = new NationView(ENGLISH, true, false);
        en.stance = Stance.WAR;
        en.opinion = Relations.opinion(-1, w.memories, ENGLISH, 100);
        w.others.put(ENGLISH, en);
        Mood m = Mood.of(new Outlook(w));
        assertEquals(FRENCH, m.nation);
        assertEquals(NationCharacter.TRADING_EMPIRE, m.character);
        Mood.Feeling f = m.about(ENGLISH);
        assertEquals("hostile", f.mood());
        assertEquals(Stance.WAR, f.stance);
        assertEquals(1, f.memories.size());
    }


    private static Memories remember(String me, Snapshot a, Snapshot b) {
        Memories m = new Memories();
        m.remember(me, TurnDiff.diff(a, b, new HashSet<>()), a, a.turn);
        return m;
    }

    private static void assertHas(Memories m, Memory.Kind k, String about,
                                  int count) {
        for (Memory x : m.list()) {
            if (x.kind == k && x.about.equals(about) && x.count == count) return;
        }
        fail("no " + k + " " + about + " x" + count + " in " + m.list());
    }

    private static Snapshot snap(int turn) {
        Snapshot s = new Snapshot(turn, "t" + turn);
        for (String n : new String[] { DUTCH, FRENCH, ENGLISH, SPANISH }) {
            s.nations.put(n, new Snapshot.Nation(n, true, true));
        }
        return s;
    }

    private static void war(Snapshot s, String x, String y) {
        s.stances.put(Snapshot.key(x, y), Stance.WAR);
        s.stances.put(Snapshot.key(y, x), Stance.WAR);
    }

    private static void peace(Snapshot s, String x, String y) {
        s.stances.put(Snapshot.key(x, y), Stance.PEACE);
        s.stances.put(Snapshot.key(y, x), Stance.PEACE);
    }
}
