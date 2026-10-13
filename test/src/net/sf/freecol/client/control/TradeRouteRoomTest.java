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
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * A carrier on a trade route only loads what fits where it unloads.
 */
public class TradeRouteRoomTest extends FreeColTestCase {

    private static final GoodsType lumberType
        = spec().getGoodsType("model.goods.lumber");
    private static final GoodsType foodType
        = spec().getPrimaryFoodType();
    private static final TileType mixedForestType
        = spec().getTileType("model.tile.mixedForest");
    private static final UnitType wagonTrainType
        = spec().getUnitType("model.unit.wagonTrain");

    private Colony source, sink;
    private Unit wagon;


    /** Lumber from the source to the sink, the wagon at the source. */
    private void setUpRoute() {
        Game game = getGame();
        game.changeMap(getTestMap(mixedForestType, true));
        source = createStandardColony(4, 1, 1);
        sink = createStandardColony(4, 3, 1);
        Player player = source.getOwner();
        wagon = new ServerUnit(game, source.getTile(), player,
                               wagonTrainType);
        TradeRoute route = new TradeRoute(game, "Lumber run", player);
        TradeRouteStop load = new TradeRouteStop(game, source);
        load.addCargo(lumberType);
        route.addStop(load);
        route.addStop(new TradeRouteStop(game, sink));
        assertNull(route.verify());
        player.addTradeRoute(route);
        wagon.setTradeRoute(route);
        wagon.setCurrentStop(0);
        sink.removeGoods(lumberType);
    }

    public void testUnloadsAtTheSink() {
        setUpRoute();
        TradeRouteStop dest = InGameController.nextUnloadStop(wagon,
                                                              lumberType);
        assertNotNull(dest);
        assertEquals(sink, dest.getLocation());
    }

    public void testNoRoomWhenFull() {
        setUpRoute();
        TradeRouteStop dest = InGameController.nextUnloadStop(wagon,
                                                              lumberType);
        sink.addGoods(lumberType, sink.getWarehouseCapacity());
        assertEquals(0, InGameController.roomAt(wagon, dest, lumberType));
    }

    public void testRoomIsWhatFitsOnArrival() {
        setUpRoute();
        TradeRouteStop dest = InGameController.nextUnloadStop(wagon,
                                                              lumberType);
        sink.addGoods(lumberType, 40);
        final int turns = wagon.getTurnsToReach(sink);
        final int expect = sink.getWarehouseCapacity()
            - Math.max(0, 40 + turns * sink.getNetProductionOf(lumberType));
        assertEquals(expect, InGameController.roomAt(wagon, dest, lumberType));
        assertTrue(expect > 0);
    }

    public void testNoLimitForFood() {
        setUpRoute();
        TradeRouteStop dest = InGameController.nextUnloadStop(wagon,
                                                              lumberType);
        // Food is not limited by the warehouse.
        assertEquals(-1, InGameController.roomAt(wagon, dest, foodType));
    }
}
