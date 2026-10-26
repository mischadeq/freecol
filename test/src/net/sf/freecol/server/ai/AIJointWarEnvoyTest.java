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

import net.sf.freecol.common.diplomacy.Action;
import net.sf.freecol.common.diplomacy.Coalition;
import net.sf.freecol.common.diplomacy.Commitments;
import net.sf.freecol.common.diplomacy.EnvoyRules;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.diplomacy.TribeEnvoyRules;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.diplomacy.EnvoyDesk;
import net.sf.freecol.server.ai.diplomacy.OfferPlanner;
import net.sf.freecol.server.ai.diplomacy.TribeEnvoyDesk;
import net.sf.freecol.server.ai.diplomacy.TribePlanner;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Step 8.2: envoys asking a nation to join the player's war, or to
 * leave the coalition against him; a tribe asked to fight for him (for
 * a dear price, and only as his ally).
 */
public class AIJointWarEnvoyTest extends FreeColTestCase {

    private Game game;
    private ServerPlayer dutch, english, french;
    private EuropeanAIPlayer ai;

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** English (human) at war with France; the Dutch at peace with
        both. */
    private void setUpGame() {
        game = ServerTestHelper.startServerGame(getTestMap(true));
        dutch = getServerPlayer(game, "model.nation.dutch");
        english = getServerPlayer(game, "model.nation.english");
        french = getServerPlayer(game, "model.nation.french");
        english.setAI(false);
        stance(dutch, english, Stance.PEACE);
        stance(dutch, french, Stance.PEACE);
        stance(english, french, Stance.WAR);
        english.modifyGold(20000 - english.getGold());
        dutch.modifyGold(500 - dutch.getGold());
        ai = (EuropeanAIPlayer)ServerTestHelper.getServer().getAIMain()
            .getAIPlayer(dutch);
    }

    private static void stance(ServerPlayer a, ServerPlayer b, Stance s) {
        a.setStance(b, s);
        b.setStance(a, s);
    }

    private void nextTurn() {
        game.setTurn(new Turn(game.getTurn().getNumber() + 1));
    }

    private void dutchTurn() {
        new OfferPlanner(ai).plan(ai.getOutlook(), new LogBuilder(64));
    }

    private Offer find(EuropeanAIPlayer a, Offer.Kind kind) {
        for (Offer o : a.getOffers()) if (o.kind == kind) return o;
        return null;
    }

    /** Enough gold: they go to war, and hold it like their own. */
    public void testJoinWarForGold() {
        setUpGame();
        assertNull(new EnvoyDesk(ai).send(english, Offer.Kind.ENVOY_WAR,
            15000, french.getNationId(), new ChangeSet()));
        assertEquals(5000, english.getGold());
        assertEquals(french.getNationId(),
                     find(ai, Offer.Kind.ENVOY_WAR).against);
        dutchTurn();
        final Offer o = find(ai, Offer.Kind.ENVOY_WAR);
        assertEquals(Offer.State.DONE, o.state);
        assertEquals(Stance.WAR, dutch.getStance(french));
        final Commitments.Choice c = ai.getCommitments()
            .get(french.getNationId());
        assertNotNull("they hold the war", c);
        assertEquals(Action.Kind.DECLARE_WAR, c.kind);
    }

    /** No gold: their price, as terms; taking them starts the war. */
    public void testJoinWarTerms() {
        setUpGame();
        assertNull(new EnvoyDesk(ai).send(english, Offer.Kind.ENVOY_WAR,
            0, french.getNationId(), new ChangeSet()));
        dutchTurn();
        assertEquals("never a war for free", Stance.PEACE,
                     dutch.getStance(french));
        final Offer t = find(ai, Offer.Kind.TERMS);
        assertNotNull("their price", t);
        assertEquals(french.getNationId(), t.against);
        assertTrue(t.gold > 0);
        assertEquals(Stance.PEACE, dutch.getStance(french));
        english.modifyGold(t.gold + 100 - english.getGold());
        assertTrue(new OfferPlanner(ai).answer(english, t.id, true,
                                                new ChangeSet()));
        assertEquals(Offer.State.DONE, t.state);
        assertEquals(Stance.WAR, dutch.getStance(french));
        assertEquals(100, english.getGold());
    }

    /** Not against an ally, nor a nation the player is not fighting. */
    public void testJoinWarMisfits() {
        setUpGame();
        final EnvoyDesk desk = new EnvoyDesk(ai);
        assertEquals("envoy.error.enemy", desk.send(english,
            Offer.Kind.ENVOY_WAR, 0, dutch.getNationId(), new ChangeSet()));
        assertEquals("envoy.error.enemy", desk.send(english,
            Offer.Kind.ENVOY_WAR, 0, null, new ChangeSet()));
        stance(english, french, Stance.PEACE);
        assertEquals("envoy.error.enemy", desk.send(english,
            Offer.Kind.ENVOY_WAR, 0, french.getNationId(), new ChangeSet()));
        stance(english, french, Stance.WAR);
        stance(dutch, french, Stance.ALLIANCE);
        assertNull(desk.send(english, Offer.Kind.ENVOY_WAR, 1000,
                             french.getNationId(), new ChangeSet()));
        dutchTurn();
        assertEquals(Offer.State.DECLINED,
                     find(ai, Offer.Kind.ENVOY_WAR).state);
        assertEquals("gold back", 20000, english.getGold());
        assertEquals(Stance.ALLIANCE, dutch.getStance(french));
    }

    /** Buy a member out of the coalition against the player; the
        leader can not be bought. */
    public void testLeaveCoalition() {
        setUpGame();
        final EnvoyDesk desk = new EnvoyDesk(ai);
        assertEquals("envoy.error.coalition", desk.send(english,
            Offer.Kind.ENVOY_LEAVE, 0, null, new ChangeSet()));
        final EuropeanAIPlayer fai = (EuropeanAIPlayer)ServerTestHelper
            .getServer().getAIMain().getAIPlayer(french);
        final Coalition c = new Coalition(english.getNationId(),
                                          game.getTurn().getNumber());
        c.members.add(french.getNationId());
        c.members.add(dutch.getNationId());
        fai.getCoalitions().add(c);
        stance(dutch, french, Stance.ALLIANCE);
        stance(dutch, english, Stance.WAR);
        assertTrue(EnvoyDesk.status(english,
            java.util.Collections.singletonList(ai)).get(0).endsWith(":1"));
        assertEquals("envoy.error.leader", new EnvoyDesk(fai).send(english,
            Offer.Kind.ENVOY_LEAVE, 0, null, new ChangeSet()));

        assertNull(desk.send(english, Offer.Kind.ENVOY_LEAVE, 15000, null,
                             new ChangeSet()));
        dutchTurn();
        assertEquals(Offer.State.DONE, find(ai, Offer.Kind.ENVOY_LEAVE).state);
        assertFalse(c.members.contains(dutch.getNationId()));
        assertEquals(Stance.PEACE, dutch.getStance(english));
        assertEquals(Stance.PEACE, dutch.getStance(french));
    }

    /** A tribe: dear, and only as allies; an ally fights for free. */
    public void testTribeFightsForThePlayer() {
        setUpGame();
        final IndianSettlement camp
            = new FreeColTestCase.IndianSettlementBuilder(game)
            .initialBravesInCamp(4).build();
        final ServerPlayer tribe = (ServerPlayer)camp.getOwner();
        final NativeAIPlayer nai = (NativeAIPlayer)ServerTestHelper
            .getServer().getAIMain().getAIPlayer(tribe);
        stance(tribe, english, Stance.PEACE);
        stance(tribe, french, Stance.PEACE);
        tribe.getTension(english).setValue(0);
        tribe.getTension(french).setValue(0);
        assertTrue(TribePlanner.opinion(nai, english) >= 0);
        final TribeEnvoyDesk desk = new TribeEnvoyDesk(nai);
        assertNull(desk.send(english, Offer.Kind.ENVOY_WAR, 0,
                             french.getNationId(), new ChangeSet()));
        desk.answerAll(new ChangeSet(), new LogBuilder(0));
        Offer t = null;
        for (Offer o : nai.getOffers()) if (o.kind == Offer.Kind.TERMS) t = o;
        assertNotNull("their price", t);
        assertEquals(TribeEnvoyRules.ALLY, t.amount);
        assertEquals("4 braves against a nation", 4
            * TribeEnvoyRules.PER_BRAVE * TribeEnvoyRules.EUROPEAN, t.gold, 0.1);
        assertEquals(Stance.PEACE, tribe.getStance(french));
        assertTrue(new TribePlanner(nai).answer(english, t.id, true,
                                                 new ChangeSet()));
        assertEquals(Offer.State.DONE, t.state);
        assertEquals(Stance.ALLIANCE, tribe.getStance(english));
        assertEquals(Stance.WAR, tribe.getStance(french));
        assertEquals(20000 - t.gold, english.getGold());

        // Allies: the next war is free.
        final ServerPlayer spanish = getServerPlayer(game,
            "model.nation.spanish");
        stance(spanish, tribe, Stance.PEACE);
        stance(spanish, english, Stance.WAR);
        tribe.getTension(spanish).setValue(0);
        for (int i = 0; i < EnvoyRules.WAIT; i++) nextTurn();
        assertNull(desk.send(english, Offer.Kind.ENVOY_WAR, 0,
                             spanish.getNationId(), new ChangeSet()));
        desk.answerAll(new ChangeSet(), new LogBuilder(0));
        assertEquals(Stance.WAR, tribe.getStance(spanish));
    }
}
