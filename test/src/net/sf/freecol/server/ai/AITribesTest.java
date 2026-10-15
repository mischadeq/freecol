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
        for (int i = 0; i < 4; i++) {
            final IndianSettlement si = sioux.getIndianSettlementList().get(0);
            si.addOwnedUnit(new net.sf.freecol.server.model.ServerUnit(game, si,
                sioux, spec().getDefaultUnitType(sioux)));
        }
    }

    /** A Dutch dragoon at war with the Tupi, next to their capital. */
    void threatenTupiCapital() {
        dutch.setStance(tupi, Stance.WAR);
        tupi.setStance(dutch, Stance.WAR);
        new net.sf.freecol.server.model.ServerUnit(game,
            game.getMap().getTile(4, 5), dutch,
            spec().getUnitType("model.unit.veteranSoldier"),
            spec().getRole("model.role.dragoon"));
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

    public void testHelpScore() {
        final net.sf.freecol.common.diplomacy.TribeCharacter w
            = net.sf.freecol.common.diplomacy.TribeCharacter.WARLIKE;
        // A neutral tribe nearby, not fond of the attacker: helps.
        assertTrue(TribePlanner.helpScore(w, 0, 0, 0, 3) >= TribePlanner.HELP_LINE);
        // Likes the attacker, fears it, far away, or a rival: does not.
        assertTrue(TribePlanner.helpScore(w, 0, 100, 0, 3) < TribePlanner.HELP_LINE);
        assertTrue(TribePlanner.helpScore(w, 0, 0, 30, 3) < TribePlanner.HELP_LINE);
        assertTrue(TribePlanner.helpScore(w, 0, 0, 0, 20) < TribePlanner.HELP_LINE);
        assertTrue(TribePlanner.helpScore(w, -40, 0, 0, 3) < TribePlanner.HELP_LINE);
    }

    public void testFriendsComeToHelp() {
        setUpGame();
        threatenTupiCapital();
        assertEquals(1, TribePlanner.calls(game).size());
        // The Sioux dislike the Dutch a little: they come.
        sioux.getTension(dutch).setValue(600);
        plan(sioux);
        assertEquals(Stance.WAR, sioux.getStance(dutch));
        assertTrue(remembers(ai(tupi), Memory.Kind.CAME_TO_HELP, SIOUX));
        boolean party = false;
        for (net.sf.freecol.common.model.Unit u : sioux.getUnitSet()) {
            final AIUnit aiu = aiMain.getAIUnit(u);
            party |= aiu != null && aiu.getMission()
                instanceof net.sf.freecol.server.ai.mission.UnitSeekAndDestroyMission;
        }
        assertTrue("a war party is sent", party);
        boolean news = false;
        for (net.sf.freecol.server.model.TribeNews.News n
                 : net.sf.freecol.server.model.TribeNews.recent(game)) {
            news |= n.kind == net.sf.freecol.server.model.TribeNews.Kind.HELP
                && SIOUX.equals(n.tribe) && TUPI.equals(n.other);
        }
        assertTrue(news);
        // The rival Arawak do not help.
        plan(arawak);
        assertFalse(arawak.getStance(dutch) == Stance.WAR);
    }

    public void testTribesThatLikeTheAttackerStayHome() {
        setUpGame();
        threatenTupiCapital();
        plan(sioux); // no tension with the Dutch: they like them
        assertFalse(sioux.getStance(dutch) == Stance.WAR);
    }

    public void testRivalSidesWithTheAttacker() {
        setUpGame();
        threatenTupiCapital();
        plan(arawak);
        // Peaceful Arawak who loathe the Tupi but hold no grudge to
        // fight them over: they side with the Dutch.
        arawak.getTension(tupi).setValue(560);
        ai(arawak).getCallsHeard().clear();
        plan(arawak);
        assertEquals(Stance.WAR, arawak.getStance(tupi));
        assertFalse(arawak.getStance(dutch) == Stance.WAR);
    }

    public void testRefugeesFleeToTheirOwnTribe() {
        setUpGame();
        final IndianSettlement camp = tupi.getIndianSettlementList().get(1);
        final IndianSettlement capital = tupi.getIndianSettlementList().get(0);
        for (int i = 0; i < 2; i++) {
            camp.addOwnedUnit(new net.sf.freecol.server.model.ServerUnit(game,
                camp, tupi, spec().getDefaultUnitType(tupi)));
        }
        assertEquals(4, camp.getUnitCount());
        final int before = capital.getUnitCount();
        assertSame(capital, dutch.csRefugees(camp,
            new net.sf.freecol.common.networking.ChangeSet()));
        assertEquals(before + 2, capital.getUnitCount());
        assertEquals(2, camp.getUnitCount());
    }

    public void testLastSettlementFleesToTheBestFriend() {
        setUpGame();
        final IndianSettlement camp = arawak.getIndianSettlementList().get(0);
        arawak.getTension(tupi).setValue(500);
        final IndianSettlement refuge = sioux.getIndianSettlementList().get(0);
        final int before = refuge.getUnitCount();
        assertSame(refuge, dutch.csRefugees(camp,
            new net.sf.freecol.common.networking.ChangeSet()));
        assertEquals("one of two, now Sioux", before + 1, refuge.getUnitCount());
        // At war with everyone: nobody takes them in.
        arawak.setStance(sioux, Stance.WAR);
        arawak.setStance(tupi, Stance.WAR);
        assertNull(dutch.csRefugees(camp,
            new net.sf.freecol.common.networking.ChangeSet()));
    }

    /** A Dutch mission, happy, for some turns. */
    void mission(IndianSettlement is, Player p, int turns) {
        final net.sf.freecol.common.model.Unit m
            = new net.sf.freecol.server.model.ServerUnit(game, null, p,
                spec().getUnitType("model.unit.jesuitMissionary"),
                spec().getRole("model.role.missionary"));
        is.setMissionary(m);
        m.setLocation(is);
        is.setAlarm(p, new net.sf.freecol.common.model.Tension(0));
        ai((ServerPlayer)is.getOwner()).getMissionSince().put(is.getId(),
            p.getNationId() + ":" + (game.getTurn().getNumber() - turns));
    }

    net.sf.freecol.common.diplomacy.Offer offer(NativeAIPlayer a,
            net.sf.freecol.common.diplomacy.Offer.Kind k) {
        for (net.sf.freecol.common.diplomacy.Offer o : a.getOffers()) {
            if (o.kind == k) return o;
        }
        return null;
    }

    public void testSettlementBreaksOff() {
        setUpGame();
        dutch.setAI(false);
        final IndianSettlement camp = tupi.getIndianSettlementList().get(1);
        // A young mission: not yet.
        mission(camp, dutch, 5);
        plan(tupi);
        assertNull(offer(ai(tupi), net.sf.freecol.common.diplomacy.Offer.Kind.SETTLEMENT_JOINS));
        // Twenty turns, happy, and the tribe is weak (two settlements).
        mission(camp, dutch, 25);
        nextTurn();
        plan(tupi);
        final net.sf.freecol.common.diplomacy.Offer o = offer(ai(tupi),
            net.sf.freecol.common.diplomacy.Offer.Kind.SETTLEMENT_JOINS);
        assertNotNull(o);
        assertEquals(camp.getId(), o.place);
        assertTrue(new TribePlanner(ai(tupi)).answer(dutch, o.id, true,
            new net.sf.freecol.common.networking.ChangeSet()));
        assertSame(dutch, camp.getAlliedPlayer());
        assertSame("it stays native", tupi, camp.getOwner());
        // Its land is free for the ally only.
        net.sf.freecol.common.model.Tile land = null;
        for (net.sf.freecol.common.model.Tile t : camp.getOwnedTiles()) {
            if (!t.hasSettlement() && t.getOwner() == tupi) land = t;
        }
        assertNotNull(land);
        assertEquals(0, dutch.getLandPrice(land));
        assertTrue(french.getLandPrice(land) > 0);
        // Its braves do not attack the ally, even when the tribe is angry.
        tupi.getTension(dutch).setValue(1000);
        net.sf.freecol.common.model.Unit brave = camp.getUnitList().get(0);
        assertEquals("target-settlement-ally",
            net.sf.freecol.server.ai.mission.Mission.invalidAttackReason(
                aiMain.getAIUnit(brave), dutch));
        // The tribe resents the ally.
        tupi.getTension(dutch).setValue(500);
        nextTurn();
        plan(tupi);
        assertTrue(tupi.getTension(dutch).getValue() > 500);
    }

    public void testWholeTribeJoins() {
        setUpGame();
        dutch.setAI(false);
        final IndianSettlement camp = arawak.getIndianSettlementList().get(0);
        mission(camp, dutch, 1);
        arawak.setStance(dutch, Stance.PEACE);
        dutch.setStance(arawak, Stance.PEACE);
        plan(arawak);
        final net.sf.freecol.common.diplomacy.Offer o = offer(ai(arawak),
            net.sf.freecol.common.diplomacy.Offer.Kind.ALLIANCE);
        assertNotNull(o);
        assertTrue(new TribePlanner(ai(arawak)).answer(dutch, o.id, true,
            new net.sf.freecol.common.networking.ChangeSet()));
        assertEquals(Stance.ALLIANCE, arawak.getStance(dutch));
        assertEquals(Stance.ALLIANCE, dutch.getStance(arawak));
        // Our wars are theirs.
        dutch.setStance(french, Stance.WAR);
        french.setStance(dutch, Stance.WAR);
        nextTurn();
        plan(arawak);
        assertEquals(Stance.WAR, arawak.getStance(french));
        // A tribe that is not ours does not join.
        assertFalse(sioux.getStance(french) == Stance.WAR);
    }

    public void testAllyMustAnswerTheCall() {
        setUpGame();
        dutch.setAI(false);
        arawak.setStance(dutch, Stance.ALLIANCE);
        dutch.setStance(arawak, Stance.ALLIANCE);
        arawak.setStance(french, Stance.WAR);
        french.setStance(arawak, Stance.WAR);
        new net.sf.freecol.server.model.ServerUnit(game,
            game.getMap().getTile(8, 6), french,
            spec().getUnitType("model.unit.veteranSoldier"),
            spec().getRole("model.role.dragoon"));
        plan(arawak);
        final net.sf.freecol.common.diplomacy.Offer o = offer(ai(arawak),
            net.sf.freecol.common.diplomacy.Offer.Kind.CALL_TO_ARMS);
        assertNotNull(o);
        assertEquals(FRENCH, o.against);
        assertTrue(new TribePlanner(ai(arawak)).answer(dutch, o.id, false,
            new net.sf.freecol.common.networking.ChangeSet()));
        assertEquals(Stance.PEACE, arawak.getStance(dutch));
        assertTrue(remembers(ai(arawak), Memory.Kind.DEAL_BROKEN, DUTCH));
    }

    public void testTlaxcala() {
        setUpGame();
        plan(tupi);
        tupi.setStance(arawak, Stance.WAR);
        arawak.setStance(tupi, Stance.WAR);
        dutch.setStance(arawak, Stance.WAR);
        arawak.setStance(dutch, Stance.WAR);
        tupi.setStance(dutch, Stance.PEACE);
        dutch.setStance(tupi, Stance.PEACE);
        tupi.getTension(dutch).setValue(300);
        assertTrue("at war with our old rival too: allies",
                   new TribePlanner(ai(tupi)).joins(dutch));
        assertFalse(new TribePlanner(ai(tupi)).joins(french));
    }

    public void testAlliesAreSaved() throws Exception {
        setUpGame();
        dutch.setAI(false);
        final IndianSettlement camp = tupi.getIndianSettlementList().get(1);
        mission(camp, dutch, 25);
        nextTurn();
        plan(tupi);
        camp.setAlliedPlayer(french);
        java.io.File file = java.io.File.createTempFile("allies", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        final Game g = loaded.getGame();
        final IndianSettlement c2 = g.getFreeColGameObject(camp.getId(),
                                                           IndianSettlement.class);
        assertEquals(FRENCH, c2.getAlliedPlayer().getNationId());
        NativeAIPlayer t2 = (NativeAIPlayer)loaded.getAIMain()
            .getAIPlayer(g.getPlayerByNationId(TUPI));
        assertNotNull(offer(t2, net.sf.freecol.common.diplomacy.Offer.Kind.SETTLEMENT_JOINS));
        assertTrue(t2.getMissionSince().get(camp.getId()).startsWith(DUTCH + ":"));
    }

    /** Two tribes that hate the Dutch: at peace with them, angry. */
    void hateTheDutch(ServerPlayer... tribes) {
        for (ServerPlayer t : tribes) {
            t.setStance(dutch, Stance.PEACE);
            dutch.setStance(t, Stance.PEACE);
            t.getTension(dutch).setValue(1000);
        }
    }

    boolean news(net.sf.freecol.server.model.TribeNews.Kind k, String tribe) {
        for (net.sf.freecol.server.model.TribeNews.News n
                 : net.sf.freecol.server.model.TribeNews.recent(game)) {
            if (n.kind == k && tribe.equals(n.tribe)) return true;
        }
        return false;
    }

    public void testUnrestTurnsByDifficulty() {
        assertEquals(5, TribePlanner.unrestTurns("model.difficulty.medium"));
        assertEquals(3, TribePlanner.unrestTurns("model.difficulty.veryHard"));
        assertEquals(7, TribePlanner.unrestTurns("model.difficulty.veryEasy"));
    }

    public void testUnrestBeforeTheUprising() {
        setUpGame();
        dutch.setAI(false);
        hateTheDutch(tupi, sioux);
        plan(sioux);
        plan(tupi);
        assertTrue(ai(tupi).getUnrest().containsKey(DUTCH));
        assertTrue(news(net.sf.freecol.server.model.TribeNews.Kind.UNREST, TUPI));
        assertFalse("a warning first", tupi.getStance(dutch) == Stance.WAR);
        final int turns = TribePlanner.unrestTurns(null);
        for (int i = 1; i < turns; i++) {
            nextTurn();
            plan(tupi);
            assertFalse(tupi.getStance(dutch) == Stance.WAR);
        }
        nextTurn();
        plan(tupi);
        assertEquals(Stance.WAR, tupi.getStance(dutch));
        assertTrue(news(net.sf.freecol.server.model.TribeNews.Kind.UPRISING, TUPI));
        assertNotNull("the mood shows it", ai(tupi).getMood());
    }

    public void testOneTribeAloneDoesNotRise() {
        setUpGame();
        hateTheDutch(tupi);
        plan(tupi);
        assertFalse(ai(tupi).getUnrest().containsKey(DUTCH));
        // Nor with only its old rival.
        hateTheDutch(arawak);
        plan(tupi);
        assertFalse(ai(tupi).getUnrest().containsKey(DUTCH));
    }

    public void testUnrestCalms() {
        setUpGame();
        hateTheDutch(tupi, sioux);
        plan(tupi);
        assertTrue(ai(tupi).getUnrest().containsKey(DUTCH));
        tupi.getTension(dutch).setValue(100); // gifts
        nextTurn();
        plan(tupi);
        assertFalse(ai(tupi).getUnrest().containsKey(DUTCH));
    }

    public void testRivalRoseSoWeStayOut() {
        setUpGame();
        hateTheDutch(tupi, sioux);
        plan(tupi);
        net.sf.freecol.server.model.TribeNews.add(game,
            new net.sf.freecol.server.model.TribeNews.News(
                game.getTurn().getNumber(),
                net.sf.freecol.server.model.TribeNews.Kind.UPRISING,
                ARAWAK, null, DUTCH, ""));
        for (int i = 0; i < TribePlanner.unrestTurns(null); i++) nextTurn();
        plan(tupi);
        assertFalse(tupi.getStance(dutch) == Stance.WAR);
    }

    public void testPontiacTurnsOnTheWinner() {
        setUpGame();
        tupi.setStance(dutch, Stance.PEACE);
        dutch.setStance(tupi, Stance.PEACE);
        tupi.setStance(french, Stance.PEACE);
        french.setStance(tupi, Stance.PEACE);
        net.sf.freecol.common.model.Colony fort = FreeColTestUtils
            .getColonyBuilder().player(french)
            .colonyTile(game.getMap().getTile(3, 6)).initialColonists(1).build();
        plan(tupi);
        nextTurn();
        ((net.sf.freecol.server.model.ServerColony)fort).csChangeOwner(dutch,
            false, null, new net.sf.freecol.common.networking.ChangeSet());
        plan(tupi);
        assertEquals("our friend the French lost a colony near us",
                     Stance.WAR, tupi.getStance(dutch));
        assertEquals(Stance.PEACE, tupi.getStance(french));
    }

    public void testUnrestIsSaved() throws Exception {
        setUpGame();
        hateTheDutch(tupi, sioux);
        plan(tupi);
        java.io.File file = java.io.File.createTempFile("unrest", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        NativeAIPlayer t2 = (NativeAIPlayer)loaded.getAIMain()
            .getAIPlayer(loaded.getGame().getPlayerByNationId(TUPI));
        assertEquals(ai(tupi).getUnrest(), t2.getUnrest());
    }
}
