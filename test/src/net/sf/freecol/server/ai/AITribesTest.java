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
        tupi.getTension(sioux).setValue(900); // a grudge, not hatred
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

    public void testMissionTakesHalfTheWarShock() {
        setUpGame();
        final IndianSettlement capital = tupi.getIndianSettlementList().get(0);
        final IndianSettlement camp = tupi.getIndianSettlementList().get(1);
        mission(camp, dutch, 25);
        capital.setAlarm(dutch, new net.sf.freecol.common.model.Tension(0));
        capital.setContacted(dutch);
        camp.setContacted(dutch);
        tupi.setStance(dutch, Stance.PEACE);
        dutch.setStance(tupi, Stance.PEACE);
        tupi.csChangeStance(Stance.WAR, dutch, true,
            new net.sf.freecol.common.networking.ChangeSet());
        assertEquals(net.sf.freecol.common.model.Tension.WAR_MODIFIER,
                     capital.getAlarm(dutch).getValue());
        assertEquals("the mission village takes half",
                     net.sf.freecol.common.model.Tension.WAR_MODIFIER / 2,
                     camp.getAlarm(dutch).getValue());
        // Only the mission's own nation: a French war hits it fully.
        camp.setAlarm(french, new net.sf.freecol.common.model.Tension(0));
        camp.setContacted(french);
        tupi.setStance(french, Stance.PEACE);
        french.setStance(tupi, Stance.PEACE);
        tupi.csChangeStance(Stance.WAR, french, true,
            new net.sf.freecol.common.networking.ChangeSet());
        assertEquals(net.sf.freecol.common.model.Tension.WAR_MODIFIER,
                     camp.getAlarm(french).getValue());
    }

    public void testBreaksOffInWarWhenContent() {
        setUpGame();
        dutch.setAI(false);
        final IndianSettlement camp = tupi.getIndianSettlementList().get(1);
        mission(camp, dutch, 25);
        // Content but not happy, in peace: stays (the tribe is weak).
        camp.setAlarm(dutch, new net.sf.freecol.common.model.Tension(500));
        assertNull(new TribePlanner(ai(tupi)).breaksOffTo(camp));
        // At war: content is enough.
        tupi.setStance(dutch, Stance.WAR);
        dutch.setStance(tupi, Stance.WAR);
        assertSame(dutch, new TribePlanner(ai(tupi)).breaksOffTo(camp));
        nextTurn();
        plan(tupi);
        assertNotNull(offer(ai(tupi),
            net.sf.freecol.common.diplomacy.Offer.Kind.SETTLEMENT_JOINS));
        // Angry, even at war: no.
        camp.setAlarm(dutch, new net.sf.freecol.common.model.Tension(800));
        assertNull(new TribePlanner(ai(tupi)).breaksOffTo(camp));
        // A cease-fire is no peace: content is still enough.
        camp.setAlarm(dutch, new net.sf.freecol.common.model.Tension(500));
        tupi.setStance(dutch, Stance.CEASE_FIRE);
        dutch.setStance(tupi, Stance.CEASE_FIRE);
        assertSame(dutch, new TribePlanner(ai(tupi)).breaksOffTo(camp));
    }

    /**
     * Two mission villages in a war: one offer for both; accepted, they
     * found a tribe of their own (a spare nation, the player's name),
     * with their braves and land, allied to the player, at war with the
     * tribe they left (plans/breakaway-tribe.md, step 2).
     */
    public void testVillagesBreakAwayTogether() throws Exception {
        setUpGame();
        dutch.setAI(false);
        // A third Tupi village, so the tribe is not weak: the war counts.
        settlement(tupi, 9, 12, false);
        final IndianSettlement a = tupi.getIndianSettlementList().get(1);
        final IndianSettlement b = tupi.getIndianSettlementList().get(2);
        mission(a, dutch, 25);
        mission(b, dutch, 25);
        tupi.setStance(dutch, Stance.WAR);
        dutch.setStance(tupi, Stance.WAR);
        nextTurn();
        plan(tupi);
        assertNull("not one by one", offer(ai(tupi),
            net.sf.freecol.common.diplomacy.Offer.Kind.SETTLEMENT_JOINS));
        final net.sf.freecol.common.diplomacy.Offer o = offer(ai(tupi),
            net.sf.freecol.common.diplomacy.Offer.Kind.BREAKAWAY);
        assertNotNull(o);
        assertEquals(2, o.amount);
        final int braves = a.getOwnedUnitList().size()
            + b.getOwnedUnitList().size();
        assertTrue(braves > 0);

        assertTrue(new TribePlanner(ai(tupi)).answer(dutch, o.id, true,
            "Praying Tupi", new net.sf.freecol.common.networking.ChangeSet()));
        final Player tribe = a.getOwner();
        assertNotSame(tupi, tribe);
        assertSame(tribe, b.getOwner());
        assertTrue(tribe.getNation().isBreakaway());
        assertTrue(tribe.isIndian());
        assertEquals("Praying Tupi", net.sf.freecol.common.i18n.Messages
            .message(tribe.getNationLabel()));
        assertSame(tupi.getNationType(), tribe.getNationType());
        assertEquals(TUPI, tribe.getBreakawayFrom());
        assertEquals(DUTCH, tribe.getProtector());
        assertEquals(Stance.ALLIANCE, tribe.getStance(dutch));
        assertEquals(Stance.ALLIANCE, dutch.getStance(tribe));
        assertEquals(Stance.WAR, tribe.getStance(tupi));
        assertEquals(1, tupi.getIndianSettlementList().size());
        for (net.sf.freecol.common.model.Unit u : a.getOwnedUnitList()) {
            assertSame(tribe, u.getOwner());
        }
        assertEquals(braves, a.getOwnedUnitList().size()
            + b.getOwnedUnitList().size());
        for (net.sf.freecol.common.model.Tile t : a.getOwnedTiles()) {
            assertSame(tribe, t.getOwner());
        }
        assertSame("the mission stays", dutch, a.getMissionary().getOwner());
        // The old tribe's colour, lighter, with the Dutch as accent.
        assertFalse(tupi.getNationColor().equals(tribe.getNationColor()));
        assertEquals(dutch.getNationColor(), tribe.getAccentColor());
        // Its AI is a tribe's, with the old tribe as its rival.
        assertTrue(aiMain.getAIPlayer(tribe) instanceof NativeAIPlayer);
        assertEquals(TUPI, ai(tribe).getRival());

        // It takes on its ally's wars on its own turn.
        dutch.setStance(french, Stance.WAR);
        french.setStance(dutch, Stance.WAR);
        Player.makeContact(tribe, french);
        plan(tribe);
        assertEquals(Stance.WAR, tribe.getStance(french));

        // Saved and loaded: its name, parent and protector.
        java.io.File file = java.io.File.createTempFile("breakaway", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        final Player t2 = loaded.getGame().getPlayerByNationId(tribe.getNationId());
        assertEquals("Praying Tupi", t2.getIndependentNationName());
        assertEquals(TUPI, t2.getBreakawayFrom());
        assertEquals(DUTCH, t2.getProtector());
        assertEquals(tupi.getNationType().getId(), t2.getNationType().getId());
    }

    /** Tupi villages 1 and 2 (of 4) break away under the Dutch. */
    Player formBreakaway() {
        dutch.setAI(false);
        settlement(tupi, 9, 12, false);
        settlement(tupi, 12, 3, false);
        mission(tupi.getIndianSettlementList().get(1), dutch, 25);
        mission(tupi.getIndianSettlementList().get(2), dutch, 25);
        tupi.setStance(dutch, Stance.WAR);
        dutch.setStance(tupi, Stance.WAR);
        nextTurn();
        plan(tupi);
        final net.sf.freecol.common.diplomacy.Offer o = offer(ai(tupi),
            net.sf.freecol.common.diplomacy.Offer.Kind.BREAKAWAY);
        assertTrue(new TribePlanner(ai(tupi)).answer(dutch, o.id, true,
            "Praying Tupi", new net.sf.freecol.common.networking.ChangeSet()));
        return tupi.getIndianSettlementList().isEmpty() ? null
            : game.getPlayerByNationId(net.sf.freecol.common.model.Nation
                .BREAKAWAY_PREFIX + "1");
    }

    /** A later mission village asks to join the breakaway tribe. */
    public void testLaterVillageJoins() {
        setUpGame();
        final Player tribe = formBreakaway();
        assertEquals(2, tribe.getIndianSettlementList().size());
        assertEquals(2, tupi.getIndianSettlementList().size());
        final IndianSettlement late = tupi.getIndianSettlementList().get(1);
        mission(late, dutch, 25);
        nextTurn();
        plan(tupi);
        assertNull("not a second breakaway", offer(ai(tupi),
            net.sf.freecol.common.diplomacy.Offer.Kind.SETTLEMENT_JOINS));
        net.sf.freecol.common.diplomacy.Offer j = offer(ai(tupi),
            net.sf.freecol.common.diplomacy.Offer.Kind.JOIN_BREAKAWAY);
        assertNotNull(j);
        assertEquals(late.getId(), j.place);
        assertEquals(tribe.getNationId(), j.against);
        assertTrue(new TribePlanner(ai(tupi)).answer(dutch, j.id, true,
            new net.sf.freecol.common.networking.ChangeSet()));
        assertSame(tribe, late.getOwner());
        assertEquals(3, tribe.getIndianSettlementList().size());
        for (net.sf.freecol.common.model.Unit u : late.getOwnedUnitList()) {
            assertSame(tribe, u.getOwner());
        }
    }

    /**
     * Abandoned (no missions left, two calls for help ignored), the
     * breakaway tribe goes back with all its villages; one ignored call,
     * or a mission still there, is not enough.  An ignored call does not
     * end its alliance on the spot.
     */
    public void testAbandonedTribeRejoins() {
        setUpGame();
        final Player tribe = formBreakaway();
        final java.util.List<IndianSettlement> vs = tribe.getIndianSettlementList();
        // A call for help declined: still allied, counted.
        final net.sf.freecol.common.diplomacy.Offer call
            = new net.sf.freecol.common.diplomacy.Offer("tcall:test",
                net.sf.freecol.common.diplomacy.Offer.Kind.CALL_TO_ARMS,
                tribe.getNationId(), null, 0, 0, null, "", game.getTurn().getNumber(), 0);
        call.against = TUPI;
        call.audience.add(DUTCH);
        ai(tribe).getOffers().add(call);
        assertTrue(new TribePlanner(ai(tribe)).answer(dutch, call.id, false,
            new net.sf.freecol.common.networking.ChangeSet()));
        assertEquals(Stance.ALLIANCE, tribe.getStance(dutch));
        assertEquals(1, new TribePlanner(ai(tribe)).ignoredCalls(dutch));
        // The missions leave; one ignored call: it stays.
        for (IndianSettlement is : vs) is.setMissionary(null);
        plan(tribe);
        assertSame(tribe, vs.get(0).getOwner());
        // A second ignored call: it goes back.
        ai(tribe).getCallsHeard().put("ignored|" + DUTCH, 2);
        plan(tribe);
        assertSame(tupi, vs.get(0).getOwner());
        assertSame(tupi, vs.get(1).getOwner());
        assertEquals(4, tupi.getIndianSettlementList().size());
        assertEquals(0, tribe.getUnitCount());
        assertNull("no accents any more", tribe.getAccentColor());
        assertTrue(remembers(ai(tupi), Memory.Kind.DEAL_BROKEN, DUTCH));
    }

    /** A French colony near the breakaway villages, at war with the Dutch. */
    net.sf.freecol.common.model.Colony frenchColonyNearby() {
        dutch.setStance(french, Stance.WAR);
        french.setStance(dutch, Stance.WAR);
        return net.sf.freecol.util.test.FreeColTestUtils.getColonyBuilder()
            .player(french).colonyTile(game.getMap().getTile(14, 12)).build();
    }

    /** Two braves of a village out on the land next to it. */
    void bravesOut(Player tribe, IndianSettlement is, int x, int y) {
        for (int i = 0; i < 2; i++) {
            final net.sf.freecol.common.model.Unit u
                = new net.sf.freecol.server.model.ServerUnit(game,
                    game.getMap().getTile(x, y), tribe,
                    spec().getDefaultUnitType(tribe));
            u.changeHomeIndianSettlement(is);
        }
    }

    /** The breakaway tribe raids its protector's enemies near it. */
    public void testBreakawayRaids() {
        setUpGame();
        final Player tribe = formBreakaway();
        final net.sf.freecol.common.model.Colony c = frenchColonyNearby();
        Player.makeContact(tribe, french);
        final IndianSettlement v = tribe.getIndianSettlementList().get(1);
        bravesOut(tribe, v, v.getTile().getX() + 1, v.getTile().getY());
        nextTurn();
        plan(tribe);
        assertEquals("it took up its ally's war", Stance.WAR,
                     tribe.getStance(french));
        int raiders = 0;
        for (net.sf.freecol.common.model.Unit u : tribe.getUnitSet()) {
            final AIUnit au = aiMain.getAIUnit(u);
            if (au != null && au.getMission()
                instanceof net.sf.freecol.server.ai.mission.UnitSeekAndDestroyMission) {
                raiders++;
            }
        }
        assertTrue("raiders " + raiders, raiders > 0);
        assertTrue(raiders <= TribePlanner.RAID_PARTY);
        assertEquals(Integer.valueOf(game.getTurn().getNumber()),
                     ai(tribe).getCallsHeard().get("raid|last"));
        assertNotNull(c);
    }

    /** The protector orders a war party; only he may. */
    public void testRaidOrder() {
        setUpGame();
        final Player tribe = formBreakaway();
        final net.sf.freecol.common.model.Colony c = frenchColonyNearby();
        final IndianSettlement v = tribe.getIndianSettlementList().get(1);
        bravesOut(tribe, v, v.getTile().getX() + 1, v.getTile().getY());
        final net.sf.freecol.server.ai.diplomacy.TribeEnvoyDesk desk
            = new net.sf.freecol.server.ai.diplomacy.TribeEnvoyDesk(ai(tribe));
        // Not a colony of an enemy: no.
        assertEquals("envoy.error.enemy", desk.send(dutch,
            net.sf.freecol.common.diplomacy.Offer.Kind.ENVOY_RAID, 0,
            "colony:nonsense", new net.sf.freecol.common.networking.ChangeSet()));
        assertNull(desk.send(dutch,
            net.sf.freecol.common.diplomacy.Offer.Kind.ENVOY_RAID, 0, c.getId(),
            new net.sf.freecol.common.networking.ChangeSet()));
        nextTurn();
        desk.answerAll(new net.sf.freecol.common.networking.ChangeSet(),
                       new LogBuilder(64));
        net.sf.freecol.common.diplomacy.Offer o = null;
        for (net.sf.freecol.common.diplomacy.Offer x : ai(tribe).getOffers()) {
            if (x.kind == net.sf.freecol.common.diplomacy.Offer.Kind.ENVOY_RAID) o = x;
        }
        assertEquals(net.sf.freecol.common.diplomacy.Offer.State.DONE, o.state);
        assertEquals(Stance.WAR, tribe.getStance(french));
        int raiders = 0;
        for (net.sf.freecol.common.model.Unit u : tribe.getUnitSet()) {
            final AIUnit au = aiMain.getAIUnit(u);
            if (au != null && au.getMission()
                instanceof net.sf.freecol.server.ai.mission.UnitSeekAndDestroyMission) {
                raiders++;
            }
        }
        assertTrue(raiders > 0);
        // A tribe it does not protect: no order.
        assertNull(desk.raidTarget(french, c.getId()));
    }

    /** The protector sees what the breakaway tribe sees. */
    public void testProtectorSeesWhatTheTribeSees() {
        setUpGame();
        final boolean fog = spec().getBoolean(
            net.sf.freecol.common.option.GameOptions.FOG_OF_WAR);
        final boolean enhanced = spec().getBoolean(
            net.sf.freecol.common.option.GameOptions.ENHANCED_MISSIONARIES);
        spec().setBoolean(net.sf.freecol.common.option.GameOptions.FOG_OF_WAR,
                          Boolean.TRUE);
        spec().setBoolean(net.sf.freecol.common.option.GameOptions.ENHANCED_MISSIONARIES,
                          Boolean.FALSE);
        try {
            final Player tribe = formBreakaway();
            final IndianSettlement v = tribe.getIndianSettlementList().get(1);
            final net.sf.freecol.common.model.Tile far
                = game.getMap().getTile(15, 6); // the test map is 20 x 15
            bravesOut(tribe, v, far.getX(), far.getY());
            dutch.invalidateCanSeeTiles();
            assertTrue(dutch.canSee(v.getTile()));
            assertTrue(dutch.canSee(far));
            // A brave moves: the protector's sight follows.
            final net.sf.freecol.common.model.Tile further
                = game.getMap().getTile(19, 12);
            assertFalse(dutch.canSee(further));
            for (net.sf.freecol.common.model.Unit u : far.getUnitList()) {
                u.setLocation(further);
            }
            tribe.invalidateCanSeeTiles();
            assertTrue(dutch.canSee(further));
            // Not allied any more: nothing shared.
            dutch.setStance(tribe, Stance.PEACE);
            tribe.setStance(dutch, Stance.PEACE);
            dutch.invalidateCanSeeTiles();
            assertFalse(dutch.canSee(further));
        } finally {
            spec().setBoolean(net.sf.freecol.common.option.GameOptions.FOG_OF_WAR, fog);
            spec().setBoolean(net.sf.freecol.common.option.GameOptions.ENHANCED_MISSIONARIES,
                              enhanced);
        }
    }

    /** At most three breakaway tribes in a game; a new game has none. */
    public void testBreakawaySlots() {
        setUpGame();
        for (Player p : game.getLivePlayerList()) {
            assertFalse(p.getNation().isBreakaway());
        }
        assertNotNull(net.sf.freecol.server.model.Breakaway.freeSlot(game));
        int n = 0;
        for (net.sf.freecol.common.model.Nation x : spec().getNations()) {
            if (x.isBreakaway()) n++;
        }
        assertEquals(3, n);
        assertFalse(new net.sf.freecol.common.model.NationOptions(spec())
            .getNations().keySet().stream()
            .anyMatch(x -> x.isBreakaway()));
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

    /** A European pays a tribe to fight the tribe it is at war with. */
    public void testEuropeanPaysATribe() {
        setUpGame();
        final net.sf.freecol.server.control.InGameController igc
            = ServerTestHelper.getServer().getInGameController();
        plan(tupi, arawak, sioux);
        assertTrue("the old rival is cheaper",
            igc.tribeIncitePrice(tupi, arawak, 0) < igc.tribeIncitePrice(sioux, arawak, 0));
        for (Player t : new Player[] { tupi, sioux }) {
            dutch.setStance(t, Stance.PEACE);
            t.setStance(dutch, Stance.PEACE);
        }
        dutch.setStance(arawak, Stance.WAR);
        arawak.setStance(dutch, Stance.WAR);
        dutch.setGold(10000);
        final Player paid = new net.sf.freecol.server.ai.diplomacy.AlliancePlanner(
            (EuropeanAIPlayer)aiMain.getAIPlayer(dutch)).inciteTribes(new LogBuilder(64));
        assertSame(tupi, paid);
        assertTrue(dutch.getGold() < 10000);
        nextTurn();
        plan(tupi);
        assertEquals("paid hatred means war", Stance.WAR, tupi.getStance(arawak));
    }

    /** An angry tribe marches on its breakaway settlement. */
    public void testTribeTakesBackItsSettlement() {
        setUpGame();
        dutch.setAI(false);
        tupi.setStance(dutch, Stance.PEACE);
        dutch.setStance(tupi, Stance.PEACE);
        final IndianSettlement camp = tupi.getIndianSettlementList().get(1);
        camp.setAlliedPlayer(dutch);
        tupi.getTension(dutch).setValue(300);
        plan(tupi);
        assertNull("not angry enough yet", offer(ai(tupi),
            net.sf.freecol.common.diplomacy.Offer.Kind.DEFEND_SETTLEMENT));
        tupi.getTension(dutch).setValue(900);
        nextTurn();
        plan(tupi);
        net.sf.freecol.common.diplomacy.Offer o = offer(ai(tupi),
            net.sf.freecol.common.diplomacy.Offer.Kind.DEFEND_SETTLEMENT);
        assertNotNull(o);
        assertEquals(camp.getId(), o.place);
        // Not defended: it goes back.
        assertTrue(new TribePlanner(ai(tupi)).answer(dutch, o.id, false,
            new net.sf.freecol.common.networking.ChangeSet()));
        assertNull(camp.getAlliedPlayer());
        // Defended: war with the tribe, it stays ours.
        camp.setAlliedPlayer(dutch);
        tupi.getTension(dutch).setValue(900);
        ai(tupi).getOffers().clear();
        nextTurn();
        plan(tupi);
        o = offer(ai(tupi), net.sf.freecol.common.diplomacy.Offer.Kind.DEFEND_SETTLEMENT);
        assertTrue(new TribePlanner(ai(tupi)).answer(dutch, o.id, true,
            new net.sf.freecol.common.networking.ChangeSet()));
        assertSame(dutch, camp.getAlliedPlayer());
        assertEquals(Stance.WAR, tupi.getStance(dutch));
    }

    /** Ignored: the settlement goes back when the offer runs out. */
    public void testUndefendedSettlementGoesBack() {
        setUpGame();
        dutch.setAI(false);
        tupi.setStance(dutch, Stance.PEACE);
        dutch.setStance(tupi, Stance.PEACE);
        final IndianSettlement camp = tupi.getIndianSettlementList().get(1);
        camp.setAlliedPlayer(dutch);
        tupi.getTension(dutch).setValue(900);
        plan(tupi);
        assertNotNull(offer(ai(tupi),
            net.sf.freecol.common.diplomacy.Offer.Kind.DEFEND_SETTLEMENT));
        for (int i = 0; i < net.sf.freecol.common.diplomacy.Offer.PEACE_TURNS; i++) {
            nextTurn();
        }
        plan(tupi);
        assertNull(camp.getAlliedPlayer());
    }
}
