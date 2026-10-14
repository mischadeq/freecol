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

import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.diplomacy.OfferPlanner;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * Offers to the human player: peace by messenger, notices delivered by
 * ship, on time, late, too late; saved with the game.
 */
public class AIOffersTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");
    private static final TileType ocean = spec().getTileType("model.tile.ocean");
    private static final GoodsType muskets = spec().getGoodsType("model.goods.muskets");
    private static final String DUTCH = "model.nation.dutch",
        ENGLISH = "model.nation.english";

    private Game game;
    private ServerPlayer dutch, english;
    private EuropeanAIPlayer ai;
    private Colony havana;


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** Dutch (computer) colony on the coast, English human player. */
    private void setUpGame() {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(20, 20).setBaseTileType(ocean);
        for (int y = 2; y < 18; y++) {
            for (int x = 2; x <= 8; x++) mb.setTileType(x, y, plains);
        }
        game = ServerTestHelper.startServerGame(mb.build());
        dutch = getServerPlayer(game, DUTCH);
        english = getServerPlayer(game, ENGLISH);
        english.setAI(false);
        havana = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(game.getMap().getTile(8, 10)).initialColonists(3).build();
        FreeColTestUtils.getColonyBuilder().player(english)
            .colonyTile(game.getMap().getTile(3, 10)).initialColonists(2).build();
        ai = (EuropeanAIPlayer)ServerTestHelper.getServer().getAIMain()
            .getAIPlayer(dutch);
    }

    private Offer want(int deadline) {
        final int now = game.getTurn().getNumber();
        Offer o = new Offer("offer:" + DUTCH + ":" + now, Offer.Kind.WANT,
            DUTCH, muskets.getId(), 100, 600, havana.getId(),
            havana.getName(), now, deadline);
        o.audience.add(ENGLISH);
        ai.getOffers().add(o);
        return o;
    }

    private Unit shipNextToHavana(int musketsAboard) {
        Unit ship = new ServerUnit(game, game.getMap().getTile(9, 10), english,
            spec().getUnitType("model.unit.merchantman"));
        if (musketsAboard > 0) ship.addGoods(muskets, musketsAboard);
        return ship;
    }

    private void nextTurns(int n) {
        game.setTurn(new Turn(game.getTurn().getNumber() + n));
    }

    private boolean remembers(Memory.Kind k) {
        for (Memory m : ai.getMemories().list()) {
            if (m.kind == k && ENGLISH.equals(m.about)) return true;
        }
        return false;
    }

    public void testPeaceByMessenger() {
        setUpGame();
        dutch.setStance(english, Stance.WAR);
        english.setStance(dutch, Stance.WAR);
        dutch.modifyGold(1000 - dutch.getGold());
        final int englishGold = english.getGold();
        Offer o = new Offer("offer:peace", Offer.Kind.PEACE, DUTCH, null, 0,
            300, havana.getId(), havana.getName(), game.getTurn().getNumber(), 0);
        o.audience.add(ENGLISH);
        ai.getOffers().add(o);
        OfferPlanner planner = new OfferPlanner(ai);
        assertFalse("not someone else's", planner.answer(
            getServerPlayer(game, "model.nation.french"), o.id, true, new ChangeSet()));
        assertTrue(planner.answer(english, o.id, true, new ChangeSet()));
        assertEquals(Stance.PEACE, dutch.getStance(english));
        assertEquals(Stance.PEACE, english.getStance(dutch));
        assertEquals(700, dutch.getGold());
        assertEquals(englishGold + 300, english.getGold());
        assertEquals(Offer.State.DONE, o.state);
        assertFalse("answered once", planner.answer(english, o.id, true, new ChangeSet()));
    }

    public void testPeaceDeclinedIsRemembered() {
        setUpGame();
        dutch.setStance(english, Stance.WAR);
        english.setStance(dutch, Stance.WAR);
        Offer o = new Offer("offer:peace", Offer.Kind.PEACE, DUTCH, null, 0,
            0, null, "", game.getTurn().getNumber(), 0);
        o.audience.add(ENGLISH);
        ai.getOffers().add(o);
        assertTrue(new OfferPlanner(ai).answer(english, o.id, false, new ChangeSet()));
        assertEquals(Stance.WAR, dutch.getStance(english));
        assertEquals(Offer.State.DECLINED, o.state);
        assertTrue(remembers(Memory.Kind.REFUSED_OFFER));
    }

    public void testDeliveredOnTime() {
        setUpGame();
        dutch.modifyGold(1000 - dutch.getGold());
        final int englishGold = english.getGold();
        Offer o = want(8);
        OfferPlanner planner = new OfferPlanner(ai);
        assertTrue(planner.answer(english, o.id, true, new ChangeSet()));
        assertEquals(Offer.State.TAKEN, o.state);
        final int stock = havana.getGoodsCount(muskets);

        // Nothing yet: no ship.
        nextTurns(2);
        planner.plan(ai.getOutlook(), new LogBuilder(64));
        assertEquals(Offer.State.TAKEN, o.state);

        Unit ship = shipNextToHavana(100);
        planner.plan(ai.getOutlook(), new LogBuilder(64));
        assertEquals(Offer.State.DONE, o.state);
        assertEquals(0, ship.getGoodsCount(muskets));
        assertEquals(stock + 100, havana.getGoodsCount(muskets));
        assertEquals(400, dutch.getGold());
        assertEquals(englishGold + 600, english.getGold());
        assertTrue(remembers(Memory.Kind.DEAL_KEPT));
        assertTrue(remembers(Memory.Kind.TRADE));
    }

    public void testLateAndTooLate() {
        setUpGame();
        dutch.modifyGold(2000 - dutch.getGold());
        final int englishGold = english.getGold();
        Offer late = want(8);
        OfferPlanner planner = new OfferPlanner(ai);
        planner.answer(english, late.id, true, new ChangeSet());
        nextTurns(10); // two turns late
        shipNextToHavana(100);
        planner.plan(ai.getOutlook(), new LogBuilder(64));
        assertEquals(Offer.State.LATE, late.state);
        assertEquals(450, late.paid);
        assertEquals(englishGold + 450, english.getGold());
        assertTrue(remembers(Memory.Kind.DEAL_LATE));

        Offer never = want(8);
        planner.answer(english, never.id, true, new ChangeSet());
        nextTurns(13); // over by more than half the deadline
        planner.plan(ai.getOutlook(), new LogBuilder(64));
        assertEquals(Offer.State.FAILED, never.state);
        assertTrue(remembers(Memory.Kind.DEAL_BROKEN));
    }

    public void testFetchGoodsForSale() {
        setUpGame();
        havana.addGoods(muskets, 200);
        english.modifyGold(1000 - english.getGold());
        final int now = game.getTurn().getNumber();
        Offer o = new Offer("offer:sell", Offer.Kind.SELL, DUTCH,
            muskets.getId(), 100, 300, havana.getId(), havana.getName(),
            now, 8);
        o.audience.add(ENGLISH);
        ai.getOffers().add(o);
        OfferPlanner planner = new OfferPlanner(ai);
        planner.answer(english, o.id, true, new ChangeSet());
        Unit ship = shipNextToHavana(0);
        planner.plan(ai.getOutlook(), new LogBuilder(64));
        assertEquals(Offer.State.DONE, o.state);
        assertEquals(100, ship.getGoodsCount(muskets));
        assertEquals(700, english.getGold());
    }

    public void testOffersAreSavedAndToldOnce() throws Exception {
        setUpGame();
        Offer o = want(8);
        OfferPlanner planner = new OfferPlanner(ai);
        planner.plan(ai.getOutlook(), new LogBuilder(64));
        assertTrue("the English were told", o.told.contains(ENGLISH));
        // One offer per five turns: none new now.
        assertEquals(1, ai.getOffers().size());

        java.io.File file = java.io.File.createTempFile("offers", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        Game game2 = loaded.getGame();
        EuropeanAIPlayer ai2 = (EuropeanAIPlayer)loaded.getAIMain()
            .getAIPlayer(game2.getPlayerByNationId(DUTCH));
        assertEquals(1, ai2.getOffers().size());
        assertEquals(o.serialize(), ai2.getOffers().get(0).serialize());
    }

    /** The player's offers are judged by the diplomat. */
    public void testPlayersOfferJudgedByTheDiplomat() {
        setUpGame();
        dutch.setStance(english, Stance.PEACE);
        english.setStance(dutch, Stance.PEACE);
        dutch.getTension(english).setValue(net.sf.freecol.common.model.Tension.TENSION_MAX);
        // An alliance from a nation it hates: never.
        java.util.List<net.sf.freecol.common.model.TradeItem> items = new java.util.ArrayList<>();
        items.add(new net.sf.freecol.common.model.StanceTradeItem(game, english,
            dutch, Stance.ALLIANCE));
        net.sf.freecol.common.model.DiplomaticTrade dt
            = new net.sf.freecol.common.model.DiplomaticTrade(game,
                net.sf.freecol.common.model.DiplomaticTrade.TradeContext.DIPLOMATIC,
                english, dutch, items, 1);
        assertFalse(ai.acceptDiplomaticTrade(dt)
            == net.sf.freecol.common.model.DiplomaticTrade.TradeStatus.ACCEPT_TRADE);
    }
}
