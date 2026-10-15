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

import junit.framework.TestCase;
import net.sf.freecol.common.model.Stance;


/**
 * Tribes: characters, opinions of each other, rare wars, memories.
 */
public class TribeRelationsTest extends TestCase {

    private static final String TUPI = "model.nation.tupi",
        ARAWAK = "model.nation.arawak", SIOUX = "model.nation.sioux",
        DUTCH = "model.nation.dutch";

    public void testCharacters() {
        assertEquals(TribeCharacter.WARLIKE, TribeCharacter.of(SIOUX));
        assertEquals(TribeCharacter.WARLIKE, TribeCharacter.of("model.nation.apache"));
        assertEquals(TribeCharacter.WARLIKE, TribeCharacter.of("model.nation.aztec"));
        assertEquals(TribeCharacter.WARLIKE, TribeCharacter.of("model.nation.inca"));
        assertEquals(TribeCharacter.PEACEFUL, TribeCharacter.of(ARAWAK));
        assertEquals(TribeCharacter.TRADERS, TribeCharacter.of(TUPI));
        assertEquals(TribeCharacter.MIDDLING, TribeCharacter.of("model.nation.iroquois"));
        assertEquals(TribeCharacter.MIDDLING, TribeCharacter.of("model.nation.cherokee"));
        // Warlike tribes go to war sooner.
        assertTrue(TribeCharacter.WARLIKE.warLine() > TribeCharacter.PEACEFUL.warLine());
    }

    public void testOpinions() {
        List<Memory> none = new ArrayList<>();
        assertEquals(0.0, TribeRelations.opinion(0, none, ARAWAK, 10,
                                                 false, 0, 0), 1e-9);
        final double rival = TribeRelations.opinion(0, none, ARAWAK, 10,
                                                    true, 0, 0);
        final double neighbours = TribeRelations.opinion(0, none, ARAWAK, 10,
                                                         true, 3, 0);
        final double together = TribeRelations.opinion(0, none, ARAWAK, 10,
                                                       true, 3, 2);
        assertEquals(-TribeRelations.RIVAL, rival, 1e-9);
        assertTrue(neighbours < rival);
        assertTrue("a shared enemy brings them together", together > neighbours);
        // Tension only makes it worse.
        assertTrue(TribeRelations.opinion(550, none, ARAWAK, 10, false, 0, 0) < -40);
    }

    public void testWarsAreRare() {
        final TribeCharacter w = TribeCharacter.WARLIKE;
        assertFalse("not the rival", TribeRelations.wantsWar(w, -100, false, true));
        assertFalse("no grudge", TribeRelations.wantsWar(w, -100, true, false));
        assertFalse("not angry enough", TribeRelations.wantsWar(w, -20, true, true));
        assertTrue(TribeRelations.wantsWar(w, -60, true, true));
        assertFalse("peaceful ones need more",
            TribeRelations.wantsWar(TribeCharacter.PEACEFUL, -60, true, true));
        assertTrue(TribeRelations.grudge(700, null, ARAWAK, 10));
        List<Memory> m = new ArrayList<>();
        m.add(new Memory(Memory.Kind.UNIT_LOST, ARAWAK, 9, 1));
        assertTrue(TribeRelations.grudge(0, m, ARAWAK, 10));
        assertFalse(TribeRelations.grudge(0, m, SIOUX, 10));
        // Peace after a while.
        assertFalse(TribeRelations.wantsPeace(w, -50, 5));
        assertTrue(TribeRelations.wantsPeace(w, -50, 30));
        assertEquals(4, TribeRelations.rub(TribeCharacter.WARLIKE, 3));
        assertEquals(0, TribeRelations.rub(TribeCharacter.WARLIKE, 0));
    }

    public void testTribeMemories() {
        Snapshot before = new Snapshot(10, "1600");
        before.stances.put(Snapshot.key(TUPI, DUTCH), Stance.PEACE);
        List<Observed> d = new ArrayList<>();
        d.add(new Observed(Observed.What.DESTROY, DUTCH, TUPI, false, 0, "Camp"));
        d.add(new Observed(Observed.What.MISSION, "model.nation.french", TUPI,
                           false, 0, "Camp"));
        d.add(new Observed(Observed.What.ARM_TRIBE, "model.nation.english",
                           ARAWAK, false, 0, "muskets"));
        d.add(new Observed(Observed.What.ARM_TRIBE, "model.nation.spanish",
                           TUPI, false, 0, "horses"));
        Memories mem = new Memories();
        TribeRelations.remember(mem, TUPI, ARAWAK, d, before, 10);
        assertTrue(has(mem, Memory.Kind.SETTLEMENT_DESTROYED, DUTCH));
        assertTrue(has(mem, Memory.Kind.MISSION, "model.nation.french"));
        assertTrue(has(mem, Memory.Kind.ARMED_RIVAL, "model.nation.english"));
        assertTrue(has(mem, Memory.Kind.TRADE, "model.nation.spanish"));
        // Saved and read back.
        assertEquals(mem.serialize(';'), Memories.parse(mem.serialize(';')).serialize(';'));
    }

    private static boolean has(Memories mem, Memory.Kind k, String about) {
        for (Memory m : mem.list()) {
            if (m.kind == k && about.equals(m.about)) return true;
        }
        return false;
    }
}
