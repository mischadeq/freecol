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

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The tribes next to the nations on the companion page (mod): their
 * settlements from the Native Affairs report, braves from the rules,
 * braves in sight as they are; only tribes we met.
 */
public class TribeStrengthTest extends FreeColTestCase {

    public void testTribeFigures() {
        Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        IndianSettlement camp = new FreeColTestCase.IndianSettlementBuilder(game)
            .initialBravesInCamp(3).build();
        Player tribe = camp.getOwner();

        CompanionTribeStrength.Figures f
            = CompanionTribeStrength.figures(game, dutch, tribe, 5);
        assertEquals(5, f.settlements);
        assertEquals(1, f.known);
        assertEquals(0, f.seen);
        assertTrue("capital and 4 more, a few braves each",
                   f.braves >= 5 * 4 && f.braves <= 5 * 12);
        assertEquals(f.braves * 1.5, f.strength, 0.001);
        assertEquals(f.strength / 3.0, f.soldiers, 0.25);

        // A brave in sight, next to a Dutch colonist.
        final Tile t = camp.getTile().getNeighbourOrNull(
            net.sf.freecol.common.model.Direction.N);
        final UnitType colonist = spec().getDefaultUnitType();
        new net.sf.freecol.server.model.ServerUnit(game, t, dutch, colonist);
        dutch.invalidateCanSeeTiles();
        new net.sf.freecol.server.model.ServerUnit(game, t, tribe,
            spec().getUnitType("model.unit.brave"));
        f = CompanionTribeStrength.figures(game, dutch, tribe, 5);
        assertEquals(1, f.seen);
        assertEquals(Integer.valueOf(1), f.kinds.get("brave"));

        // Unknown count: the settlements seen.
        assertEquals(1, CompanionTribeStrength.figures(game, dutch, tribe, -1)
                     .settlements);

        // Only tribes we met go on the page.
        Json j = new Json().obj();
        CompanionTribeStrength.put(j, game, dutch, 12, p -> 5);
        String out = j.end().toString();
        if (dutch.hasContacted(tribe)) {
            assertTrue(out, out.contains(tribe.getNationId()));
        } else {
            assertFalse(out, out.contains(tribe.getNationId()));
        }
        dutch.setStance(tribe, Stance.PEACE);
        tribe.setStance(dutch, Stance.PEACE);
        j = new Json().obj();
        CompanionTribeStrength.put(j, game, dutch, 12, p -> 5);
        out = j.end().toString();
        assertTrue(out, out.contains(tribe.getNationId()));
        assertTrue(out, out.contains("\"settlements\":5"));
        assertTrue(out, out.contains("\"verdict\""));
    }
}
