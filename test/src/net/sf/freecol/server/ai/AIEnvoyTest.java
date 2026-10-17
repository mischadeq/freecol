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

import java.util.List;

import net.sf.freecol.common.diplomacy.EnvoyRules;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.diplomacy.EnvoyDesk;
import net.sf.freecol.server.ai.diplomacy.OfferPlanner;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * The player's envoys (step 8): sent with gold held, one per nation
 * every 5 turns, answered on the nation's turn: a gift taken, peace
 * agreed or priced (terms the player can accept), gold back on a no.
 */
public class AIEnvoyTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");
    private static final TileType ocean = spec().getTileType("model.tile.ocean");

    private Game game;
    private ServerPlayer dutch, english;
    private EuropeanAIPlayer ai;

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** Dutch computer nation, English human player, at peace. */
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
        FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(game.getMap().getTile(8, 10)).initialColonists(3).build();
        FreeColTestUtils.getColonyBuilder().player(english)
            .colonyTile(game.getMap().getTile(2, 10)).initialColonists(2).build();
        english.modifyGold(1000 - english.getGold());
        dutch.modifyGold(500 - dutch.getGold());
        ai = (EuropeanAIPlayer)ServerTestHelper.getServer().getAIMain()
            .getAIPlayer(dutch);
    }

    private void nextTurn() {
        game.setTurn(new Turn(game.getTurn().getNumber() + 1));
    }

    private void dutchTurn() {
        new OfferPlanner(ai).plan(ai.getOutlook(), new LogBuilder(64));
    }

    private Offer envoy() {
        for (Offer o : ai.getOffers()) if (o.kind.envoy()) return o;
        return null;
    }

    public void testGiftRoundTrip() {
        setUpGame();
        dutch.getTension(english).setValue(500);
        EnvoyDesk desk = new EnvoyDesk(ai);
        assertNull(desk.send(english, Offer.Kind.ENVOY_GIFT, 200,
                             new ChangeSet()));
        assertEquals("the gold goes with the envoy", 800, english.getGold());
        assertEquals("one envoy per 5 turns", "envoy.error.wait",
                     desk.send(english, Offer.Kind.ENVOY_GIFT, 100,
                               new ChangeSet()));
        List<String> st = EnvoyDesk.status(english,
            java.util.Collections.singletonList(ai));
        final int now = game.getTurn().getNumber();
        assertEquals(dutch.getNationId() + ":5:" + (now + 5) + ":0",
                     st.get(0));

        dutchTurn();
        assertEquals(Offer.State.DONE, envoy().state);
        assertEquals(700, dutch.getGold());
        assertEquals(500 - EnvoyRules.giftTension(200),
                     dutch.getTension(english).getValue());
        boolean gift = false;
        for (Memory m : ai.getMemories().list()) {
            gift |= m.kind == Memory.Kind.GIFT
                && english.getNationId().equals(m.about);
        }
        assertTrue("they remember the gift", gift);
        // Answered once only.
        dutchTurn();
        assertEquals(700, dutch.getGold());

        for (int i = 0; i < EnvoyRules.WAIT; i++) nextTurn();
        assertEquals(dutch.getNationId() + ":0:" + (now + 5) + ":0",
            EnvoyDesk.status(english,
                java.util.Collections.singletonList(ai)).get(0));
        assertNull(desk.send(english, Offer.Kind.ENVOY_GIFT, 100,
                             new ChangeSet()));
    }

    public void testWrongProposalsRefusedAtOnce() {
        setUpGame();
        EnvoyDesk desk = new EnvoyDesk(ai);
        assertEquals("no peace while at peace", "envoy.error.fits",
            desk.send(english, Offer.Kind.ENVOY_PEACE, 0, new ChangeSet()));
        assertEquals("envoy.error.gold",
            desk.send(english, Offer.Kind.ENVOY_GIFT, 0, new ChangeSet()));
        assertEquals("envoy.error.gold",
            desk.send(english, Offer.Kind.ENVOY_GIFT, 5000, new ChangeSet()));
        assertEquals(1000, english.getGold());
        assertNull(envoy());
    }

    /** Peace: agreed, or priced, or refused; never gold lost. */
    public void testPeaceEnvoyAnswered() {
        setUpGame();
        dutch.csChangeStance(Stance.WAR, english, true, new ChangeSet());
        nextTurn();
        assertNull(new EnvoyDesk(ai).send(english, Offer.Kind.ENVOY_PEACE,
                                          300, new ChangeSet()));
        assertEquals(700, english.getGold());
        dutchTurn();
        final Offer o = envoy();
        assertTrue(o.state.over());
        if (o.state == Offer.State.DONE) {
            assertEquals(Stance.PEACE, dutch.getStance(english));
            assertEquals(800, dutch.getGold());
        } else {
            assertEquals("gold back on a no", 1000, english.getGold());
            assertEquals(Stance.WAR, dutch.getStance(english));
        }
    }

    /** Terms: the player pays, the deal is done. */
    public void testTermsAccepted() {
        setUpGame();
        dutch.csChangeStance(Stance.WAR, english, true, new ChangeSet());
        final int now = game.getTurn().getNumber();
        Offer t = new Offer("terms:test", Offer.Kind.TERMS,
            dutch.getNationId(), Offer.Kind.ENVOY_PEACE.name(), 0, 400,
            null, "", now, 0);
        t.audience.add(english.getNationId());
        ai.getOffers().add(t);
        assertTrue(new OfferPlanner(ai).answer(english, t.id, true,
                                                new ChangeSet()));
        assertEquals(Offer.State.DONE, t.state);
        assertEquals(Stance.PEACE, dutch.getStance(english));
        assertEquals(600, english.getGold());
        assertEquals(900, dutch.getGold());
    }

    /** Terms that no longer fit, or no gold: nothing happens. */
    public void testTermsNoLongerFit() {
        setUpGame();
        final int now = game.getTurn().getNumber();
        Offer t = new Offer("terms:test2", Offer.Kind.TERMS,
            dutch.getNationId(), Offer.Kind.ENVOY_PEACE.name(), 0, 400,
            null, "", now, 0);
        t.audience.add(english.getNationId());
        ai.getOffers().add(t);
        new OfferPlanner(ai).answer(english, t.id, true, new ChangeSet());
        assertEquals("at peace already", Offer.State.EXPIRED, t.state);
        assertEquals(1000, english.getGold());
    }
}
