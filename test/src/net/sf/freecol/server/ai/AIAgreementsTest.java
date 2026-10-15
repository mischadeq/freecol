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
import net.sf.freecol.common.diplomacy.PriceAgreement;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
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
 * Price agreements (mod, step 4): the player sells a good at a fixed
 * price for some turns, by ship, as much as the buyer has room for.
 */
public class AIAgreementsTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");
    private static final TileType ocean = spec().getTileType("model.tile.ocean");
    private static final GoodsType coats = spec().getGoodsType("model.goods.coats");
    private static final GoodsType cloth = spec().getGoodsType("model.goods.cloth");
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
        net.sf.freecol.util.test.FreeColTestCase.MapBuilder mb
            = new net.sf.freecol.util.test.FreeColTestCase.MapBuilder(getGame());
        mb.setDimensions(20, 20).setBaseTileType(ocean);
        for (int y = 2; y < 18; y++) {
            for (int x = 2; x <= 8; x++) mb.setTileType(x, y, plains);
        }
        mb.setTileType(19, 0, spec().getTileType("model.tile.highSeas"));
        game = ServerTestHelper.startServerGame(mb.build());
        dutch = getServerPlayer(game, DUTCH);
        english = getServerPlayer(game, ENGLISH);
        english.setAI(false);
        dutch.setStance(english, Stance.PEACE);
        english.setStance(dutch, Stance.PEACE);
        havana = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(game.getMap().getTile(8, 10)).initialColonists(3).build();
        FreeColTestUtils.getColonyBuilder().player(english)
            .colonyTile(game.getMap().getTile(2, 10)).initialColonists(2).build();
        ai = (EuropeanAIPlayer)ServerTestHelper.getServer().getAIMain()
            .getAIPlayer(dutch);
        dutch.setGold(10000);
    }

    private int net(GoodsType gt) {
        return PriceAgreement.net(dutch.getMarket().getPaidForSale(gt),
                                  dutch.getTax());
    }

    private Offer propose(GoodsType gt, int price, int turns) {
        Offer p = new Offer("new", Offer.Kind.AGREEMENT, ENGLISH, gt.getId(),
            0, price, null, "", game.getTurn().getNumber(), turns);
        p.audience.add(DUTCH);
        return new OfferPlanner(ai).considerAgreement(english, p, new ChangeSet());
    }

    private Offer open(Offer.Kind k) {
        for (Offer o : ai.getOffers()) {
            if (o.kind == k && o.state == Offer.State.OPEN) return o;
        }
        return null;
    }

    private void plan() {
        new OfferPlanner(ai).plan(ai.getOutlook(), new LogBuilder(64));
    }

    private void nextTurns(int n) {
        game.setTurn(new Turn(game.getTurn().getNumber() + n));
    }

    private Unit ship(GoodsType gt, int n) {
        Unit s = new ServerUnit(game, game.getMap().getTile(9, 10), english,
            spec().getUnitType("model.unit.merchantman"));
        s.addGoods(gt, n);
        return s;
    }

    public void testAcceptCounterRefuse() {
        setUpGame();
        final int net = net(coats);
        assertTrue("coats sell in Europe: " + net, net > 2);
        final Offer a = propose(coats, net / 2, 20);
        assertNotNull(a);
        assertEquals("a profit for them: agreed", Offer.State.TAKEN, a.state);
        assertEquals(ENGLISH, a.takenBy);
        assertEquals(20, a.deadline);
        assertNull("one per good", propose(coats, 1, 20));
        // Too dear: their own price.
        final Offer c = propose(cloth, 3 * net(cloth), 20);
        assertEquals(Offer.State.OPEN, c.state);
        assertEquals(PriceAgreement.counter(net(cloth)), c.gold);
        assertTrue(new OfferPlanner(ai).answer(english, c.id, true, new ChangeSet()));
        assertEquals(Offer.State.TAKEN, c.state);
        // Boycotted themselves: no.
        dutch.getMarket().setArrears(muskets, 500);
        assertNull(propose(muskets, 1, 20));
        // Length is clamped.
        assertEquals(PriceAgreement.MAX_TURNS, PriceAgreement.turns(99));
    }

    public void testNotWithEnemies() {
        setUpGame();
        dutch.setStance(english, Stance.WAR);
        english.setStance(dutch, Stance.WAR);
        assertNull(propose(coats, 1, 20));
    }

    public void testShipSellsHowManyThePlayerSays() {
        setUpGame();
        final Offer a = propose(coats, 5, 20);
        final Unit s = ship(coats, 150);
        final int stock = havana.getGoodsCount(coats);
        final int gold = english.getGold();
        plan();
        final Offer q = open(Offer.Kind.SALE);
        assertNotNull("asked how many", q);
        assertTrue(q.amount > 0);
        assertTrue(new OfferPlanner(ai).answer(english, q.id, true, 100,
                                               new ChangeSet()));
        assertEquals(50, s.getGoodsCount(coats));
        assertEquals(stock + 100, havana.getGoodsCount(coats));
        assertEquals(gold + 500, english.getGold());
        assertEquals(100, a.delivered);
        assertEquals(500, a.paid);
        boolean trade = false;
        for (Memory m : ai.getMemories().list()) {
            trade |= m.kind == Memory.Kind.TRADE && ENGLISH.equals(m.about);
        }
        assertTrue("trust grows with each sale", trade);
        // Not asked again at once; again after a while.
        plan();
        assertNull(open(Offer.Kind.SALE));
        nextTurns(2);
        plan();
        assertNull("its warehouse is full now", open(Offer.Kind.SALE));
        havana.removeGoods(coats); // shipped to Europe
        nextTurns(2);
        plan();
        assertNotNull(open(Offer.Kind.SALE));
    }

    public void testFullColonySaysNo() {
        setUpGame();
        propose(coats, 5, 20);
        havana.addGoods(coats, havana.getWarehouseCapacity());
        ship(coats, 100);
        plan();
        assertNull("no question when full", open(Offer.Kind.SALE));
        assertEquals(0, PriceAgreement.room(100, 100, 1000, 5));
        assertEquals(PriceAgreement.MAX_PER_VISIT, PriceAgreement.room(1000, 0, 100000, 5));
        assertEquals("what they can pay", 20, PriceAgreement.room(1000, 0, 100, 5));
    }

    public void testAgreementEnds() {
        setUpGame();
        final Offer a = propose(coats, 5, 6);
        nextTurns(6);
        plan();
        assertEquals(Offer.State.DONE, a.state);
        final Offer b = propose(cloth, 1, 20);
        dutch.setStance(english, Stance.WAR);
        english.setStance(dutch, Stance.WAR);
        plan();
        assertEquals(Offer.State.CANCELLED, b.state);
    }

    /** A fixed amount may come in parts. */
    public void testDeliveryInParts() {
        setUpGame();
        dutch.setGold(1000);
        final int now = game.getTurn().getNumber();
        Offer o = new Offer("offer:" + DUTCH + ":" + now, Offer.Kind.WANT,
            DUTCH, muskets.getId(), 100, 600, havana.getId(),
            havana.getName(), now, 8);
        o.audience.add(ENGLISH);
        ai.getOffers().add(o);
        assertTrue(new OfferPlanner(ai).answer(english, o.id, true, new ChangeSet()));
        final int gold = english.getGold();
        final Unit s = ship(muskets, 40);
        plan();
        assertEquals(Offer.State.TAKEN, o.state);
        assertEquals(40, o.delivered);
        assertEquals(gold + 240, english.getGold());
        s.addGoods(muskets, 60);
        nextTurns(1);
        plan();
        assertEquals(Offer.State.DONE, o.state);
        assertEquals(gold + 600, english.getGold());
    }

    public void testAgreementsAreSaved() throws Exception {
        setUpGame();
        final Offer a = propose(coats, 5, 20);
        a.delivered = 70;
        java.io.File file = java.io.File.createTempFile("agreement", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        EuropeanAIPlayer d2 = (EuropeanAIPlayer)loaded.getAIMain()
            .getAIPlayer(loaded.getGame().getPlayerByNationId(DUTCH));
        boolean found = false;
        for (Offer o : d2.getOffers()) {
            found |= o.id.equals(a.id) && o.delivered == 70
                && o.state == Offer.State.TAKEN;
        }
        assertTrue(found);
    }

    /** The treasury: kept back from shopping, grows with promises. */
    public void testTreasury() {
        setUpGame();
        final int base = ai.reserve();
        assertTrue(base > 0);
        assertEquals(0, ai.promisedGold());
        final Offer a = propose(coats, 5, 20);
        assertEquals(net.sf.freecol.common.diplomacy.Treasury.forAgreement(5),
                     ai.promisedGold());
        assertEquals(base + ai.promisedGold(), ai.reserve());
        // Shopping in Europe leaves the reserve.
        dutch.setGold(ai.reserve() + 50);
        ai.buyUnitsInEurope(new LogBuilder(64));
        assertTrue("kept " + dutch.getGold(), dutch.getGold() >= ai.reserve());
        // Losing a war: only the promises are kept.
        dutch.setStance(english, Stance.WAR);
        english.setStance(dutch, Stance.WAR);
        ai.getMemories().add(new Memory(Memory.Kind.COLONY_TAKEN, ENGLISH,
                                        game.getTurn().getNumber(), 1));
        assertTrue(ai.losingWar());
        assertEquals(ai.promisedGold(), ai.reserve());
    }
}
