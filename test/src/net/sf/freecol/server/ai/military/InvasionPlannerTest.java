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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import net.sf.freecol.common.diplomacy.CaptureSim;
import net.sf.freecol.common.diplomacy.PlaceView;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.AIMain;
import net.sf.freecol.server.ai.AIUnit;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.ai.mission.UnitSeekAndDestroyMission;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


public class InvasionPlannerTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");
    private static final TileType ocean = spec().getTileType("model.tile.ocean");
    private static final UnitType veteran = spec().getUnitType("model.unit.veteranSoldier");
    private static final UnitType galleon = spec().getUnitType("model.unit.galleon");


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    public void testForceIsTheSmallestThatWins() {
        PlaceView weak = new PlaceView("c", "c", "x", 0, 0, 1, true, 3, false,
                                       new double[] { 2 }, 1);
        double[] strong = { 10, 10, 10, 4, 4 };
        int n = InvasionPlanner.forceFor(strong, weak, 5, 1);
        assertTrue(n >= 1 && n <= 2);
        assertTrue(CaptureSim.attack(java.util.Arrays.copyOf(strong, n), weak, 1).chance
                   >= InvasionPlanner.TARGET_CHANCE);
        // Hopeless: nobody goes.
        PlaceView fort = new PlaceView("f", "f", "x", 0, 0, 1, true, 8, false,
                                       new double[] { 30, 30, 30, 30 }, 5);
        assertEquals(0, InvasionPlanner.forceFor(new double[] { 3, 3 }, fort, 2, 1));
        // No room on ships: nobody goes.
        assertEquals(0, InvasionPlanner.forceFor(strong, weak, 0, 1));
    }

    /** Two islands: the Dutch should ship an army to the French one. */
    public void testShipsAnArmyToAnotherIsland() {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(20, 15).setBaseTileType(ocean);
        mb.setExploredByAll(true);
        for (int y = 2; y < 13; y++) {
            for (int x = 1; x <= 5; x++) mb.setTileType(x, y, plains);
            for (int x = 12; x <= 16; x++) mb.setTileType(x, y, plains);
        }
        mb.setTileType(19, 0, spec().getTileType("model.tile.highSeas"));
        Game game = ServerTestHelper.startServerGame(mb.build());
        Map map = game.getMap();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        ServerPlayer french = getServerPlayer(game, "model.nation.french");
        assertTrue(map.getTile(3, 7).getContiguity() != map.getTile(14, 7).getContiguity());

        Colony target = FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(map.getTile(5, 7)).initialColonists(2).build();
        Colony home = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(12, 7)).initialColonists(4).build();

        final Role dragoon = spec().getRole("model.role.dragoon");
        List<AIUnit> army = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Unit u = new ServerUnit(game, home.getTile(), dutch, veteran, dragoon);
            army.add(aiMain.getAIUnit(u));
        }
        new ServerUnit(game, home.getTile(), dutch, galleon);

        dutch.setStance(french, Stance.WAR);
        french.setStance(dutch, Stance.WAR);
        dutch.getTension(french).setValue(Tension.TENSION_MAX);
        dutch.exploreMap(true); // it has seen the French colony

        InvasionPlanner planner = new InvasionPlanner(
            (EuropeanAIPlayer)aiMain.getAIPlayer(dutch));
        List<AIUnit> candidates = new ArrayList<>(army);
        List<AIUnit> sent = planner.plan(candidates, new HashSet<>(), new HashSet<>(), new LogBuilder(64));
        assertFalse("Someone should go", sent.isEmpty());
        assertTrue("One dragoon stays home", sent.size() < army.size());
        for (AIUnit aiu : sent) {
            assertTrue(aiu.getMission() instanceof UnitSeekAndDestroyMission);
            assertEquals(target, aiu.getMission().getTarget());
            assertNotNull("Needs a ship", aiu.getMission().getTransportDestination());
            assertTrue(InvasionPlanner.onInvasion(aiu));
            assertFalse(candidates.contains(aiu));
        }

        // At peace nobody goes, and the attackers stop counting as an invasion.
        dutch.setStance(french, Stance.PEACE);
        french.setStance(dutch, Stance.PEACE);
        assertTrue(planner.plan(new ArrayList<>(army), new HashSet<>(),
                                new HashSet<>(), new LogBuilder(64)).isEmpty());
        assertFalse(InvasionPlanner.onInvasion(sent.get(0)));
    }

    /** A coalition's joint strike: wait, then sail to land on time. */
    public void testJointStrikeIsTimed() {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(20, 15).setBaseTileType(ocean);
        mb.setExploredByAll(true);
        for (int y = 2; y < 13; y++) {
            for (int x = 1; x <= 5; x++) mb.setTileType(x, y, plains);
            for (int x = 12; x <= 16; x++) mb.setTileType(x, y, plains);
        }
        mb.setTileType(19, 0, spec().getTileType("model.tile.highSeas"));
        Game game = ServerTestHelper.startServerGame(mb.build());
        Map map = game.getMap();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        ServerPlayer french = getServerPlayer(game, "model.nation.french");
        ServerPlayer english = getServerPlayer(game, "model.nation.english");
        Colony target = FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(map.getTile(5, 7)).initialColonists(2).build();
        Colony home = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(12, 7)).initialColonists(4).build();
        final Role dragoon = spec().getRole("model.role.dragoon");
        List<AIUnit> army = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Unit u = new ServerUnit(game, home.getTile(), dutch, veteran, dragoon);
            army.add(aiMain.getAIUnit(u));
        }
        new ServerUnit(game, home.getTile(), dutch, galleon);
        dutch.setStance(french, Stance.WAR);
        french.setStance(dutch, Stance.WAR);
        dutch.getTension(french).setValue(Tension.TENSION_MAX);
        dutch.exploreMap(true);

        EuropeanAIPlayer eai = (EuropeanAIPlayer)aiMain.getAIPlayer(english);
        net.sf.freecol.common.diplomacy.Coalition c
            = new net.sf.freecol.common.diplomacy.Coalition(
                "model.nation.french", game.getTurn().getNumber());
        c.members.add("model.nation.english");
        c.members.add("model.nation.dutch");
        c.jointTarget = target.getId();
        c.jointName = target.getName();
        final int now = game.getTurn().getNumber();
        c.strikeTurn = now + 10;
        eai.getCoalitions().add(c);
        final int travel = net.sf.freecol.server.ai.diplomacy.AlliancePlanner
            .travelTurns(dutch, target.getTile());
        assertTrue("by sea: " + travel, travel >= 2 && travel < 10);

        InvasionPlanner planner = new InvasionPlanner(
            (EuropeanAIPlayer)aiMain.getAIPlayer(dutch));
        LogBuilder lb = new LogBuilder(64);
        assertTrue("too early: waits",
            planner.plan(new ArrayList<>(army), new HashSet<>(), new HashSet<>(), lb).isEmpty());
        assertTrue(lb.toString(), lb.toString().contains("joint strike"));
        // Time to set off to land on the strike turn.
        c.strikeTurn = now + travel;
        List<AIUnit> sent = planner.plan(new ArrayList<>(army), new HashSet<>(),
                                         new HashSet<>(), new LogBuilder(64));
        assertFalse(sent.isEmpty());
        assertEquals(target, sent.get(0).getMission().getTarget());
    }

    /** A whole AI turn: the army boards the galleon. */
    public void testTheArmyBoards() {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(20, 15).setBaseTileType(ocean);
        mb.setExploredByAll(true);
        for (int y = 2; y < 13; y++) {
            for (int x = 1; x <= 5; x++) mb.setTileType(x, y, plains);
            for (int x = 12; x <= 16; x++) mb.setTileType(x, y, plains);
        }
        Game game = ServerTestHelper.startServerGame(mb.build());
        Map map = game.getMap();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        ServerPlayer french = getServerPlayer(game, "model.nation.french");
        FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(map.getTile(5, 7)).initialColonists(2).build();
        Colony home = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(12, 7)).initialColonists(4).build();
        final Role dragoon = spec().getRole("model.role.dragoon");
        List<Unit> army = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            army.add(new ServerUnit(game, home.getTile(), dutch, veteran, dragoon));
        }
        Unit ship = new ServerUnit(game, home.getTile(), dutch, galleon);
        dutch.setStance(french, Stance.WAR);
        french.setStance(dutch, Stance.WAR);
        dutch.getTension(french).setValue(Tension.TENSION_MAX);
        dutch.exploreMap(true);

        game.setCurrentPlayer(dutch);
        ((EuropeanAIPlayer)aiMain.getAIPlayer(dutch)).startWorking();
        int aboard = 0, going = 0;
        for (Unit u : army) {
            if (u.isOnCarrier()) aboard++;
            AIUnit aiu = aiMain.getAIUnit(u);
            if (aiu != null && aiu.getMission() instanceof UnitSeekAndDestroyMission) going++;
        }
        assertTrue("Some units sent (" + going + ")", going > 0);
        assertTrue("Some units on board or the ship sailing (" + aboard + ")",
                   aboard > 0 || ship.getTile() != home.getTile());
    }

    public void testColoniesInDangerKeepTheirUnits() {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(20, 15).setBaseTileType(ocean);
        mb.setExploredByAll(true);
        for (int y = 2; y < 13; y++) {
            for (int x = 1; x <= 5; x++) mb.setTileType(x, y, plains);
            for (int x = 12; x <= 16; x++) mb.setTileType(x, y, plains);
        }
        Game game = ServerTestHelper.startServerGame(mb.build());
        Map map = game.getMap();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        ServerPlayer french = getServerPlayer(game, "model.nation.french");
        FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(map.getTile(5, 7)).initialColonists(2).build();
        Colony home = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(12, 7)).initialColonists(4).build();
        final Role dragoon = spec().getRole("model.role.dragoon");
        List<AIUnit> army = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            army.add(aiMain.getAIUnit(new ServerUnit(game, home.getTile(), dutch,
                                                     veteran, dragoon)));
        }
        new ServerUnit(game, home.getTile(), dutch, galleon);
        dutch.setStance(french, Stance.WAR);
        french.setStance(dutch, Stance.WAR);
        dutch.getTension(french).setValue(Tension.TENSION_MAX);
        dutch.exploreMap(true);
        HashSet<net.sf.freecol.common.model.Settlement> danger = new HashSet<>();
        danger.add(home);
        InvasionPlanner planner = new InvasionPlanner((EuropeanAIPlayer)aiMain.getAIPlayer(dutch));
        // Under attack: all four stay.
        assertTrue(planner.plan(new ArrayList<>(army), danger, new HashSet<>(),
                                new LogBuilder(64)).isEmpty());
        // Merely threatened: one stays, like any colony.
        List<AIUnit> sent = planner.plan(new ArrayList<>(army), new HashSet<>(),
                                         danger, new LogBuilder(64));
        assertFalse(sent.isEmpty());
        assertTrue(sent.size() <= 3);
    }
}
