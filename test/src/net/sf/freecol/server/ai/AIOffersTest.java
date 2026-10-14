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
    private Colony havana, jamestown;


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
        mb.setTileType(19, 0, spec().getTileType("model.tile.highSeas"));
        game = ServerTestHelper.startServerGame(mb.build());
        dutch = getServerPlayer(game, DUTCH);
        english = getServerPlayer(game, ENGLISH);
        english.setAI(false);
        havana = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(game.getMap().getTile(8, 10)).initialColonists(3).build();
        jamestown = FreeColTestUtils.getColonyBuilder().player(english)
            .colonyTile(game.getMap().getTile(2, 10)).initialColonists(2).build();
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

    /** A computer ship sails to the player's colony and opens a trade. */
    public void testTradeVoyage() throws Exception {
        setUpGame();
        final GoodsType furs = spec().getGoodsType("model.goods.furs");
        Unit ship = new ServerUnit(game, havana.getTile(), dutch,
            spec().getUnitType("model.unit.merchantman"));
        OfferPlanner planner = new OfferPlanner(ai);
        assertTrue("empty, no job", planner.freeShip(ship));
        ship.addGoods(furs, 100);
        assertFalse("has cargo", planner.freeShip(ship));

        final int now = game.getTurn().getNumber();
        Offer o = new Offer("offer:ship", Offer.Kind.SHIP_SELL, DUTCH,
            furs.getId(), 100, 400, jamestown.getId(), jamestown.getName(),
            now, 0);
        o.audience.add(ENGLISH);
        ai.getOffers().add(o);
        assertFalse("answered in the trade screen", o.visibleTo(ENGLISH, now));
        AIUnit aiu = ServerTestHelper.getServer().getAIMain().getAIUnit(ship);
        net.sf.freecol.server.ai.mission.TradeVoyageMission m
            = new net.sf.freecol.server.ai.mission.TradeVoyageMission(
                ServerTestHelper.getServer().getAIMain(), aiu, jamestown, o.id);
        aiu.changeMission(m);
        assertNull(m.invalidReason());
        net.sf.freecol.common.model.Tile berth
            = net.sf.freecol.server.ai.mission.TradeVoyageMission.berth(jamestown, ship);
        assertNotNull(berth);
        assertFalse(berth.isLand());
        assertTrue(berth.isAdjacent(jamestown.getTile()));
        // The deal: the furs for the gold.
        net.sf.freecol.common.model.DiplomaticTrade dt = m.deal(o);
        assertNotNull(dt);
        assertEquals(2, dt.getItems().size());
        assertEquals(400, dt.getGoldGivenBy(english));

        // At the berth it opens the trade through the server (in this
        // test the "human" is answered by a computer helper in its own
        // thread, so the outcome itself is not checked here).
        ship.setLocation(berth);
        ship.setMovesLeft(ship.getInitialMovesLeft());
        game.setCurrentPlayer(dutch);
        m.doMission(new LogBuilder(64));
        assertEquals(Offer.State.DONE, o.state);

        // A new voyage of the same ship is kept in a save.
        o.state = Offer.State.OPEN;
        aiu.changeMission(new net.sf.freecol.server.ai.mission.TradeVoyageMission(
            ServerTestHelper.getServer().getAIMain(), aiu, jamestown, o.id));
        // The voyage is kept in a save.
        java.io.File file = java.io.File.createTempFile("voyage", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        AIUnit aiu2 = loaded.getAIMain().getAIUnit(
            loaded.getGame().getFreeColGameObject(ship.getId(), Unit.class));
        assertTrue(aiu2.getMission()
            instanceof net.sf.freecol.server.ai.mission.TradeVoyageMission);
        assertEquals(jamestown.getId(), aiu2.getMission().getTarget().getId());
    }

    /** The player posts an offer; a computer nation takes it and sails. */
    public void testPlayerPostsAnOffer() {
        setUpGame();
        assertTrue(jamestown.isConnectedPort());
        dutch.setStance(english, Stance.PEACE);
        english.setStance(dutch, Stance.PEACE);
        english.modifyGold(5000 - english.getGold());
        havana.addGoods(muskets, 200);
        Unit ship = new ServerUnit(game, havana.getTile(), dutch,
            spec().getUnitType("model.unit.merchantman"));
        // Bad posts are refused.
        Offer bad = new Offer("new", Offer.Kind.WANT, ENGLISH, muskets.getId(),
            100, 9000, jamestown.getId(), jamestown.getName(), 0, 0);
        assertNotNull("not enough gold", OfferPlanner.checkPosted(english, bad));

        Offer post = new Offer("new", Offer.Kind.WANT, ENGLISH, muskets.getId(),
            100, 3000, jamestown.getId(), jamestown.getName(), 0, 0);
        assertNull(OfferPlanner.checkPosted(english, post));
        ServerTestHelper.getServer().getInGameController().postOffer(english, post);
        Offer copy = null;
        for (Offer o : ai.getOffers()) if (ENGLISH.equals(o.from)) copy = o;
        assertNotNull("every computer nation gets a copy", copy);

        new OfferPlanner(ai).plan(ai.getOutlook(), new LogBuilder(64));
        assertEquals(Offer.State.TAKEN, copy.state);
        assertEquals(DUTCH, copy.takenBy);
        Offer trip = null;
        for (Offer o : ai.getOffers()) if (o.kind == Offer.Kind.SHIP_SELL) trip = o;
        assertNotNull("a ship is sent", trip);
        assertEquals(3000, trip.gold);
        assertEquals(100, ship.getGoodsCount(muskets));
        assertTrue(ServerTestHelper.getServer().getAIMain().getAIUnit(ship)
            .getMission() instanceof net.sf.freecol.server.ai.mission.TradeVoyageMission);
        // The French copy is taken too.
        EuropeanAIPlayer french = (EuropeanAIPlayer)ServerTestHelper.getServer()
            .getAIMain().getAIPlayer(game.getPlayerByNationId("model.nation.french"));
        for (Offer o : french.getOffers()) {
            if (o.id.equals(copy.id)) assertEquals(Offer.State.TAKEN, o.state);
        }
    }

    /** One offer per nation per five turns. */
    public void testOneOfferPerFiveTurns() {
        setUpGame();
        final int now = game.getTurn().getNumber();
        Offer old = new Offer("offer:old", Offer.Kind.SELL, DUTCH,
            muskets.getId(), 100, 300, havana.getId(), havana.getName(),
            now, 8);
        ai.getOffers().add(old);
        OfferPlanner planner = new OfferPlanner(ai);
        for (int t = 0; t < OfferPlanner.GAP; t++) {
            assertNull("turn " + t, planner.post(ai.getOutlook()));
            nextTurns(1);
        }
    }
}
