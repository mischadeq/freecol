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

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Who may join a colony to be taught there (Mischa: a soldier at the
 * palisade could be picked as a student, but nothing happened).
 */
public class JoinToLearnTest extends FreeColTestCase {

    private static final TileType plainsType
        = spec().getTileType("model.tile.plains");
    private static final UnitType colonistType
        = spec().getUnitType("model.unit.freeColonist");
    private static final UnitType wagonTrainType
        = spec().getUnitType("model.unit.wagonTrain");
    private static final Role soldierRole
        = spec().getRole("model.role.soldier");

    public void testWhoCanJoinToLearn() {
        Game game = getGame();
        game.changeMap(getTestMap(plainsType, true));
        Colony colony = createStandardColony(3, 2, 2);
        Player player = colony.getOwner();

        // A soldier standing on the colony tile: yes.
        Unit soldier = new ServerUnit(game, colony.getTile(), player,
                                      colonistType, soldierRole);
        assertFalse(soldier.isInColony());
        assertTrue(InGameController.canJoinToLearn(soldier, colony));

        // A colonist already working inside: no need to join.
        Unit worker = colony.getUnitList().get(0);
        assertTrue(worker.isInColony());
        assertFalse(InGameController.canJoinToLearn(worker, colony));

        // On another tile: no.
        Tile elsewhere = game.getMap().getTile(6, 6);
        Unit away = new ServerUnit(game, elsewhere, player, colonistType);
        assertFalse(InGameController.canJoinToLearn(away, colony));

        // A wagon train is not a person: no.
        Unit wagon = new ServerUnit(game, colony.getTile(), player,
                                    wagonTrainType);
        assertFalse(InGameController.canJoinToLearn(wagon, colony));

        // A colonist riding in the wagon: not standing on the tile.
        Unit rider = new ServerUnit(game, wagon, player, colonistType);
        assertFalse(InGameController.canJoinToLearn(rider, colony));
    }
}
