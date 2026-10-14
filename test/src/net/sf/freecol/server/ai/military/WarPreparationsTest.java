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

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
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
import net.sf.freecol.server.ai.mission.DefendSettlementMission;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


public class WarPreparationsTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");
    private static final TileType ocean = spec().getTileType("model.tile.ocean");
    private static final UnitType veteran = spec().getUnitType("model.unit.veteranSoldier");
    private static final GoodsType muskets = spec().getGoodsType("model.goods.muskets");
    private static final GoodsType horses = spec().getGoodsType("model.goods.horses");


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** Enemy soldiers next door: colonists take up the muskets in store. */
    public void testArmsColonistsInDanger() {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(20, 15).setBaseTileType(plains).setExploredByAll(true);
        Game game = ServerTestHelper.startServerGame(mb.build());
        Map map = game.getMap();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        ServerPlayer english = getServerPlayer(game, "model.nation.english");
        Colony home = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(5, 7)).initialColonists(5).build();
        home.addGoods(muskets, 150);
        home.addGoods(horses, 50);
        FreeColTestUtils.getColonyBuilder().player(english)
            .colonyTile(map.getTile(12, 7)).initialColonists(3).build();
        final Role soldier = spec().getRole("model.role.soldier");
        for (int i = 0; i < 6; i++) {
            new ServerUnit(game, map.getTile(7, 7), english, veteran, soldier);
        }
        dutch.setStance(english, Stance.WAR);
        english.setStance(dutch, Stance.WAR);
        dutch.getTension(english).setValue(Tension.TENSION_MAX);
        dutch.exploreMap(true);
        game.setCurrentPlayer(dutch);

        EuropeanAIPlayer ai = (EuropeanAIPlayer)aiMain.getAIPlayer(dutch);
        int done = new WarPreparations(ai).plan(new LogBuilder(64));
        assertTrue("someone armed", done > 0);
        assertTrue("the colony keeps its workers",
                   home.getUnitCount() >= WarPreparations.KEEP_WORKERS);
        int armed = 0, mounted = 0;
        for (Unit u : home.getTile().getUnitList()) {
            if (!u.isArmed()) continue;
            armed++;
            if (u.isMounted()) mounted++;
            AIUnit aiu = aiMain.getAIUnit(u);
            assertTrue(aiu.getMission() instanceof DefendSettlementMission);
            assertTrue(ai.isKeptArmed(u));
            // The colony planner leaves it armed.
            assertFalse(aiu.isAvailableForWork(home));
        }
        assertEquals(done, armed);
        assertEquals("the horses went to one dragoon", 1, mounted);
        assertTrue(home.getGoodsCount(muskets) < 150);

        // Long after the danger, the colony may have them back.
        Unit one = home.getTile().getUnitList().stream()
            .filter(Unit::isArmed).findFirst().get();
        game.setTurn(new net.sf.freecol.common.model.Turn(
            game.getTurn().getNumber() + EuropeanAIPlayer.KEEP_ARMED + 1));
        assertFalse(ai.isKeptArmed(one));
        assertTrue(aiMain.getAIUnit(one).isAvailableForWork(home));
    }

    /** An enemy colony across the sea and no ship: buy one. */
    public void testBuysAShipForAnArmy() {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(20, 15).setBaseTileType(ocean).setExploredByAll(true);
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
        FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(map.getTile(5, 7)).initialColonists(6).build();
        Colony home = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(12, 7)).initialColonists(4).build();
        final Role dragoon = spec().getRole("model.role.dragoon");
        for (int i = 0; i < 4; i++) {
            new ServerUnit(game, home.getTile(), dutch, veteran, dragoon);
        }
        dutch.setStance(french, Stance.WAR);
        french.setStance(dutch, Stance.WAR);
        dutch.getTension(french).setValue(Tension.TENSION_MAX);
        dutch.exploreMap(true);
        game.setCurrentPlayer(dutch);

        EuropeanAIPlayer ai = (EuropeanAIPlayer)aiMain.getAIPlayer(dutch);
        dutch.setGold(0);
        assertEquals("no gold, no ship", 0, new WarPreparations(ai).plan(new LogBuilder(64)));
        dutch.setGold(20000);
        final long before = dutch.getUnitSet().stream().filter(Unit::isNaval).count();
        new WarPreparations(ai).plan(new LogBuilder(64));
        final long after = dutch.getUnitSet().stream().filter(Unit::isNaval).count();
        assertEquals("one ship bought", before + 1, after);
        assertTrue(dutch.getGold() < 20000);
        Unit ship = dutch.getUnitSet().stream().filter(Unit::isNaval).findFirst().get();
        assertTrue(ship.isInEurope());
        assertTrue(ship.getType().getSpace() > 0);
    }
}
