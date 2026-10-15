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
 * Grievances: demands, stages, saving.
 */
public class GrievanceTest extends TestCase {

    public void testDemand() {
        assertEquals(Grievance.MIN_DEMAND, Grievance.demandFor(0));
        assertEquals(500, Grievance.demandFor(2000));
        assertEquals(250, Grievance.demandFor(1000));
        assertEquals(Grievance.MAX_DEMAND, Grievance.demandFor(100000));
    }

    public void testStages() {
        Grievance g = new Grievance("model.nation.dutch",
                                    Grievance.Cause.SOLDIERS, 10);
        assertEquals(Grievance.Stage.COMPLAINT, g.stage);
        assertEquals(Grievance.COMPLAINT_TURNS, g.turnsLeft(10));
        assertEquals(0, g.turnsLeft(10 + Grievance.COMPLAINT_TURNS));
        assertFalse(g.stale(11));
        assertTrue("forgotten when nothing comes of it",
                   g.stale(10 + Grievance.STALE_TURNS));
        g.stage = Grievance.Stage.SETTLED;
        g.since = 20;
        assertTrue(g.settled(21));
        assertFalse(g.settled(20 + Grievance.SETTLED_TURNS));
        assertTrue(g.stale(20 + Grievance.SETTLED_TURNS));
    }

    public void testSerialize() {
        Grievance g = new Grievance("model.nation.dutch",
                                    Grievance.Cause.CROWDING, 10);
        g.stage = Grievance.Stage.ULTIMATUM;
        g.since = 13;
        g.demand = 450;
        Grievance h = Grievance.parse(g.serialize());
        assertEquals(g.serialize(), h.serialize());
        assertEquals(Grievance.Cause.CROWDING, h.cause);
        assertNull(Grievance.parse("junk"));
    }
}
