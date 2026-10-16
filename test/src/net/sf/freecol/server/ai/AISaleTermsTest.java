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

package net.sf.freecol.server.ai;

import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.common.diplomacy.SaleTerms;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsTradeItem;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * A computer nation says what it sells at a colony and for how much,
 * and takes a deal made on those terms (mod).
 */
public class AISaleTermsTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");
    private static final TileType ocean = spec().getTileType("model.tile.ocean");
    private static final GoodsType cloth = spec().getGoodsType("model.goods.cloth");
    private static final GoodsType muskets = spec().getGoodsType("model.goods.muskets");

    private Game game;
    private ServerPlayer dutch, english;
    private EuropeanAIPlayer ai;
    private Colony havana;
    private Unit ship;


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** Dutch (computer) colony with cloth, an English ship next to it. */
    private void setUpGame() {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(20, 20).setBaseTileType(ocean);
        for (int y = 2; y < 18; y++) {
            for (int x = 2; x <= 8; x++) mb.setTileType(x, y, plains);
        }
        game = ServerTestHelper.startServerGame(mb.build());
        dutch = getServerPlayer(game, "model.nation.dutch");
        english = getServerPlayer(game, "model.nation.english");
        english.setAI(false);
        dutch.setStance(english, Stance.PEACE);
        english.setStance(dutch, Stance.PEACE);
        havana = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(game.getMap().getTile(8, 10)).initialColonists(3).build();
        havana.addGoods(cloth, 300);
        ship = new ServerUnit(game, game.getMap().getTile(9, 10), english,
            spec().getUnitType("model.unit.merchantman"));
        english.modifyGold(5000 - english.getGold());
        ai = (EuropeanAIPlayer)ServerTestHelper.getServer().getAIMain()
            .getAIPlayer(dutch);
    }

    private SaleTerms.Line line(GoodsType gt) {
        return SaleTerms.find(ai.saleTerms(havana, english), gt.getId());
    }

    private TradeStatus buy(GoodsType gt, int amount, int gold) {
        List<TradeItem> items = new ArrayList<>();
        items.add(new GoodsTradeItem(game, dutch, english,
            new Goods(game, havana, gt, amount)));
        items.add(new GoldTradeItem(game, english, dutch, gold));
        DiplomaticTrade dt = new DiplomaticTrade(game, TradeContext.TRADE,
                                                 english, dutch, items, 1);
        return ai.acceptDiplomaticTrade(dt);
    }

    /** Every storable goods has a line; amounts in tens, never the stock. */
    public void testTermsListEveryGoods() {
        setUpGame();
        List<SaleTerms.Line> lines = ai.saleTerms(havana, english);
        assertEquals(spec().getStorableGoodsTypeList().size(), lines.size());
        SaleTerms.Line c = line(cloth);
        assertEquals(SaleTerms.Status.FOR_SALE, c.status);
        assertTrue(c.max > 0 && c.max < 300);
        assertEquals(0, c.max % SaleTerms.STEP);
        assertEquals(SaleTerms.Status.OUT_OF_STOCK, line(muskets).status);
        assertTrue("someone else's colony: nothing",
                   ai.saleTerms(FreeColTestUtils.getColonyBuilder()
                       .player(english).colonyTile(game.getMap().getTile(3, 10))
                       .initialColonists(1).build(), english).isEmpty());
    }

    /** A deal on the terms is taken. */
    public void testDealOnTermsAccepted() {
        setUpGame();
        SaleTerms.Line c = line(cloth);
        assertEquals(TradeStatus.ACCEPT_TRADE,
                     buy(cloth, c.max, c.priceFor(c.max)));
    }

    /** Too little gold, or more than they let go: no deal. */
    public void testDealOffTermsRefused() {
        setUpGame();
        SaleTerms.Line c = line(cloth);
        assertFalse(TradeStatus.ACCEPT_TRADE
                    == buy(cloth, c.max, c.priceFor(c.max) / 2));
        assertFalse(TradeStatus.ACCEPT_TRADE
                    == buy(cloth, c.max + SaleTerms.STEP, 5000));
    }

    /** Disliked: nothing for sale, and no deal. */
    public void testDislikedBuyerGetsNothing() {
        setUpGame();
        dutch.getTension(english).setValue(
            net.sf.freecol.common.model.Tension.TENSION_MAX);
        assertEquals(SaleTerms.Status.NOT_FOR_SALE, line(cloth).status);
        assertFalse(TradeStatus.ACCEPT_TRADE == buy(cloth, 10, 5000));
    }
}
