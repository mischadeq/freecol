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
import net.sf.freecol.common.diplomacy.Commitments;
import net.sf.freecol.common.diplomacy.ScoredAction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * War and peace between computer nations come from the diplomat; with
 * the human player, the tribes and the King's forces the old rule stays.
 */
public class AIStancesTest extends FreeColTestCase {

    private static final String DUTCH = "model.nation.dutch",
        FRENCH = "model.nation.french", ENGLISH = "model.nation.english";


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    public void testWhoTheDiplomatDecidesFor() {
        Game game = ServerTestHelper.startServerGame(getTestMap(true));
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, DUTCH);
        ServerPlayer french = getServerPlayer(game, FRENCH);
        EuropeanAIPlayer ai = (EuropeanAIPlayer)aiMain.getAIPlayer(dutch);
        assertTrue(ai.usesDiplomat(french));
        for (Player tribe : game.getLiveNativePlayerList()) {
            assertFalse(ai.usesDiplomat(tribe));
        }
        french.setAI(false); // the human player
        assertFalse(ai.usesDiplomat(french));
    }

    /**
     * Anger alone is not enough for a war between computer nations (the
     * old rule went to war at 1010 tension); full hatred, with nothing
     * else to gain, just is: then the war is the diplomat's choice and
     * its reason is "hates them".
     */
    public void testWarNeedsTheDiplomatsWord() {
        Game game = ServerTestHelper.startServerGame(getTestMap(true));
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, DUTCH);
        ServerPlayer french = getServerPlayer(game, FRENCH);
        dutch.setStance(french, Stance.PEACE);
        french.setStance(dutch, Stance.PEACE);
        EuropeanAIPlayer ai = (EuropeanAIPlayer)aiMain.getAIPlayer(dutch);

        dutch.getTension(french).setValue(800); // angry
        assertFalse(ai.determineStances(new LogBuilder(64)));
        assertEquals(Stance.PEACE, dutch.getStance(french));
        assertNull(ai.getCommitments().get(FRENCH));

        dutch.getTension(french).setValue(Tension.TENSION_MAX);
        assertTrue(warScore(ai, FRENCH) >= 20);
        // A complaint first (step 6) ...
        assertFalse(ai.determineStances(new LogBuilder(64)));
        assertEquals(Stance.PEACE, dutch.getStance(french));
        ignoredTerms(ai, FRENCH);
        // ... war once the terms were ignored.
        assertTrue(ai.determineStances(new LogBuilder(64)));
        assertEquals(Stance.WAR, dutch.getStance(french));
        Commitments.Choice c = ai.getCommitments().get(FRENCH);
        assertEquals(Action.Kind.DECLARE_WAR, c.kind);
        assertEquals("hates them", c.why);
    }

    /** The terms of an ultimatum ran out (step 6). */
    private static void ignoredTerms(EuropeanAIPlayer ai, String id) {
        final net.sf.freecol.common.diplomacy.Grievance g = ai.getGrievances().get(id);
        g.stage = net.sf.freecol.common.diplomacy.Grievance.Stage.ULTIMATUM;
        g.since = ai.getGame().getTurn().getNumber()
            - net.sf.freecol.common.diplomacy.Grievance.ULTIMATUM_TURNS;
    }

    private static double warScore(EuropeanAIPlayer ai, String id) {
        for (ScoredAction sa : ai.getOutlook().ranked) {
            if (sa.action.kind == Action.Kind.DECLARE_WAR
                && id.equals(sa.action.target)) return sa.total;
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** Towards the human player the old tension rule still decides. */
    public void testOldRuleTowardsTheHuman() {
        Game game = ServerTestHelper.startServerGame(getTestMap(true));
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, DUTCH);
        ServerPlayer english = getServerPlayer(game, ENGLISH);
        english.setAI(false);
        dutch.setStance(english, Stance.PEACE);
        english.setStance(dutch, Stance.PEACE);
        dutch.getTension(english).setValue(Tension.TENSION_MAX);
        EuropeanAIPlayer ai = (EuropeanAIPlayer)aiMain.getAIPlayer(dutch);
        ai.determineStances(new LogBuilder(64));
        assertEquals("a complaint first", Stance.PEACE, dutch.getStance(english));
        ignoredTerms(ai, ENGLISH);
        ai.determineStances(new LogBuilder(64));
        assertEquals(Stance.WAR, dutch.getStance(english));
        assertNull(ai.getCommitments().get(ENGLISH));
    }

    public void testChoicesAreSaved() throws Exception {
        Game game = ServerTestHelper.startServerGame(getTestMap(true));
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer dutch = getServerPlayer(game, DUTCH);
        EuropeanAIPlayer ai = (EuropeanAIPlayer)aiMain.getAIPlayer(dutch);
        ScoredAction sa = new ScoredAction(Action.war(FRENCH));
        sa.total = 33;
        sa.land = 10;
        ai.getCommitments().chose(FRENCH, sa, 7);
        ai.getCommitments().shock(8);

        java.io.File file = java.io.File.createTempFile("stances", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        Game game2 = loaded.getGame();
        EuropeanAIPlayer ai2 = (EuropeanAIPlayer)loaded.getAIMain()
            .getAIPlayer(game2.getPlayerByNationId(DUTCH));
        Commitments.Choice c = ai2.getCommitments().get(FRENCH);
        assertNotNull(c);
        assertEquals(Action.Kind.DECLARE_WAR, c.kind);
        assertEquals(7, c.turn);
        assertEquals("wants their colonies", c.why);
        assertEquals(8, ai2.getCommitments().lastShock());
    }
}
