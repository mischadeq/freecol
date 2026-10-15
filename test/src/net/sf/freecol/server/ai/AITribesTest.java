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
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.diplomacy.TribePlanner;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * Tribes think (mod, step 7): they know each other, have an old rival,
 * remember, and rarely fight each other.
 */
public class AITribesTest extends FreeColTestCase {

    static final String TUPI = "model.nation.tupi",
        ARAWAK = "model.nation.arawak", SIOUX = "model.nation.sioux",
        DUTCH = "model.nation.dutch", FRENCH = "model.nation.french";

    Game game;
    ServerPlayer tupi, arawak, sioux, dutch, french;
    AIMain aiMain;

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** Tupi and Arawak side by side, the Sioux far away. */
    void setUpGame() {
        game = ServerTestHelper.startServerGame(getTestMap(true));
        aiMain = ServerTestHelper.getServer().getAIMain();
        tupi = getServerPlayer(game, TUPI);
        arawak = getServerPlayer(game, ARAWAK);
        sioux = getServerPlayer(game, SIOUX);
        dutch = getServerPlayer(game, DUTCH);
        french = getServerPlayer(game, FRENCH);
        settlement(tupi, 5, 5, true);
        settlement(tupi, 5, 8, false);
        settlement(arawak, 7, 6, true);
        settlement(sioux, 17, 13, true);
    }

    IndianSettlement settlement(Player p, int x, int y, boolean capital) {
        return new FreeColTestCase.IndianSettlementBuilder(game).player(p)
            .settlementTile(game.getMap().getTile(x, y)).capital(capital)
            .initialBravesInCamp(2).build();
    }

    NativeAIPlayer ai(Player p) {
        return (NativeAIPlayer)aiMain.getAIPlayer(p);
    }

    void plan(Player... ps) {
        for (Player p : ps) new TribePlanner(ai(p)).plan(new LogBuilder(64));
    }

    void nextTurn() {
        game.setTurn(new Turn(game.getTurn().getNumber() + 1));
    }

    static boolean remembers(NativeAIPlayer a, Memory.Kind k, String about) {
        for (Memory m : a.getMemories().list()) {
            if (m.kind == k && about.equals(m.about)) return true;
        }
        return false;
    }

    public void testTribesKnowEachOther() {
        setUpGame();
        tupi.setStance(sioux, Stance.UNCONTACTED);
        sioux.setStance(tupi, Stance.UNCONTACTED);
        TribePlanner.meetTribes(game);
        for (Player a : game.getLiveNativePlayerList()) {
            for (Player b : game.getLiveNativePlayerList()) {
                if (a != b) assertEquals(a + "/" + b, Stance.PEACE, a.getStance(b));
            }
        }
    }

    public void testOldRivalIsTheNearest() {
        setUpGame();
        plan(tupi, arawak);
        assertEquals(ARAWAK, ai(tupi).getRival());
        assertEquals(TUPI, ai(arawak).getRival());
        assertTrue(TribePlanner.borders(tupi, arawak) >= 2);
        assertEquals(0, TribePlanner.borders(tupi, sioux));
        // The rival is liked less than a far-away tribe.
        assertTrue(TribePlanner.opinion(ai(tupi), arawak)
                   < TribePlanner.opinion(ai(tupi), sioux));
    }

    public void testSharedEnemyBringsThemTogether() {
        setUpGame();
        plan(tupi);
        final double before = TribePlanner.opinion(ai(tupi), arawak);
        tupi.getTension(dutch).setValue(900);
        arawak.getTension(dutch).setValue(900);
        assertEquals(1, TribePlanner.sharedEnemies(tupi, arawak));
        assertTrue(TribePlanner.opinion(ai(tupi), arawak) > before);
    }

    public void testWarOnlyOnTheRivalWithAGrudge() {
        setUpGame();
        plan(tupi);
        assertEquals("no grudge: peace", Stance.PEACE, tupi.getStance(arawak));
        // A deep grudge against both: only the old rival is attacked.
        tupi.getTension(arawak).setValue(1000);
        tupi.getTension(sioux).setValue(1000);
        nextTurn();
        plan(tupi);
        assertEquals(Stance.WAR, tupi.getStance(arawak));
        assertEquals(Stance.WAR, arawak.getStance(tupi));
        assertEquals(Stance.PEACE, tupi.getStance(sioux));
        assertTrue("the braves are angry enough to attack",
            arawak.getTension(tupi).getValue() >= 800);
        // Peace again after a while.
        tupi.getTension(arawak).setValue(300);
        for (int i = 0; i < 30; i++) nextTurn();
        plan(tupi);
        assertEquals(Stance.PEACE, tupi.getStance(arawak));
    }

    public void testRivalsRub() {
        setUpGame();
        plan(tupi);
        final int t = tupi.getTension(arawak).getValue();
        nextTurn();
        plan(tupi);
        assertTrue(tupi.getTension(arawak).getValue() > t);
        assertEquals(0, tupi.getTension(sioux).getValue());
    }

    public void testLandTakenIsRemembered() {
        setUpGame();
        plan(tupi);
        FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(game.getMap().getTile(4, 6)).initialColonists(1).build();
        FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(game.getMap().getTile(17, 2)).initialColonists(1).build();
        nextTurn();
        plan(tupi);
        assertTrue(remembers(ai(tupi), Memory.Kind.LAND_TAKEN, DUTCH));
        assertFalse(remembers(ai(tupi), Memory.Kind.LAND_TAKEN, FRENCH));
        // Only once.
        nextTurn();
        plan(tupi);
        int n = 0;
        for (Memory m : ai(tupi).getMemories().list()) {
            if (m.kind == Memory.Kind.LAND_TAKEN) n++;
        }
        assertEquals(1, n);
    }

    public void testSettlementDestroyedIsRemembered() {
        setUpGame();
        IndianSettlement camp = settlement(tupi, 9, 9, false);
        plan(tupi);
        nextTurn();
        dutch.setStance(tupi, Stance.WAR);
        tupi.setStance(dutch, Stance.WAR);
        final String id = camp.getId();
        tupi.csDisposeSettlement(camp, new net.sf.freecol.common.networking.ChangeSet());
        plan(tupi);
        // The turn's record, shared by all tribes, saw it go (who did
        // it comes from the history; TribeRelationsTest checks the
        // memory itself).
        final TribePlanner.TurnRecord r = TribePlanner.record(game);
        assertNotNull(r.before.places.get(id));
        assertNull(r.now.places.get(id));
        boolean seen = false;
        for (net.sf.freecol.common.diplomacy.Observed o : r.diff) {
            seen |= TUPI.equals(o.actor) || TUPI.equals(o.other);
        }
        assertTrue(seen);
    }

    public void testSavedAndLoaded() throws Exception {
        setUpGame();
        plan(tupi);
        ai(tupi).getMemories().add(new Memory(Memory.Kind.MISSION, DUTCH,
                                              game.getTurn().getNumber(), 1));
        java.io.File file = java.io.File.createTempFile("tribes", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        NativeAIPlayer t2 = (NativeAIPlayer)loaded.getAIMain()
            .getAIPlayer(loaded.getGame().getPlayerByNationId(TUPI));
        assertEquals(ARAWAK, t2.getRival());
        assertNotNull(t2.getNearColonies());
        assertTrue(remembers(t2, Memory.Kind.MISSION, DUTCH));
    }
}
