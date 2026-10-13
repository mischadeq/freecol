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

package net.sf.freecol.client.control;

import java.util.List;
import java.util.Map;

import junit.framework.TestCase;


/**
 * What trade routes moved, turn by turn.
 */
public class RouteHistoryTest extends TestCase {

    private static final String ORE = "model.goods.ore";
    private static final String TOOLS = "model.goods.tools";

    public void testTotalsPerTurn() {
        RouteHistory h = new RouteHistory();
        h.record("tr:1", 10, ORE, 100);
        h.record("tr:1", 10, TOOLS, 50); // Two goods in one turn
        h.record("tr:1", 12, ORE, 100);
        h.record("tr:1", 12, ORE, 40);   // Two unloads in one turn add up
        h.record("tr:2", 12, ORE, 999);  // Another route
        int[] t = h.totals("tr:1", 12, 4);
        assertEquals(4, t.length);
        assertEquals(0, t[0]);   // turn 9
        assertEquals(150, t[1]); // turn 10
        assertEquals(0, t[2]);   // turn 11
        assertEquals(140, t[3]); // turn 12
        assertEquals(0, h.totals("tr:none", 12, 4)[3]);
        assertEquals(Integer.valueOf(50), h.byGoods("tr:1", 10).get(TOOLS));
        assertTrue(h.byGoods("tr:1", 11).isEmpty());
    }

    public void testSumByGoodsLargestFirst() {
        RouteHistory h = new RouteHistory();
        h.record("tr:1", 5, TOOLS, 30);
        h.record("tr:1", 6, ORE, 100);
        h.record("tr:1", 7, TOOLS, 30);
        h.record("tr:1", 1, ORE, 500); // Too old for the last 3 turns
        Map<String, Integer> sum = h.sumByGoods("tr:1", 7, 3);
        assertEquals(List.of(ORE, TOOLS), List.copyOf(sum.keySet()));
        assertEquals(Integer.valueOf(100), sum.get(ORE));
        assertEquals(Integer.valueOf(60), sum.get(TOOLS));
    }

    public void testNothingRecordedForNoAmount() {
        RouteHistory h = new RouteHistory();
        h.record("tr:1", 5, ORE, 0);
        h.record("tr:1", 5, ORE, -3);
        h.record(null, 5, ORE, 10);
        assertEquals("", h.serialize());
    }

    public void testLoadingForgetsTurnsPlayedAgain() {
        RouteHistory h = new RouteHistory();
        h.record("tr:1", 8, ORE, 10);
        h.record("tr:1", 9, ORE, 20);
        h.record("tr:1", 10, ORE, 30);
        // Go Back One Turn: turn 9 will be played again.
        assertTrue(h.loaded(9));
        int[] t = h.totals("tr:1", 10, 3);
        assertEquals(10, t[0]);
        assertEquals(0, t[1]);
        assertEquals(0, t[2]);
        assertFalse(h.loaded(9)); // Nothing more to forget
    }

    public void testOldTurnsDropped() {
        RouteHistory h = new RouteHistory();
        h.record("tr:1", 1, ORE, 10);
        h.record("tr:1", 1 + RouteHistory.KEEP_TURNS + 1, ORE, 10);
        assertTrue(h.byGoods("tr:1", 1).isEmpty());
    }

    public void testSaveAndRead() {
        RouteHistory h = new RouteHistory();
        h.record("tr:1", 10, ORE, 100);
        h.record("tr:1", 10, TOOLS, 50);
        h.record("tr:2", 11, ORE, 7);
        RouteHistory back = new RouteHistory();
        back.parse(h.serialize() + "garbage line\n,,,\n");
        assertEquals(h.serialize(), back.serialize());
        assertEquals(150, back.totals("tr:1", 10, 1)[0]);
    }
}
