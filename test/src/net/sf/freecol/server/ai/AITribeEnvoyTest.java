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

import net.sf.freecol.common.diplomacy.EnvoyRules;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.diplomacy.TribeEnvoyRules;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.diplomacy.TribeEnvoyDesk;
import net.sf.freecol.server.model.LandClaims;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * Envoys to a tribe (step 8.3b): a losing tribe makes peace at once; a
 * strong one sets terms (gold, a colony on its land abandoned, soldiers
 * out of its land), and peace follows when they are met; a gift cools
 * its anger.
 */
public class AITribeEnvoyTest extends FreeColTestCase {

    private Game game;
    private ServerPlayer english, tribe;
    private NativeAIPlayer ai;
    private IndianSettlement camp;

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    private void setUpGame(int braves) {
        game = ServerTestHelper.startServerGame(getTestMap(true));
        english = getServerPlayer(game, "model.nation.english");
        english.setAI(false);
        camp = new FreeColTestCase.IndianSettlementBuilder(game)
            .initialBravesInCamp(braves).build();
        tribe = (ServerPlayer)camp.getOwner();
        tribe.setStance(english, Stance.WAR);
        english.setStance(tribe, Stance.WAR);
        tribe.getTension(english).setValue(900);
        english.modifyGold(2000 - english.getGold());
        ai = (NativeAIPlayer)ServerTestHelper.getServer().getAIMain()
            .getAIPlayer(tribe);
    }

    private Tile at(int distance) {
        for (Tile t : game.getMap().getTileList(x -> true)) {
            if (t.isLand() && t.getDistanceTo(camp.getTile()) == distance
                && t.getX() > 2 && t.getY() > 2
                && t.getX() < game.getMap().getWidth() - 3
                && t.getY() < game.getMap().getHeight() - 3) return t;
        }
        fail("no tile at " + distance);
        return null;
    }

    private Unit artillery(Tile t) {
        return new ServerUnit(game, t, english,
                              spec().getUnitType("model.unit.artillery"));
    }

    private void tribeTurn() {
        new TribeEnvoyDesk(ai).answerAll(new ChangeSet(), new LogBuilder(0));
    }

    private Offer terms() {
        for (Offer o : ai.getOffers()) {
            if (o.kind == Offer.Kind.TERMS) return o;
        }
        return null;
    }

    public void testLosingTribeTakesPeace() {
        setUpGame(1);
        for (int i = 0; i < 4; i++) artillery(at(3));
        assertNull(new TribeEnvoyDesk(ai).send(english,
            Offer.Kind.ENVOY_PEACE, 0, new ChangeSet()));
        tribeTurn();
        assertEquals(Stance.PEACE, tribe.getStance(english));
        assertTrue(tribe.getTension(english).getValue()
                   <= TribeEnvoyRules.FREE_TENSION);
        assertNull("no terms needed", terms());
    }

    public void testStrongTribeTermsColonyAndTroops() {
        setUpGame(8);
        // A colony on land taken from them, and a gun inside their land.
        final Colony c = FreeColTestUtils.getColonyBuilder().player(english)
            .colonyTile(at(3)).initialColonists(2).build();
        LandClaims.record(tribe, c.getTile(), english, true);
        final Tile inside = at(1);
        inside.changeOwnership(tribe, camp);
        final Unit gun = artillery(inside);
        assertNull(new TribeEnvoyDesk(ai).send(english,
            Offer.Kind.ENVOY_PEACE, 100, new ChangeSet()));
        assertEquals(1900, english.getGold());
        tribeTurn();
        assertEquals("gold back with the terms", 2000, english.getGold());
        final Offer t = terms();
        assertNotNull(t);
        assertTrue((t.amount & TribeEnvoyRules.COLONY) != 0);
        assertTrue((t.amount & TribeEnvoyRules.TROOPS) != 0);
        assertEquals(c.getName(), t.placeName);
        assertEquals(Stance.WAR, tribe.getStance(english));

        // Accept: pay, the colony goes, peace waits for the gun to leave.
        final Tile ct = c.getTile();
        assertTrue(new TribeEnvoyDesk(ai).answerTerms(t, english, true,
                                                      new ChangeSet()));
        assertEquals(2000 - t.gold, english.getGold());
        assertNull("the colony is disbanded", ct.getColony());
        assertEquals("the land is theirs again", tribe, ct.getOwner());
        assertFalse("its colonists stay, as free units",
                    ct.getUnitList().isEmpty());
        assertEquals(Offer.State.TAKEN, t.state);
        assertEquals(Stance.WAR, tribe.getStance(english));
        tribeTurn();
        assertEquals("the gun is still inside", Stance.WAR,
                     tribe.getStance(english));
        gun.setLocation(at(5));
        tribeTurn();
        assertEquals(Offer.State.DONE, t.state);
        assertEquals(Stance.PEACE, tribe.getStance(english));
    }

    public void testGiftCoolsAnger() {
        setUpGame(3);
        tribe.setStance(english, Stance.PEACE);
        english.setStance(tribe, Stance.PEACE);
        tribe.getTension(english).setValue(600);
        assertNull(new TribeEnvoyDesk(ai).send(english,
            Offer.Kind.ENVOY_GIFT, 300, new ChangeSet()));
        assertEquals("one envoy per 5 turns", "envoy.error.wait",
            new TribeEnvoyDesk(ai).send(english, Offer.Kind.ENVOY_GIFT,
                                        100, new ChangeSet()));
        tribeTurn();
        assertTrue(tribe.getTension(english).getValue()
                   <= 600 - EnvoyRules.giftTension(300));
        assertEquals("no alliances with tribes by envoy", "envoy.error.kind",
            new TribeEnvoyDesk(ai).send(english, Offer.Kind.ENVOY_ALLIANCE,
                                        0, new ChangeSet()));
    }
}
