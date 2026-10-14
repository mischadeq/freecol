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


package net.sf.freecol.server.ai.military;

import net.sf.freecol.common.diplomacy.Bearings;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.AIMain;
import net.sf.freecol.server.ai.AIUnit;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.ai.mission.ScoutBearingMission;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


public class ScoutPlannerTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");
    private static final TileType ocean = spec().getTileType("model.tile.ocean");
    private static final UnitType frigate = spec().getUnitType("model.unit.frigate");
    private static final String FRENCH = "model.nation.french";


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /**
     * Two islands, nothing explored.  The Dutch have seen French
     * soldiers coming from the west; at war they send their frigate to
     * look, and stop once the French colony is in sight.
     */
    public void testSendsAShipTheWayTheEnemyCameFrom() throws Exception {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(30, 20).setBaseTileType(ocean);
        mb.setExploredByAll(false);
        for (int y = 3; y < 17; y++) {
            for (int x = 1; x <= 5; x++) mb.setTileType(x, y, plains);
            for (int x = 22; x <= 26; x++) mb.setTileType(x, y, plains);
        }
        mb.setTileType(29, 0, spec().getTileType("model.tile.highSeas"));
        Game game = ServerTestHelper.startServerGame(mb.build());
        Map map = game.getMap();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        ServerPlayer french = getServerPlayer(game, FRENCH);
        Colony theirs = FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(map.getTile(5, 10)).initialColonists(2).build();
        FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(22, 10)).initialColonists(3).build();
        Unit ship = new ServerUnit(game, map.getTile(21, 10), dutch, frigate);
        dutch.setStance(french, Stance.WAR);
        french.setStance(dutch, Stance.WAR);
        dutch.getTension(french).setValue(Tension.TENSION_MAX);
        assertFalse(ScoutBearingMission.found(dutch, FRENCH));

        EuropeanAIPlayer ai = (EuropeanAIPlayer)aiMain.getAIPlayer(dutch);
        ScoutPlanner planner = new ScoutPlanner(ai);
        // Nothing seen yet: no idea where to look.
        assertNull(planner.plan(new LogBuilder(64)));

        final int now = game.getTurn().getNumber();
        for (int i = 0; i < 4; i++) {
            ai.getBearings().add(new Bearings.Sighting(FRENCH, 9, 10, now,
                Bearings.Kind.COURSE));
        }
        AIUnit scout = planner.plan(new LogBuilder(64));
        assertNotNull("a ship should go", scout);
        assertEquals(ship, scout.getUnit());
        assertTrue(scout.getMission() instanceof ScoutBearingMission);
        Tile target = (Tile)scout.getMission().getTarget();
        assertFalse(target.isLand());
        assertTrue("west, near the guess: " + target,
                   target.getX() >= 6 && target.getX() <= 12);
        // One at a time.
        assertNull(planner.plan(new LogBuilder(64)));


        // It sails west.
        game.setCurrentPlayer(dutch);
        ship.setMovesLeft(ship.getInitialMovesLeft());
        scout.getMission().doMission(new LogBuilder(64));
        assertTrue("moved west: " + ship.getTile(), ship.getTile().getX() < 21);

        // The scout and the sightings survive a save and a load.
        java.io.File file = java.io.File.createTempFile("scout", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        AIMain aiMain2 = loaded.getAIMain();
        Game game2 = loaded.getGame();
        EuropeanAIPlayer ai2 = (EuropeanAIPlayer)aiMain2.getAIPlayer(
            game2.getPlayerByNationId("model.nation.dutch"));
        assertEquals(4, ai2.getBearings().size());
        AIUnit scout2 = aiMain2.getAIUnit(
            game2.getFreeColGameObject(ship.getId(), Unit.class));
        assertTrue(scout2.getMission() instanceof ScoutBearingMission);
        assertEquals(FRENCH, ((ScoutBearingMission)scout2.getMission()).getNation());
        assertEquals(target.getId(), scout2.getMission().getTarget().getId());

        // Once the colony is in sight the job is done.
        dutch.exploreTile(theirs.getTile());
        assertTrue(ScoutBearingMission.found(dutch, FRENCH));
        assertFalse(scout.getMission().isValid());
    }

    public void testWaterNearAPoint() {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(30, 20).setBaseTileType(ocean);
        for (int y = 3; y < 17; y++) {
            for (int x = 1; x <= 5; x++) mb.setTileType(x, y, plains);
        }
        mb.setTileType(29, 0, spec().getTileType("model.tile.highSeas"));
        Game game = ServerTestHelper.startServerGame(mb.build());
        Map map = game.getMap();
        Tile t = ScoutBearingMission.waterNear(map, 4, 10);
        assertNotNull(t);
        assertFalse(t.isLand());
        assertEquals(6, t.getX());
        // Off the map is kept on it.
        assertNotNull(ScoutBearingMission.waterNear(map, 50, -10));
    }
}
