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

import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.NationView;
import net.sf.freecol.common.diplomacy.Relations;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StanceTradeItem;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * A computer nation remembers, during a game, what the others did to
 * it, and saves it with the game.
 */
public class AIMemoriesTest extends FreeColTestCase {

    private static final UnitType veteran
        = spec().getUnitType("model.unit.veteranSoldier");
    private static final String DUTCH = "model.nation.dutch",
        FRENCH = "model.nation.french";


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    public void testLiveMemoriesTrustAndSaving() throws Exception {
        Game game = ServerTestHelper.startServerGame(getTestMap(true));
        Map map = game.getMap();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, DUTCH);
        ServerPlayer french = getServerPlayer(game, FRENCH);
        Colony home = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(5, 8)).initialColonists(3).build();
        FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(map.getTile(12, 8)).initialColonists(3).build();
        Unit soldier = new ServerUnit(game, home.getTile(), dutch, veteran,
            spec().getRole("model.role.soldier"));
        dutch.setStance(french, Stance.WAR);
        french.setStance(dutch, Stance.WAR);
        EuropeanAIPlayer ai = (EuropeanAIPlayer)aiMain.getAIPlayer(dutch);

        // The first turn only looks; the soldier dies; next turn it
        // remembers who killed it.
        ai.rememberTurn();
        assertEquals(0, ai.getMemories().size());
        final double before = ai.getOutlook().view.others.get(FRENCH).opinion;
        soldier.dispose();
        final int t = game.getTurn().getNumber();
        game.setTurn(new Turn(t + 1));
        ai.rememberTurn();
        assertEquals(1, ai.getMemories().size());
        Memory m = ai.getMemories().list().get(0);
        assertEquals(Memory.Kind.UNIT_LOST, m.kind);
        assertEquals(FRENCH, m.about);
        assertEquals(t, m.turn);
        // The planners see it: a worse opinion of the French.
        NationView fv = ai.getOutlook().view.others.get(FRENCH);
        assertTrue(fv.opinion < before);

        // Trust comes from memories too.
        assertEquals(Relations.DEFAULT_TRUST, fv.trust, 1e-9);
        ai.getMemories().add(new Memory(Memory.Kind.DEAL_BROKEN, FRENCH,
                                        t + 1, 1));
        assertTrue(ai.getOutlook().view.others.get(FRENCH).trust
                   < Relations.DEFAULT_TRUST);

        // Saved and loaded with the game.
        java.io.File file = java.io.File.createTempFile("memories", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        Game game2 = loaded.getGame();
        EuropeanAIPlayer ai2 = (EuropeanAIPlayer)loaded.getAIMain()
            .getAIPlayer(game2.getPlayerByNationId(DUTCH));
        assertEquals(ai.getMemories().list(), ai2.getMemories().list());
    }

    public void testTradesBecomeMemories() {
        Game game = ServerTestHelper.startServerGame(getTestMap(true));
        ServerPlayer dutch = getServerPlayer(game, DUTCH);
        ServerPlayer french = getServerPlayer(game, FRENCH);
        french.modifyGold(1000);
        dutch.modifyGold(1000);

        // French gold for nothing: a gift.
        DiplomaticTrade gift = trade(game, french, dutch,
            new GoldTradeItem(game, french, dutch, 100));
        gift.setStatus(TradeStatus.ACCEPT_TRADE);
        assertEquals(Memory.Kind.GIFT, EuropeanAIPlayer.tradeMemory(gift, dutch));
        assertNull(EuropeanAIPlayer.tradeMemory(gift, french)); // we gave

        // Gold both ways: a trade.
        DiplomaticTrade swap = trade(game, french, dutch,
            new GoldTradeItem(game, french, dutch, 100),
            new GoldTradeItem(game, dutch, french, 50));
        swap.setStatus(TradeStatus.ACCEPT_TRADE);
        assertEquals(Memory.Kind.TRADE, EuropeanAIPlayer.tradeMemory(swap, dutch));

        // Peace alone is no gift.
        DiplomaticTrade peace = trade(game, french, dutch,
            new StanceTradeItem(game, french, dutch, Stance.PEACE));
        peace.setStatus(TradeStatus.ACCEPT_TRADE);
        assertNull(EuropeanAIPlayer.tradeMemory(peace, dutch));

        // Our offer turned down; theirs turned down by us is nothing.
        swap.setStatus(TradeStatus.REJECT_TRADE);
        assertEquals(Memory.Kind.REFUSED_OFFER,
                     EuropeanAIPlayer.tradeMemory(swap, french));
        assertNull(EuropeanAIPlayer.tradeMemory(swap, dutch));

        // The AI player keeps it.
        EuropeanAIPlayer ai = (EuropeanAIPlayer)ServerTestHelper.getServer()
            .getAIMain().getAIPlayer(dutch);
        ai.tradeDone(gift);
        assertEquals(1, ai.getMemories().size());
        assertEquals(Memory.Kind.GIFT, ai.getMemories().list().get(0).kind);
    }

    private static DiplomaticTrade trade(Game game, ServerPlayer sender,
                                         ServerPlayer recipient,
                                         TradeItem... items) {
        List<TradeItem> l = new ArrayList<>();
        for (TradeItem ti : items) l.add(ti);
        return new DiplomaticTrade(game, TradeContext.DIPLOMATIC, sender,
                                   recipient, l, 1);
    }
}
