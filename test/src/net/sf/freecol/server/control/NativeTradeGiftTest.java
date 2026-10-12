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

package net.sf.freecol.server.control;

import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.NativeTrade;
import net.sf.freecol.common.model.NativeTrade.NativeTradeAction;
import net.sf.freecol.common.model.NativeTradeItem;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.NativeTradeSession;
import net.sf.freecol.server.model.ServerIndianSettlement;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.server.model.Session;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * A gift to the natives may be part of the goods a unit carries.
 */
public class NativeTradeGiftTest extends FreeColTestCase {

    private static final GoodsType tradeGoodsType
        = spec().getGoodsType("model.goods.tradeGoods");
    private static final UnitType wagonTrainType
        = spec().getUnitType("model.unit.wagonTrain");


    private static void waitFor(java.util.function.BooleanSupplier done) {
        for (int i = 0; i < 400 && !done.getAsBoolean(); i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ie) {
                break;
            }
        }
    }

    @Override
    public void tearDown() throws Exception {
        Session.clearAll();
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    public void testGiftPartOfTheCargo() {
        final Game game = ServerTestHelper.startServerGame(getTestMap());
        final InGameController igc = ServerTestHelper.getInGameController();
        final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        final ServerIndianSettlement camp = (ServerIndianSettlement)
            new FreeColTestCase.IndianSettlementBuilder(game).build();
        camp.setContacted(dutch);
        final Tile tile = camp.getTile().getNeighbourOrNull(Direction.N);
        final ServerUnit wagon = new ServerUnit(game, tile, dutch,
                                                wagonTrainType);
        wagon.addGoods(tradeGoodsType, 100);
        camp.removeGoods(tradeGoodsType); // Room for the gift
        final int before = camp.getGoodsCount(tradeGoodsType);

        // A session as after the natives valued the cargo.
        NativeTrade nt = NativeTradeSession.openSession(
            new NativeTrade(wagon, camp));
        assertEquals(1, nt.getUnitToSettlement().size());
        NativeTradeItem whole = nt.getUnitToSettlement().get(0);
        whole.setPrice(200);
        assertTrue(nt.canGift());

        // The player gives 40 of the 100, and claims they are worth a lot.
        NativeTradeItem part = whole.withAmount(40);
        part.setPrice(5000);
        nt.setItem(part);
        igc.nativeTrade(dutch, NativeTradeAction.GIFT, nt);
        NativeTradeItem gift = nt.getItem();
        assertEquals(40, gift.getGoods().getAmount());
        assertEquals("Valued by the server, not the client", 80,
                     gift.getPrice());

        // The natives accept (their AI answers by itself).
        final NativeTrade session = Session.lookup(NativeTradeSession.class,
            wagon, camp).getNativeTrade();
        waitFor(() -> !session.getGift());
        assertEquals(60, wagon.getGoodsCount(tradeGoodsType));
        assertEquals(before + 40, camp.getGoodsCount(tradeGoodsType));
        assertFalse(session.canGift());
        assertEquals("The rest stays on offer", 1,
                     session.getUnitToSettlement().size());
        NativeTradeItem rest = session.getUnitToSettlement().get(0);
        assertEquals(60, rest.getGoods().getAmount());
        assertEquals(120, rest.getPrice());
    }

    public void testGiftAllOfTheCargo() {
        final Game game = ServerTestHelper.startServerGame(getTestMap());
        final InGameController igc = ServerTestHelper.getInGameController();
        final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        final ServerIndianSettlement camp = (ServerIndianSettlement)
            new FreeColTestCase.IndianSettlementBuilder(game).build();
        camp.setContacted(dutch);
        final Tile tile = camp.getTile().getNeighbourOrNull(Direction.N);
        final ServerUnit wagon = new ServerUnit(game, tile, dutch,
                                                wagonTrainType);
        wagon.addGoods(tradeGoodsType, 100);
        camp.removeGoods(tradeGoodsType); // Room for the gift

        NativeTrade nt = NativeTradeSession.openSession(
            new NativeTrade(wagon, camp));
        NativeTradeItem whole = nt.getUnitToSettlement().get(0);
        whole.setPrice(200);
        nt.setItem(whole);
        igc.nativeTrade(dutch, NativeTradeAction.GIFT, nt);
        final NativeTrade session = Session.lookup(NativeTradeSession.class,
            wagon, camp).getNativeTrade();
        waitFor(() -> !session.getGift());
        assertEquals(0, wagon.getGoodsCount(tradeGoodsType));
        assertTrue(session.getUnitToSettlement().isEmpty());
    }
}
