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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.NativeTrade;
import net.sf.freecol.common.model.NativeTrade.NativeTradeAction;
import net.sf.freecol.common.model.NativeTradeItem;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.NativeTradeSession;
import net.sf.freecol.server.model.ServerIndianSettlement;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Asking natives for a lower price when buying the only goods they
 * offer used to be rejected by the server with "Can not buy".
 */
public class NativeTradeHaggleTest extends FreeColTestCase {

    private static final GoodsType fursType
        = spec().getGoodsType("model.goods.furs");
    private static final UnitType wagonTrainType
        = spec().getUnitType("model.unit.wagonTrain");


    /** Collects the warnings that client errors produce. */
    private static final class Warnings extends Handler {
        final List<String> messages = new ArrayList<>();
        @Override
        public void publish(LogRecord r) {
            messages.add(r.getMessage());
        }
        @Override
        public void flush() {}
        @Override
        public void close() {}
    }

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    public void testHaggleForTheOnlyGoodsOnSale() {
        final Game game = ServerTestHelper.startServerGame(getTestMap());
        final InGameController igc = ServerTestHelper.getInGameController();
        final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        final ServerIndianSettlement camp = (ServerIndianSettlement)
            new FreeColTestCase.IndianSettlementBuilder(game).build();
        camp.setContacted(dutch);
        final Tile tile = camp.getTile().getNeighbourOrNull(Direction.N);
        final ServerUnit wagon = new ServerUnit(game, tile, dutch,
                                                wagonTrainType);

        // A session as after the natives priced their goods: one item,
        // 90 furs, for sale.
        NativeTrade nt = NativeTradeSession.openSession(
            new NativeTrade(wagon, camp));
        nt.getSettlementToUnit().clear();
        NativeTradeItem furs = new NativeTradeItem(game, camp.getOwner(),
            dutch, new Goods(game, camp, fursType, 90));
        furs.setPrice(120);
        nt.getSettlementToUnit().add(furs);
        assertTrue(nt.canBuy());

        // The player asks for a lower price: the client sends the item
        // with its price unset, which lands in the session object.
        furs.setPrice(NativeTradeItem.PRICE_UNSET);
        nt.setItem(furs);

        Warnings warnings = new Warnings();
        Logger logger = Logger.getLogger(Player.class.getName());
        logger.addHandler(warnings);
        try {
            igc.nativeTrade(dutch, NativeTradeAction.BUY, nt);
        } finally {
            logger.removeHandler(warnings);
        }
        for (String m : warnings.messages) {
            assertFalse("Haggle rejected: " + m,
                        m != null && m.startsWith("Can not buy"));
        }
    }
}
