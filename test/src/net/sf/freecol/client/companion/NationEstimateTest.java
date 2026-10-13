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

package net.sf.freecol.client.companion;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The honest estimates about other nations on the companion page.
 */
public class NationEstimateTest extends FreeColTestCase {

    public void testStrengthInSoldiersAndFrigates() {
        // A soldier attacks with 2 (muskets) +50%; a frigate with 16 +50%.
        assertEquals(3.0, NationEstimate.soldierPower(spec()), 0.001);
        assertEquals(24.0, NationEstimate.frigatePower(spec()), 0.001);
        // Mischa's army: two soldiers and a dragoon = 3 + 3 + 4.5 (12
        // after rounding by the game) = 4 soldiers' worth.
        assertEquals(4.0, NationEstimate.equivalent(12, 3.0), 0.001);
        assertEquals(1.8, NationEstimate.equivalent(42, 24.0), 0.001);
        assertEquals(-1.0, NationEstimate.equivalent(-1, 3.0), 0.001);
    }

    public void testChangePerTurn() {
        SortedMap<Integer, Integer> gold = new TreeMap<>();
        gold.put(10, 1000);
        gold.put(11, -1);   // Unknown that turn
        gold.put(12, 1300);
        gold.put(13, 1600);
        // Over turns 10..13: +600 in 3 turns
        assertEquals(200.0, NationEstimate.perTurn(gold, 5), 0.001);
        // Only the last two turns in the window
        assertEquals(300.0, NationEstimate.perTurn(gold, 1), 0.001);
        // One value is not a trend
        SortedMap<Integer, Integer> one = new TreeMap<>();
        one.put(5, 100);
        assertNull(NationEstimate.perTurn(one, 5));
        assertNull(NationEstimate.perTurn(new TreeMap<>(), 5));
        // Last value unknown: no trend
        gold.put(14, -1);
        assertNull(NationEstimate.perTurn(gold, 5));
    }

    public void testBellsFromRebels() {
        // 40 colonists; rebels 20% -> 25% in 5 turns: 8 -> 10 rebels,
        // 2 rebels = 400 bells in 5 turns = 80 bells per turn.
        SortedMap<Integer, Integer> rebels10 = new TreeMap<>();
        rebels10.put(20, (int)Math.round(10 * NationEstimate.rebels(20, 40)));
        rebels10.put(25, (int)Math.round(10 * NationEstimate.rebels(25, 40)));
        assertEquals(80.0, NationEstimate.bellsPerTurn(rebels10, 5), 0.001);
        // Falling support is not negative bells
        rebels10.put(26, 50);
        assertEquals(0.0, NationEstimate.bellsPerTurn(rebels10, 1), 0.001);
        assertEquals(-1.0, NationEstimate.rebels(-1, 40), 0.001);
    }

    public void testOutputYardstick() {
        Game game = getGame();
        game.changeMap(getTestMap(spec().getTileType("model.tile.plains"), true));
        Colony colony = createStandardColony(4);
        Player me = colony.getOwner();
        double per = NationEstimate.valuePerColonist(me);
        double value = NationEstimate.colonyValue(colony, me.getMarket());
        assertTrue("A working colony makes something", value > 0);
        assertEquals(value / colony.getUnitCount(), per, 0.001);
        // Their colony of 8 makes twice as much as one of 4 of ours.
        assertEquals((int)Math.round(8 * per), NationEstimate.output(8, per));
        assertEquals(-1, NationEstimate.output(8, 0));
    }

    public void testSeaFightsCounted() {
        CompanionRaids.clear();
        assertEquals(CompanionRaids.THEIRS_DAMAGED,
                     CompanionRaids.kindOf("combat.shipDamaged.enemy"));
        assertEquals(CompanionRaids.THEIRS_SUNK,
                     CompanionRaids.kindOf("combat.shipSunkByBombardment.enemy"));
        assertEquals(CompanionRaids.OURS_DAMAGED,
                     CompanionRaids.kindOf("combat.shipDamaged.ours"));
        assertEquals(-1, CompanionRaids.kindOf("combat.unitWin"));
        assertTrue(CompanionRaids.count(10, "model.nation.french",
                CompanionRaids.THEIRS_DAMAGED, "a"));
        assertFalse("Same message twice", CompanionRaids.count(10,
                "model.nation.french", CompanionRaids.THEIRS_DAMAGED, "a"));
        CompanionRaids.count(12, "model.nation.french",
                             CompanionRaids.THEIRS_SUNK, "b");
        CompanionRaids.count(1, "model.nation.french",
                             CompanionRaids.THEIRS_SUNK, "old");
        Map<String, int[]> t = CompanionRaids.totals(12, 10);
        int[] fr = t.get("model.nation.french");
        assertEquals(1, fr[CompanionRaids.THEIRS_DAMAGED]);
        assertEquals(1, fr[CompanionRaids.THEIRS_SUNK]); // turn 1 too old
        CompanionRaids.clear();
    }
}
