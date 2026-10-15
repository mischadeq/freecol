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

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Which units count as having orders still to carry out (Mischa: the
 * execute-orders button said one unit was waiting, but no unit could
 * move).
 */
public class PendingOrdersTest extends FreeColTestCase {

    private static final TileType plainsType
        = spec().getTileType("model.tile.plains");
    private static final UnitType colonistType
        = spec().getUnitType("model.unit.freeColonist");
    private static final UnitType caravelType
        = spec().getUnitType("model.unit.caravel");

    public void testWhoHasOrders() {
        Game game = getGame();
        game.changeMap(getTestMap(plainsType, true));
        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        Tile here = game.getMap().getTile(5, 5);
        Tile there = game.getMap().getTile(8, 8);

        // A colonist on the map with somewhere to go: yes.
        Unit walker = new ServerUnit(game, here, dutch, colonistType);
        walker.setDestination(there);
        assertTrue(InGameController.hasOrdersToCarryOut(walker));

        // The same without moves left: no.
        walker.setMovesLeft(0);
        assertFalse(InGameController.hasOrdersToCarryOut(walker));

        // A colonist without orders: no.
        Unit idle = new ServerUnit(game, here, dutch, colonistType);
        assertFalse(InGameController.hasOrdersToCarryOut(idle));

        // A colonist in Europe bound for the map waits for a ship: no.
        Unit docks = new ServerUnit(game, dutch.getEurope(), dutch,
                                    colonistType);
        docks.setDestination(there);
        assertFalse(InGameController.hasOrdersToCarryOut(docks));

        // A ship in Europe bound for the map: yes, it can sail.
        Unit ship = new ServerUnit(game, dutch.getEurope(), dutch,
                                   caravelType);
        ship.setDestination(there);
        assertTrue(InGameController.hasOrdersToCarryOut(ship));

        // A ship in Europe that is to stay there: no.
        ship.setDestination(dutch.getEurope());
        assertFalse(InGameController.hasOrdersToCarryOut(ship));

        // A ship on the high seas: no, nothing to do until it arrives.
        Unit sailing = new ServerUnit(game, dutch.getHighSeas(), dutch,
                                      caravelType);
        sailing.setDestination(there);
        assertFalse(InGameController.hasOrdersToCarryOut(sailing));
    }
}
