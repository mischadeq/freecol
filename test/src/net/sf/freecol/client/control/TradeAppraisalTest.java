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

import net.sf.freecol.client.control.TradeAppraisal.Verdict;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsTradeItem;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.AIMain;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The price estimate in the trade dialog must agree with what the
 * computer players actually do.
 */
public class TradeAppraisalTest extends FreeColTestCase {

    private static final GoodsType coatsType
        = spec().getGoodsType("model.goods.coats");

    private Game game;
    private Player dutch, spain;
    private EuropeanAIPlayer spainAI;
    private Colony colony;


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    private void setUpGame() {
        game = ServerTestHelper.startServerGame(getTestMap(true));
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        colony = createStandardColony(1);
        dutch = colony.getOwner();
        spain = game.getPlayerByNationId("model.nation.spanish");
        spainAI = (EuropeanAIPlayer)aiMain.getAIPlayer(spain);
        spain.setGold(5000);
        spain.setTax(dutch.getTax());
        colony.addGoods(coatsType, 100);
    }

    /** 100 coats for some gold. */
    private DiplomaticTrade coatsFor(int gold) {
        List<TradeItem> items = new ArrayList<>();
        items.add(new GoodsTradeItem(game, dutch, spain,
                new Goods(game, colony, coatsType, 100)));
        if (gold > 0) items.add(new GoldTradeItem(game, spain, dutch, gold));
        return new DiplomaticTrade(game, TradeContext.TRADE, dutch, spain,
                                   items, 1);
    }

    private TradeAppraisal appraise(DiplomaticTrade dt) {
        // Each nation has its own market; the dialog uses ours as an
        // estimate, here we check the rules with theirs.
        return TradeAppraisal.appraise(dt, dutch, spain, spain.getMarket(),
            spain.getEurope(), spain.getTax(), -1, spain.getGold());
    }

    private static int goldIn(DiplomaticTrade dt) {
        for (TradeItem ti : dt.getItems()) {
            if (ti instanceof GoldTradeItem) return ti.getGold();
        }
        return 0;
    }

    public void testValueMatchesTheComputer() {
        setUpGame();
        final int value = appraise(coatsFor(0)).value;
        assertTrue(value > 0);
        assertEquals(Verdict.ASK, appraise(coatsFor(0)).verdict);

        // Asking exactly the value: accepted.
        DiplomaticTrade fair = coatsFor(value);
        assertEquals(Verdict.FAIR, appraise(fair).verdict);
        assertEquals(TradeStatus.ACCEPT_TRADE,
                     spainAI.acceptDiplomaticTrade(fair));

        // One more: no longer accepted outright.
        DiplomaticTrade over = coatsFor(value + 1);
        assertEquals(Verdict.HIGH_BALL, appraise(over).verdict);
        assertFalse(TradeStatus.ACCEPT_TRADE
                    == spainAI.acceptDiplomaticTrade(over));
    }

    public void testLowBall() {
        setUpGame();
        final int value = appraise(coatsFor(0)).value;
        DiplomaticTrade low = coatsFor(value / 2);
        assertEquals(Verdict.LOW_BALL, appraise(low).verdict);
        assertEquals(TradeStatus.ACCEPT_TRADE,
                     spainAI.acceptDiplomaticTrade(low));
    }

    public void testHighBallCounterOffer() {
        setUpGame();
        final int value = appraise(coatsFor(0)).value;
        DiplomaticTrade high = coatsFor(value + 300);
        TradeAppraisal ta = appraise(high);
        assertEquals(Verdict.HIGH_BALL, ta.verdict);
        assertEquals(TradeStatus.PROPOSE_TRADE,
                     spainAI.acceptDiplomaticTrade(high));
        assertEquals("The counter offer as predicted", ta.counter,
                     goldIn(high));
    }

    public void testMoreThanTheyHave() {
        setUpGame();
        spain.setGold(100);
        TradeAppraisal ta = appraise(coatsFor(500));
        assertEquals(Verdict.HIGH_BALL, ta.verdict);
        assertEquals(-1, ta.counter);
    }
}
