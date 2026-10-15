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

import net.sf.freecol.common.diplomacy.Grievance;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.diplomacy.AlliancePlanner;
import net.sf.freecol.server.ai.diplomacy.GrievancePlanner;
import net.sf.freecol.server.ai.diplomacy.OfferPlanner;
import net.sf.freecol.server.ai.diplomacy.TribePlanner;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * No war out of nowhere (mod, step 6): complaint, terms, war.
 */
public class AIGrievancesTest extends FreeColTestCase {

    private static final String DUTCH = "model.nation.dutch",
        FRENCH = "model.nation.french", SPANISH = "model.nation.spanish";

    private Game game;
    private ServerPlayer dutch, french, spanish;
    private AIMain aiMain;

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    private void setUpGame() {
        game = ServerTestHelper.startServerGame(getTestMap(true));
        aiMain = ServerTestHelper.getServer().getAIMain();
        dutch = getServerPlayer(game, DUTCH);
        french = getServerPlayer(game, FRENCH);
        spanish = getServerPlayer(game, SPANISH);
        FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(game.getMap().getTile(5, 5)).initialColonists(3).build();
        FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(game.getMap().getTile(12, 5)).initialColonists(3).build();
        FreeColTestUtils.getColonyBuilder().player(spanish)
            .colonyTile(game.getMap().getTile(12, 12)).initialColonists(3).build();
        for (Player p : new Player[] { dutch, french, spanish }) {
            for (Player q : new Player[] { dutch, french, spanish }) {
                if (p != q) p.setStance(q, Stance.PEACE);
            }
        }
        dutch.setAI(false);
    }

    private EuropeanAIPlayer ai(Player p) {
        return (EuropeanAIPlayer)aiMain.getAIPlayer(p);
    }

    private void turns(int n) {
        game.setTurn(new Turn(game.getTurn().getNumber() + n));
    }

    /** The French hate the Dutch player: by the old rule, war. */
    private void hate() {
        french.getTension(dutch).setValue(Tension.TENSION_MAX);
    }

    private Offer ultimatum(EuropeanAIPlayer a) {
        for (Offer o : a.getOffers()) {
            if (o.kind == Offer.Kind.ULTIMATUM && o.state == Offer.State.OPEN) return o;
        }
        return null;
    }

    public void testComplaintThenTermsThenWar() {
        setUpGame();
        hate();
        final EuropeanAIPlayer fai = ai(french);
        fai.determineStances(new LogBuilder(64));
        assertEquals("a complaint first", Stance.PEACE, french.getStance(dutch));
        Grievance g = fai.getGrievances().get(DUTCH);
        assertEquals(Grievance.Stage.COMPLAINT, g.stage);
        turns(Grievance.COMPLAINT_TURNS);
        fai.determineStances(new LogBuilder(64));
        assertEquals(Stance.PEACE, french.getStance(dutch));
        assertEquals(Grievance.Stage.ULTIMATUM, g.stage);
        assertNotNull(ultimatum(fai));
        // Ignored: war when the time is up.
        turns(Grievance.ULTIMATUM_TURNS);
        fai.determineStances(new LogBuilder(64));
        assertEquals(Stance.WAR, french.getStance(dutch));
    }

    public void testPayingSettlesIt() {
        setUpGame();
        hate();
        final EuropeanAIPlayer fai = ai(french);
        fai.determineStances(new LogBuilder(64));
        turns(Grievance.COMPLAINT_TURNS);
        fai.determineStances(new LogBuilder(64));
        final Offer o = ultimatum(fai);
        dutch.setGold(5000);
        final int frenchGold = french.getGold();
        assertTrue(new OfferPlanner(fai).answer(dutch, o.id, true, new ChangeSet()));
        assertEquals(5000 - o.gold, dutch.getGold());
        assertEquals(frenchGold + o.gold, french.getGold());
        assertEquals(Grievance.Stage.SETTLED, fai.getGrievances().get(DUTCH).stage);
        // Still angry, but the terms were met: no war for a while.
        turns(Grievance.ULTIMATUM_TURNS + 2);
        fai.determineStances(new LogBuilder(64));
        assertEquals(Stance.PEACE, french.getStance(dutch));
    }

    public void testRefusingMeansWar() {
        setUpGame();
        hate();
        final EuropeanAIPlayer fai = ai(french);
        fai.determineStances(new LogBuilder(64));
        turns(Grievance.COMPLAINT_TURNS);
        fai.determineStances(new LogBuilder(64));
        final Offer o = ultimatum(fai);
        assertTrue(new OfferPlanner(fai).answer(dutch, o.id, false, new ChangeSet()));
        turns(1);
        fai.determineStances(new LogBuilder(64));
        assertEquals(Stance.WAR, french.getStance(dutch));
    }

    public void testCause() {
        setUpGame();
        final GrievancePlanner gp = new GrievancePlanner(ai(french));
        assertEquals(Grievance.Cause.RIVALRY, gp.cause(dutch));
        new ServerUnit(game, game.getMap().getTile(13, 5), dutch,
            spec().getUnitType("model.unit.veteranSoldier"),
            spec().getRole("model.role.dragoon"));
        french.invalidateCanSeeTiles();
        assertEquals(Grievance.Cause.SOLDIERS, gp.cause(dutch));
    }

    public void testComputerPaysOrFights() {
        setUpGame();
        final EuropeanAIPlayer fai = ai(french);
        final GrievancePlanner gp = new GrievancePlanner(fai);
        assertFalse(gp.mayDeclareWar(spanish, new LogBuilder(64)));
        turns(Grievance.COMPLAINT_TURNS);
        // Rich and not stronger than the French: the Spanish pay.
        spanish.setGold(4000);
        assertFalse(gp.mayDeclareWar(spanish, new LogBuilder(64)));
        assertEquals(Grievance.Stage.SETTLED, fai.getGrievances().get(SPANISH).stage);
        assertEquals(3000, spanish.getGold());
        // Poor: they refuse, war.
        fai.getGrievances().clear();
        spanish.setGold(0);
        gp.mayDeclareWar(spanish, new LogBuilder(64));
        turns(Grievance.COMPLAINT_TURNS);
        assertTrue(gp.mayDeclareWar(spanish, new LogBuilder(64)));
    }

    public void testGrievancesAreSaved() throws Exception {
        setUpGame();
        hate();
        ai(french).determineStances(new LogBuilder(64));
        final String before = ai(french).getGrievances().get(DUTCH).serialize();
        java.io.File file = java.io.File.createTempFile("grievance", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        EuropeanAIPlayer f2 = (EuropeanAIPlayer)loaded.getAIMain()
            .getAIPlayer(loaded.getGame().getPlayerByNationId(FRENCH));
        assertEquals(before, f2.getGrievances().get(DUTCH).serialize());
    }
}
