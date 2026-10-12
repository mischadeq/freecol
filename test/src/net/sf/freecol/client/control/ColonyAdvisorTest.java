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

import java.util.List;

import net.sf.freecol.client.control.ColonyAdvisor.Advice;
import net.sf.freecol.client.control.ColonyAdvisor.Duty;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The colony advisor must never suggest anything that cuts what a
 * colony supplies to another along a trade route.
 */
public class ColonyAdvisorTest extends FreeColTestCase {

    private static final GoodsType foodType
        = spec().getPrimaryFoodType();
    private static final GoodsType grainType
        = spec().getGoodsType("model.goods.grain");
    private static final GoodsType hammersType
        = spec().getGoodsType("model.goods.hammers");
    private static final GoodsType lumberType
        = spec().getGoodsType("model.goods.lumber");
    private static final TileType plainsType
        = spec().getTileType("model.tile.plains");
    private static final BuildingType carpenterHouseType
        = spec().getBuildingType("model.building.carpenterHouse");
    private static final BuildingType blacksmithHouseType
        = spec().getBuildingType("model.building.blacksmithHouse");
    private static final UnitType expertFarmerType
        = spec().getUnitType("model.unit.expertFarmer");

    private Colony farm, town;
    private Player player;


    private static void work(Unit u, WorkLocation wl, GoodsType gt) {
        u.changeWorkType(gt);
        u.setLocation(wl);
    }

    /** A free tile of a colony, other than the centre. */
    private static ColonyTile freeTile(Colony c) {
        for (ColonyTile ct : c.getColonyTiles()) {
            if (!ct.isColonyCenterTile() && ct.isEmpty()
                && ct.getWorkTile().getOwningSettlement() == c) return ct;
        }
        fail("No free tile in " + c.getName());
        return null;
    }

    /**
     * A farm colony that feeds a town along a route.  The town's six
     * colonists all work in buildings, so it is short of food.
     *
     * @param farmers The number of colonists farming at the farm.
     * @param carpenters The number of colonists at the farm in the
     *     carpenter's house.
     */
    private void setUpNetwork(int farmers, int carpenters) {
        final Game game = getGame();
        game.changeMap(getTestMap(plainsType, true));
        farm = createStandardColony(farmers + carpenters, 1, 1);
        town = createStandardColony(6, 6, 1);
        player = farm.getOwner();

        List<Unit> units = farm.getUnitList();
        for (int i = 0; i < units.size(); i++) {
            if (i < farmers) {
                work(units.get(i), freeTile(farm), grainType);
            } else {
                work(units.get(i), farm.getBuilding(carpenterHouseType),
                     hammersType);
            }
        }
        units = town.getUnitList();
        for (int i = 0; i < units.size(); i++) {
            work(units.get(i), town.getBuilding((i < 3) ? carpenterHouseType
                    : blacksmithHouseType),
                (i < 3) ? hammersType
                    : spec().getGoodsType("model.goods.tools"));
        }
        farm.invalidateCache();
        town.invalidateCache();

        TradeRoute route = new TradeRoute(game, "Food run", player);
        TradeRouteStop load = new TradeRouteStop(game, farm);
        load.addCargo(foodType);
        route.addStop(load);
        route.addStop(new TradeRouteStop(game, town));
        assertNull(route.verify());
        player.addTradeRoute(route);
    }

    public void testDutyFromRoute() {
        setUpNetwork(1, 0);
        final int short_ = -town.getAdjustedNetProductionOf(foodType);
        assertTrue("The town should be short of food", short_ > 0);

        ColonyAdvisor advisor = new ColonyAdvisor(player);
        List<Duty> duties = advisor.getDuties(farm);
        assertEquals(1, duties.size());
        Duty d = duties.get(0);
        assertEquals(foodType, d.goods);
        assertEquals(town, d.to);
        assertEquals(short_, d.need);
        assertEquals(1, advisor.getSupplies(town).size());
        assertEquals(short_, advisor.getRequired(farm, foodType));
        // Grain is stored as food, so it counts for the duty too.
        assertEquals(short_, advisor.getRequired(farm, grainType));
    }

    public void testMayNotCutTheFoodSupply() {
        setUpNetwork(1, 0);
        ColonyAdvisor advisor = new ColonyAdvisor(player);
        final int net = farm.getAdjustedNetProductionOf(foodType);
        final int need = advisor.getRequired(farm, foodType);
        final int spare = net - need;
        if (spare > 0) {
            assertTrue(advisor.mayLose(farm, grainType, spare));
        }
        assertFalse(advisor.mayLose(farm, grainType, Math.max(1, spare + 1)));
        // Goods without a duty may go, as long as no surplus turns
        // into a shortage.
        assertTrue(advisor.mayLose(farm, lumberType, 0));
    }

    public void testBellsAdviceLeavesTheFarmerAlone() {
        // One farmer feeding the town, one carpenter.
        setUpNetwork(1, 1);
        farm.addGoods(lumberType, 100);
        farm.invalidateCache();
        ColonyAdvisor advisor = new ColonyAdvisor(player);
        Advice bells = null;
        for (Advice a : advisor.advise(farm)) {
            if ("noBells".equals(a.kind)) bells = a;
        }
        assertNotNull("Nobody in the town hall should be noticed", bells);
        String text = Messages.message(bells.text);
        assertFalse("Never take the farmer: " + text,
                    text.contains(Messages.getName(grainType)));
    }

    public void testBellsAdviceWhenOnlyTheFarmerCouldGo() {
        setUpNetwork(2, 0);
        ColonyAdvisor advisor = new ColonyAdvisor(player);
        // If the farm has food to spare beyond the duty, moving one
        // farmer may be fine; otherwise nobody is suggested.
        final int spare = farm.getAdjustedNetProductionOf(foodType)
            - advisor.getRequired(farm, foodType);
        for (Advice a : advisor.advise(farm)) {
            if (!"noBells".equals(a.kind)) continue;
            String text = Messages.message(a.text);
            if (text.contains(Messages.getName(grainType))) {
                assertTrue("Moved a farmer the town needs: " + text,
                           spare > 0);
            }
        }
    }

    public void testSwapThatOnlyGains() {
        final Game game = getGame();
        game.changeMap(getTestMap(plainsType, true));
        Colony colony = createStandardColony(2, 1, 1);
        colony.addGoods(lumberType, 100);
        List<Unit> units = colony.getUnitList();
        Unit farmer = units.get(0), colonist = units.get(1);
        farmer.changeType(expertFarmerType);
        // The expert farmer saws planks, the free colonist farms.
        work(farmer, colony.getBuilding(carpenterHouseType), hammersType);
        work(colonist, freeTile(colony), grainType);
        colony.invalidateCache();

        ColonyAdvisor advisor = new ColonyAdvisor(colony.getOwner());
        boolean swap = false;
        for (Advice a : advisor.advise(colony)) {
            swap |= "swap".equals(a.kind);
        }
        assertTrue("Swapping the farmer onto the field only gains", swap);
    }
}
