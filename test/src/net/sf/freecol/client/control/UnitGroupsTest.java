/**
 *  Copyright (C) 2002-2024  The FreeCol Team
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

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


public class UnitGroupsTest extends FreeColTestCase {

    private static final TileType plainsType
        = spec().getTileType("model.tile.plains");
    private static final UnitType wagonTrainType
        = spec().getUnitType("model.unit.wagonTrain");
    private static final UnitType soldierType
        = spec().getUnitType("model.unit.veteranSoldier");


    public void testGroups() throws Exception {
        Game game = getGame();
        game.changeMap(getTestMap(plainsType, true));
        Colony colony = createStandardColony(1, 5, 8);
        Player player = colony.getOwner();
        Unit w1 = new ServerUnit(game, colony.getTile(), player, wagonTrainType);
        Unit w2 = new ServerUnit(game, colony.getTile(), player, wagonTrainType);
        Unit s1 = new ServerUnit(game, colony.getTile(), player, soldierType);

        File dir = Files.createTempDirectory("fcgroups").toFile();
        UnitGroups groups = new UnitGroups(dir);
        assertTrue(groups.getGroupNames(game).isEmpty());
        assertNull(groups.getGroupName(w1));
        assertTrue(groups.getCompanions(w1).isEmpty());
        assertEquals("Army 1", groups.suggestName(game, "Army"));

        groups.addToGroup(game, "Lumber train", Arrays.asList(w1, w2));
        assertEquals("Lumber train", groups.getGroupName(w1));
        assertEquals(Arrays.asList(w2), groups.getCompanions(w1));
        assertEquals(2, groups.getMembers(game, player, "Lumber train").size());

        // A unit is in one group only: moving w2 leaves w1 alone.
        groups.addToGroup(game, "Army 1", Arrays.asList(w2, s1));
        assertEquals("Army 1", groups.getGroupName(w2));
        assertTrue(groups.getCompanions(w1).isEmpty());
        assertEquals("Army 2", groups.suggestName(game, "Army"));
        assertEquals(Arrays.asList("Lumber train", "Army 1"),
                     groups.getGroupNames(game));

        // The groups survive a restart of the client.
        UnitGroups again = new UnitGroups(dir);
        assertEquals(Arrays.asList("Lumber train", "Army 1"),
                     again.getGroupNames(game));
        assertEquals(Arrays.asList(s1), again.getCompanions(w2));

        // A lost unit drops out of its group.
        s1.dispose();
        List<Unit> left = again.getMembers(game, player, "Army 1");
        assertEquals(Arrays.asList(w2), left);

        assertEquals("Army 1", again.leaveGroup(w2));
        assertNull(again.getGroupName(w2));
        assertEquals(Arrays.asList("Lumber train"), again.getGroupNames(game));
        assertTrue(again.disband(game, "Lumber train"));
        assertFalse(again.disband(game, "Lumber train"));
        assertTrue(new UnitGroups(dir).getGroupNames(game).isEmpty());
    }
}
