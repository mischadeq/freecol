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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * What a trade route does at each stop, as the editor shows it.
 */
public class RoutePlanTest extends FreeColTestCase {

    private static final GoodsType oreType
        = spec().getGoodsType("model.goods.ore");
    private static final GoodsType toolsType
        = spec().getGoodsType("model.goods.tools");
    private static final TileType plainsType
        = spec().getTileType("model.tile.plains");

    private Colony a, b, c;
    private Player player;

    private void setUpColonies() {
        Game game = getGame();
        game.changeMap(getTestMap(plainsType, true));
        a = createStandardColony(2, 1, 1);
        b = createStandardColony(2, 4, 1);
        c = createStandardColony(2, 7, 1);
        player = a.getOwner();
        a.addGoods(oreType, 100);
        a.addGoods(toolsType, 100);
    }

    private TradeRouteStop stop(Colony colony, GoodsType... cargo) {
        TradeRouteStop s = new TradeRouteStop(getGame(), colony);
        for (GoodsType gt : cargo) s.addCargo(gt);
        return s;
    }

    private static List<String> ids(List<StringTemplate> ws) {
        List<String> ret = new ArrayList<>();
        for (StringTemplate w : ws) ret.add(w.getId());
        return ret;
    }

    public void testUnloadsFollowFromTheStopBefore() {
        setUpColonies();
        // A loads ore and tools, B keeps the tools aboard, C gets both
        List<TradeRouteStop> stops = List.of(stop(a, oreType, toolsType),
                                             stop(b, toolsType), stop(c));
        assertEquals(List.of(), RoutePlan.unloads(stops, 0));
        assertEquals(List.of(oreType), RoutePlan.unloads(stops, 1));
        assertEquals(List.of(toolsType), RoutePlan.unloads(stops, 2));
        assertEquals(1, RoutePlan.unloadedAt(stops, 0, oreType));
        assertEquals(2, RoutePlan.unloadedAt(stops, 0, toolsType));
        assertTrue(RoutePlan.warnings(stops, 2, player).isEmpty());
    }

    public void testHoldsCounted() {
        setUpColonies();
        TradeRouteStop s = stop(a, oreType, toolsType, oreType);
        Map<GoodsType, Integer> h = RoutePlan.holds(s);
        assertEquals(Integer.valueOf(2), h.get(oreType));
        assertEquals(Integer.valueOf(1), h.get(toolsType));
        // Three holds, but the wagon train has two
        List<TradeRouteStop> stops = List.of(s, stop(b));
        assertEquals(List.of("tradeRouteEditor.warn.holds"),
                     ids(RoutePlan.warnings(stops, 2, player)));
        assertTrue(RoutePlan.warnings(stops, 0, player).isEmpty()); // Unknown
    }

    public void testTooFewStops() {
        setUpColonies();
        assertEquals(List.of("tradeRouteEditor.warn.tooFew"),
            ids(RoutePlan.warnings(List.of(stop(a, oreType)), 2, player)));
    }

    public void testNeverUnloaded() {
        setUpColonies();
        List<TradeRouteStop> stops = List.of(stop(a, oreType), stop(b, oreType));
        assertEquals(-1, RoutePlan.unloadedAt(stops, 0, oreType));
        assertTrue(ids(RoutePlan.warnings(stops, 2, player))
                   .contains("tradeRouteEditor.warn.neverUnloaded"));
    }

    public void testSamePlaceTwiceAndIdleStop() {
        setUpColonies();
        List<TradeRouteStop> stops = List.of(stop(a, oreType), stop(b),
                                             stop(c), stop(c));
        List<String> w = ids(RoutePlan.warnings(stops, 2, player));
        assertTrue(w.contains("tradeRouteEditor.warn.twice"));
        assertTrue(w.contains("tradeRouteEditor.warn.idle"));
    }

    public void testNothingToLoad() {
        setUpColonies();
        // B has no ore and makes none
        assertEquals(0, b.getGoodsCount(oreType));
        assertTrue(b.getNetProductionOf(oreType) <= 0);
        List<TradeRouteStop> stops = List.of(stop(b, oreType), stop(a));
        assertEquals(List.of("tradeRouteEditor.warn.nothingToLoad"),
                     ids(RoutePlan.warnings(stops, 2, player)));
        // Ore carried in from the stop before is not loaded at B
        stops = List.of(stop(a, oreType), stop(b, oreType), stop(c));
        assertTrue(RoutePlan.warnings(stops, 2, player).isEmpty());
    }
}
