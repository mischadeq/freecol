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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;
import net.sf.freecol.common.diplomacy.Action;
import net.sf.freecol.common.model.Stance;


public class ReplayTest extends TestCase {

    private static final String DUTCH = "model.nation.dutch",
        FRENCH = "model.nation.french", ENGLISH = "model.nation.english",
        IROQUOIS = "model.nation.iroquois";

    public void testTurnFromName() {
        assertEquals(2, SaveSeries.turnFromName("Autosave-x_Dutch_1493.fsg"));
        assertEquals(108, SaveSeries.turnFromName("Autosave-x_Dutch_1599.fsg"));
        assertEquals(109, SaveSeries.turnFromName("A_Dutch_1600_1_Spring.fsg"));
        assertEquals(110, SaveSeries.turnFromName("A_Dutch_1600_2_Autumn.fsg"));
        assertEquals(247, SaveSeries.turnFromName("Autosave-6c7a41f5_Dutch_1669_1_Spring.fsg"));
        assertEquals(-1, SaveSeries.turnFromName("Autosave-last-turn.fsg"));
    }

    public void testHatefulComputerDeclares() {
        Snapshot a = snap(10), b = snap(11);
        a.stances.put(Snapshot.key(FRENCH, ENGLISH), Stance.PEACE);
        a.stances.put(Snapshot.key(ENGLISH, FRENCH), Stance.PEACE);
        b.stances.put(Snapshot.key(FRENCH, ENGLISH), Stance.WAR);
        b.stances.put(Snapshot.key(ENGLISH, FRENCH), Stance.WAR);
        a.tensions.put(Snapshot.key(FRENCH, ENGLISH), 900);
        b.tensions.put(Snapshot.key(FRENCH, ENGLISH), 1100);
        a.tensions.put(Snapshot.key(ENGLISH, FRENCH), 100);
        Observed o = only(TurnDiff.diff(a, b, new HashSet<>()),
                          Observed.What.WAR);
        assertEquals(FRENCH, o.actor);
        assertEquals(ENGLISH, o.other);
        assertFalse(o.forced);
        assertTrue(Action.war(ENGLISH).matches(o.asAction()));
    }

    public void testWarNobodyHatedIsTheKings() {
        Snapshot a = snap(10), b = snap(11);
        a.stances.put(Snapshot.key(DUTCH, FRENCH), Stance.PEACE);
        b.stances.put(Snapshot.key(DUTCH, FRENCH), Stance.WAR);
        a.tensions.put(Snapshot.key(FRENCH, DUTCH), 300);
        Observed o = only(TurnDiff.diff(a, b, new HashSet<>()),
                          Observed.What.WAR);
        assertEquals(DUTCH, o.actor); // the human, forced
        assertTrue(o.forced);
        assertNull(o.asAction());
    }

    public void testHumanSaysHeDeclared() {
        Snapshot a = snap(10), b = snap(11);
        a.stances.put(Snapshot.key(DUTCH, IROQUOIS), Stance.PEACE);
        b.stances.put(Snapshot.key(DUTCH, IROQUOIS), Stance.WAR);
        // The attack made the tribe hateful; the human still started it.
        b.tensions.put(Snapshot.key(IROQUOIS, DUTCH), 1085);
        Set<String> declared = new HashSet<>();
        declared.add(IROQUOIS);
        Observed o = only(TurnDiff.diff(a, b, declared), Observed.What.WAR);
        assertEquals(DUTCH, o.actor);
        assertFalse(o.forced);
    }

    public void testPeaceByTheOneWhoCalmedDown() {
        Snapshot a = snap(10), b = snap(11);
        a.stances.put(Snapshot.key(FRENCH, ENGLISH), Stance.WAR);
        b.stances.put(Snapshot.key(FRENCH, ENGLISH), Stance.CEASE_FIRE);
        a.tensions.put(Snapshot.key(FRENCH, ENGLISH), 1000);
        b.tensions.put(Snapshot.key(FRENCH, ENGLISH), 900);
        a.tensions.put(Snapshot.key(ENGLISH, FRENCH), 500);
        Observed o = only(TurnDiff.diff(a, b, new HashSet<>()),
                          Observed.What.PEACE);
        assertEquals(ENGLISH, o.actor);
    }

    public void testCaptureLossesAndArming() {
        Snapshot a = snap(10), b = snap(11);
        a.places.put("colony:1", new Snapshot.Place("colony:1", "Port", FRENCH,
                                                   4, false, null));
        b.places.put("colony:1", new Snapshot.Place("colony:1", "Port", DUTCH,
                                                   3, false, null));
        a.stances.put(Snapshot.key(DUTCH, FRENCH), Stance.WAR);
        b.stances.put(Snapshot.key(DUTCH, FRENCH), Stance.WAR);
        a.stances.put(Snapshot.key(FRENCH, DUTCH), Stance.WAR);
        b.stances.put(Snapshot.key(FRENCH, DUTCH), Stance.WAR);
        a.troops.put("unit:1", new Snapshot.Troop("unit:1", FRENCH, false, false));
        a.troops.put("unit:2", new Snapshot.Troop("unit:2", FRENCH, true, false));
        b.troops.put("unit:2", new Snapshot.Troop("unit:2", FRENCH, true, true));
        a.nations.get(DUTCH).army = 4;
        b.nations.get(DUTCH).army = 7.5;
        List<Observed> d = TurnDiff.diff(a, b, new HashSet<>());
        Observed cap = only(d, Observed.What.CAPTURE);
        assertEquals(DUTCH, cap.actor);
        assertEquals(FRENCH, cap.other);
        assertEquals(Action.Kind.ATTACK_COLONY, cap.asAction().kind);
        Observed lost = only(d, Observed.What.UNITS_LOST);
        assertEquals(FRENCH, lost.actor);
        assertEquals(DUTCH, lost.other);
        assertEquals(1, lost.amount);
        assertEquals(1, only(d, Observed.What.SHIPS_DAMAGED).amount);
        Observed arm = only(d, Observed.What.ARM);
        assertEquals(DUTCH, arm.actor);
        assertEquals(3, arm.amount);
    }

    public void testMissions() {
        Snapshot a = snap(10), b = snap(11);
        a.places.put("is:1", new Snapshot.Place("is:1", "Onondaga", IROQUOIS,
                                               6, true, null));
        b.places.put("is:1", new Snapshot.Place("is:1", "Onondaga", IROQUOIS,
                                               6, true, DUTCH));
        Observed m = only(TurnDiff.diff(a, b, new HashSet<>()),
                          Observed.What.MISSION);
        assertEquals(DUTCH, m.actor);
        assertEquals(IROQUOIS, m.other);
    }

    public void testArmsSoldToATribe() {
        Snapshot a = snap(10), b = snap(11);
        a.places.put("is:1", new Snapshot.Place("is:1", "Urutu", IROQUOIS,
                                               5, true, null));
        b.places.put("is:1", new Snapshot.Place("is:1", "Urutu", IROQUOIS,
                                               5, true, null));
        a.armsSales.put(DUTCH + "|is:1|model.goods.horses", 5);
        b.armsSales.put(DUTCH + "|is:1|model.goods.horses", 5); // old sale
        b.armsSales.put(DUTCH + "|is:1|model.goods.muskets", 10);
        Observed o = only(TurnDiff.diff(a, b, new HashSet<>()),
                          Observed.What.ARM_TRIBE);
        assertEquals(DUTCH, o.actor);
        assertEquals(IROQUOIS, o.other);
        assertEquals(Action.Kind.ARM_TRIBE, o.asAction().kind);
    }

    private static Snapshot snap(int turn) {
        Snapshot s = new Snapshot(turn, "t" + turn);
        s.nations.put(DUTCH, new Snapshot.Nation(DUTCH, true, false));
        s.nations.put(FRENCH, new Snapshot.Nation(FRENCH, true, true));
        s.nations.put(ENGLISH, new Snapshot.Nation(ENGLISH, true, true));
        s.nations.put(IROQUOIS, new Snapshot.Nation(IROQUOIS, false, true));
        return s;
    }

    private static Observed only(List<Observed> d, Observed.What what) {
        Observed found = null;
        for (Observed o : d) {
            if (o.what != what) continue;
            assertNull("only one " + what, found);
            found = o;
        }
        assertNotNull("no " + what + " in " + d, found);
        return found;
    }
}
