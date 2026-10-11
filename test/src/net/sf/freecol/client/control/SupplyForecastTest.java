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

import java.util.List;

import net.sf.freecol.client.control.SupplyForecast.Delivery;
import net.sf.freecol.client.control.SupplyForecast.Outlook;
import net.sf.freecol.client.control.SupplyForecast.Status;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


public class SupplyForecastTest extends FreeColTestCase {

    private static final GoodsType lumberType
        = spec().getGoodsType("model.goods.lumber");
    private static final GoodsType fursType
        = spec().getGoodsType("model.goods.furs");
    private static final TileType mixedForestType
        = spec().getTileType("model.tile.mixedForest");
    private static final UnitType wagonTrainType
        = spec().getUnitType("model.unit.wagonTrain");
    private static final BuildingType carpenterHouseType
        = spec().getBuildingType("model.building.carpenterHouse");

    private Colony source, sink;
    private Player player;
    private Unit wagon;
    private TradeRoute route;


    /**
     * Two colonies two turns apart, a wagon at the first, and a
     * route that loads lumber at the first and unloads at the second.
     */
    private void setUpRoute() {
        Game game = getGame();
        game.changeMap(getTestMap(mixedForestType, true));
        source = createStandardColony(4, 1, 1);
        sink = createStandardColony(4, 3, 1);
        player = source.getOwner();
        wagon = new ServerUnit(game, source.getTile(), player,
                               wagonTrainType);
        assertEquals(2, wagon.getTurnsToReach(sink));

        route = new TradeRoute(game, "Lumber run", player);
        TradeRouteStop load = new TradeRouteStop(game, source);
        load.addCargo(lumberType);
        route.addStop(load);
        route.addStop(new TradeRouteStop(game, sink));
        assertNull(route.verify());
        player.addTradeRoute(route);

        source.getExportData(lumberType).setExportLevel(0);
        source.removeGoods(lumberType);
        sink.removeGoods(lumberType);
    }

    public void testNoRoute() {
        setUpRoute();
        SupplyForecast f = new SupplyForecast(player);
        // Nothing delivers furs anywhere, nor lumber to the source.
        assertEquals(Status.NO_ROUTE, f.getOutlook(sink, fursType, 5).status);
        assertEquals(Status.NO_ROUTE,
                     f.getOutlook(source, lumberType, 5).status);
    }

    public void testNoCarrier() {
        setUpRoute();
        source.addGoods(lumberType, 100);
        // The route exists but the wagon is not assigned to it.
        Outlook o = new SupplyForecast(player).getOutlook(sink, lumberType, 5);
        assertEquals(Status.NO_CARRIER, o.status);
        assertTrue(o.isBroken());
        assertEquals(route, o.route);
    }

    public void testNothingComing() {
        setUpRoute();
        wagon.setTradeRoute(route);
        // Carrier assigned, but the source has no lumber to load.
        Outlook o = new SupplyForecast(player).getOutlook(sink, lumberType, 5);
        assertEquals(Status.NOTHING_COMING, o.status);
        assertTrue(o.isBroken());
    }

    public void testDeliveryFromSource() {
        setUpRoute();
        wagon.setTradeRoute(route);
        source.addGoods(lumberType, 80);

        SupplyForecast f = new SupplyForecast(player);
        Delivery d = f.getEarliest(sink, lumberType);
        assertNotNull(d);
        assertEquals(wagon, d.unit);
        assertEquals(route, d.route);
        assertEquals(2, d.turns);
        assertEquals(80, d.amount);

        assertEquals(Status.IN_TIME, f.getOutlook(sink, lumberType, 5).status);
        assertEquals(Status.IN_TIME, f.getOutlook(sink, lumberType, 2).status);
        assertEquals(Status.IN_TIME, f.getOutlook(sink, lumberType, -1).status);
        Outlook late = f.getOutlook(sink, lumberType, 1);
        assertEquals(Status.LATE, late.status);
        assertTrue(late.isBroken());
        assertEquals(1, f.getTargets().size());
    }

    public void testDeliveryAlreadyAboard() {
        setUpRoute();
        wagon.setTradeRoute(route);
        wagon.addGoods(lumberType, 50);
        wagon.setCurrentStop(1); // Loaded and heading for the sink.

        Delivery d = new SupplyForecast(player).getEarliest(sink, lumberType);
        assertNotNull(d);
        assertEquals(2, d.turns);
        assertEquals(50, d.amount);
    }

    public void testWatch() {
        setUpRoute();
        // Make the sink burn lumber: stock it and staff the carpenter.
        sink.addGoods(lumberType, 30);
        sink.getUnitList().get(0)
            .setLocation(sink.getBuilding(carpenterHouseType));
        sink.invalidateCache();
        int p = sink.getAdjustedNetProductionOf(lumberType);
        assertTrue("Sink should consume lumber: " + p, p < 0);
        Integer turnsLeft = SupplyWatch.getTurnsLeft(sink, lumberType);
        assertNotNull(turnsLeft);
        assertEquals(30 / -p, turnsLeft.intValue());
        assertNull(SupplyWatch.getTurnsLeft(sink, fursType));

        SupplyWatch watch = new SupplyWatch();
        List<ModelMessage> ms;

        // Turn 1: route with no carrier => one warning.
        ms = watch.check(player, 1);
        assertEquals(1, ms.size());
        assertEquals("supply.noCarrier", ms.get(0).getId());

        // Turn 2: nothing changed => no repeat.
        assertEquals(0, watch.check(player, 2).size());

        // Turn 3: wagon assigned and lumber available => all well.
        wagon.setTradeRoute(route);
        source.addGoods(lumberType, 100);
        assertEquals(0, watch.check(player, 3).size());

        // Turn 4: the wagon has not moved, so its arrival slips a turn.
        ms = watch.check(player, 4);
        assertEquals(1, ms.size());
        assertEquals("supply.delayed", ms.get(0).getId());

        // Turn 5: the wagon is taken off the route => warn again.
        wagon.setTradeRoute(null);
        ms = watch.check(player, 5);
        assertEquals(1, ms.size());
        assertEquals("supply.noCarrier", ms.get(0).getId());
    }
}
