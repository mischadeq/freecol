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

package net.sf.freecol.common.model;

import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Empty wagon trains on ships (mod, player only): one slot each, so a
 * caravel carries two; with goods they do not fit; aboard they stay
 * empty.
 */
public class WagonOnShipTest extends FreeColTestCase {

    private static final UnitType wagonType
        = spec().getUnitType("model.unit.wagonTrain");
    private static final UnitType caravelType
        = spec().getUnitType("model.unit.caravel");
    private static final UnitType galleonType
        = spec().getUnitType("model.unit.galleon");
    private static final GoodsType furs = spec().getGoodsType("model.goods.furs");

    private Game game;
    private Player dutch;
    private Tile land, sea;

    private void setUpGame() {
        game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"));
        game.changeMap(map);
        dutch = game.getPlayerByNationId("model.nation.dutch");
        dutch.setAI(false);
        land = map.getTile(9, 5);
        sea = map.getTile(10, 5);
        assertTrue(land.isLand());
        assertFalse(sea.isLand());
    }

    public void testCaravelCarriesTwoEmptyWagons() {
        setUpGame();
        final Unit caravel = new ServerUnit(game, sea, dutch, caravelType);
        final Unit w1 = new ServerUnit(game, land, dutch, wagonType);
        final Unit w2 = new ServerUnit(game, land, dutch, wagonType);
        final Unit w3 = new ServerUnit(game, land, dutch, wagonType);
        assertEquals(Unit.SHIPPED_WAGON_SPACE, w1.getSpaceTaken());
        assertTrue(caravel.canAdd(w1));
        assertEquals(Unit.MoveType.EMBARK,
            w1.getMoveType(land.getDirection(sea)));
        w1.setLocation(caravel);
        w2.setLocation(caravel);
        assertFalse("a caravel takes two", caravel.canAdd(w3));
        // Aboard, a wagon stays empty.
        assertEquals(0, w1.getLoadableAmount(furs));
        assertFalse(w1.canAdd(new Goods(game, null, furs, 100)));
        // A galleon takes six.
        final Unit galleon = new ServerUnit(game, sea, dutch, galleonType);
        int n = 0;
        while (galleon.canAdd(w3)) {
            new ServerUnit(game, galleon, dutch, wagonType);
            n++;
        }
        assertEquals(6, n);
    }

    public void testLoadedWagonDoesNotFit() {
        setUpGame();
        final Unit galleon = new ServerUnit(game, sea, dutch, galleonType);
        final Unit w = new ServerUnit(game, land, dutch, wagonType);
        w.addGoods(furs, 100);
        assertEquals(wagonType.getSpaceTaken(), w.getSpaceTaken());
        assertFalse(galleon.canAdd(w));
        assertEquals("on land it loads as before", 100, w.getLoadableAmount(furs));
    }

    public void testComputerWagonsStayAshore() {
        setUpGame();
        dutch.setAI(true);
        final Unit galleon = new ServerUnit(game, sea, dutch, galleonType);
        final Unit w = new ServerUnit(game, land, dutch, wagonType);
        assertFalse(galleon.canAdd(w));
    }
}
