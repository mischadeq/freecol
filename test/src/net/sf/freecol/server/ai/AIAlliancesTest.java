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
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.diplomacy.AlliancePlanner;
import net.sf.freecol.server.ai.diplomacy.OfferPlanner;
import net.sf.freecol.server.ai.mission.Mission;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.server.model.WarDeclarations;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * Alliances are defensive pacts: calls to arms, refusals, and the
 * King's wars between New World allies.
 */
public class AIAlliancesTest extends FreeColTestCase {

    private static final String DUTCH = "model.nation.dutch",
        FRENCH = "model.nation.french", ENGLISH = "model.nation.english",
        SPANISH = "model.nation.spanish";

    private Game game;
    private ServerPlayer dutch, french, english, spanish;
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
        english = getServerPlayer(game, ENGLISH);
        spanish = getServerPlayer(game, SPANISH);
        FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(game.getMap().getTile(5, 5)).initialColonists(3).build();
        FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(game.getMap().getTile(12, 5)).initialColonists(3).build();
        FreeColTestUtils.getColonyBuilder().player(english)
            .colonyTile(game.getMap().getTile(5, 12)).initialColonists(3).build();
        FreeColTestUtils.getColonyBuilder().player(spanish)
            .colonyTile(game.getMap().getTile(12, 12)).initialColonists(3).build();
        for (Player p : new Player[] { dutch, french, english, spanish }) {
            for (Player q : new Player[] { dutch, french, english, spanish }) {
                if (p != q) p.setStance(q, Stance.PEACE);
            }
        }
    }

    private static void ally(Player a, Player b) {
        a.setStance(b, Stance.ALLIANCE);
        b.setStance(a, Stance.ALLIANCE);
    }

    private EuropeanAIPlayer ai(Player p) {
        return (EuropeanAIPlayer)aiMain.getAIPlayer(p);
    }

    /** The turn turns: the AI notes what changed since its last look. */
    private void nextTurn(EuropeanAIPlayer... ais) {
        game.setTurn(new Turn(game.getTurn().getNumber() + 1));
        for (EuropeanAIPlayer a : ais) a.rememberTurn();
    }

    private static boolean remembers(EuropeanAIPlayer a, Memory.Kind k,
                                     String about) {
        for (Memory m : a.getMemories().list()) {
            if (m.kind == k && about.equals(m.about)) return true;
        }
        return false;
    }

    public void testTheKingsWarIsNoted() {
        setUpGame();
        WarDeclarations.asKing(() ->
            dutch.csChangeStance(Stance.WAR, french, true, new ChangeSet()));
        english.csChangeStance(Stance.WAR, spanish, true, new ChangeSet());
        boolean king = false, own = false;
        for (WarDeclarations.Declaration d : WarDeclarations.recent(game)) {
            if (d.declarer.equals(DUTCH) && d.target.equals(FRENCH)) king = d.byKing;
            if (d.declarer.equals(ENGLISH) && d.target.equals(SPANISH)) own = !d.byKing;
        }
        assertTrue(king);
        assertTrue(own);
    }

    /** A computer ally answers the call or leaves the alliance. */
    public void testComputerAllyAnswersTheCall() {
        setUpGame();
        ally(dutch, french);
        EuropeanAIPlayer dai = ai(dutch), fai = ai(french);
        dai.rememberTurn();
        fai.rememberTurn();
        english.csChangeStance(Stance.WAR, french, true, new ChangeSet());
        nextTurn(dai);
        assertTrue(new AlliancePlanner(dai).plan(dai.getOutlook(),
                                                 new LogBuilder(64)));
        if (dutch.getStance(english) == Stance.WAR) {
            assertEquals(Stance.ALLIANCE, dutch.getStance(french));
            assertTrue(remembers(fai, Memory.Kind.HELPED_IN_WAR, DUTCH));
        } else {
            assertEquals("refused: the pact is over", Stance.PEACE,
                         dutch.getStance(french));
            assertTrue(remembers(fai, Memory.Kind.DEAL_BROKEN, DUTCH));
        }
        // Answered once.
        assertFalse(new AlliancePlanner(dai).plan(dai.getOutlook(),
                                                  new LogBuilder(64)));
    }

    /** The human ally is called to arms, and answers in the game. */
    public void testHumanAllyIsCalledToArms() {
        setUpGame();
        english.setAI(false);
        ally(french, english);
        EuropeanAIPlayer fai = ai(french);
        fai.rememberTurn();
        spanish.csChangeStance(Stance.WAR, french, true, new ChangeSet());
        nextTurn(fai);
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        Offer call = null;
        for (Offer o : fai.getOffers()) {
            if (o.kind == Offer.Kind.CALL_TO_ARMS) call = o;
        }
        assertNotNull(call);
        assertEquals(SPANISH, call.against);
        assertTrue(call.visibleTo(ENGLISH, game.getTurn().getNumber()));

        // Accepting: war on Spain, and the French remember the help.
        assertTrue(new OfferPlanner(fai).answer(english, call.id, true,
                                                new ChangeSet()));
        assertEquals(Stance.WAR, english.getStance(spanish));
        assertTrue(remembers(fai, Memory.Kind.HELPED_IN_WAR, ENGLISH));
    }

    public void testRefusingTheCallEndsTheAlliance() {
        setUpGame();
        english.setAI(false);
        ally(french, english);
        EuropeanAIPlayer fai = ai(french);
        fai.rememberTurn();
        spanish.csChangeStance(Stance.WAR, french, true, new ChangeSet());
        nextTurn(fai);
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        Offer call = null;
        for (Offer o : fai.getOffers()) {
            if (o.kind == Offer.Kind.CALL_TO_ARMS) call = o;
        }
        assertTrue(new OfferPlanner(fai).answer(english, call.id, false,
                                                new ChangeSet()));
        assertEquals(Stance.PEACE, french.getStance(english));
        assertEquals(Stance.PEACE, english.getStance(spanish));
        assertTrue(remembers(fai, Memory.Kind.DEAL_BROKEN, ENGLISH));
    }

    /** Allied to both sides: stays out, peace with the attacker. */
    public void testAlliedToBothStaysOut() {
        setUpGame();
        ally(french, dutch);
        ally(french, english);
        EuropeanAIPlayer fai = ai(french);
        fai.rememberTurn();
        dutch.csChangeStance(Stance.WAR, english, true, new ChangeSet());
        nextTurn(fai);
        assertTrue(new AlliancePlanner(fai).plan(fai.getOutlook(),
                                                 new LogBuilder(64)));
        assertEquals(Stance.PEACE, french.getStance(dutch));
        assertEquals(Stance.ALLIANCE, french.getStance(english));
        assertFalse(remembers(fai, Memory.Kind.DEAL_BROKEN, ENGLISH));
    }

    /** An alliance with no reason left lapses; a young one holds. */
    public void testAllianceLapses() {
        setUpGame();
        ally(french, dutch);
        // The French have come to hate the Dutch.
        french.getTension(dutch).setValue(
            net.sf.freecol.common.model.Tension.TENSION_MAX);
        EuropeanAIPlayer fai = ai(french);
        fai.rememberTurn();
        nextTurn(fai);
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        assertEquals(Stance.PEACE, french.getStance(dutch));

        // Made this turn: it holds for a while.
        french.csChangeStance(Stance.ALLIANCE, dutch, true, new ChangeSet());
        nextTurn(fai);
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        assertEquals(Stance.ALLIANCE, french.getStance(dutch));
        for (int i = 0; i < AlliancePlanner.ALLIANCE_HOLD; i++) nextTurn(fai);
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        assertEquals(Stance.PEACE, french.getStance(dutch));
    }

    /** Not while fighting side by side. */
    public void testNoLapseInAWarTogether() {
        setUpGame();
        ally(french, dutch);
        french.getTension(dutch).setValue(
            net.sf.freecol.common.model.Tension.TENSION_MAX);
        french.setStance(spanish, Stance.WAR);
        spanish.setStance(french, Stance.WAR);
        dutch.setStance(spanish, Stance.WAR);
        spanish.setStance(dutch, Stance.WAR);
        EuropeanAIPlayer fai = ai(french);
        fai.rememberTurn();
        nextTurn(fai);
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        assertEquals(Stance.ALLIANCE, french.getStance(dutch));
    }

    /** A loyal ally keeps a local truce when the King orders war. */
    public void testLoyalAllyKeepsTheTruce() {
        setUpGame();
        english.setAI(false);
        ally(dutch, english);
        EuropeanAIPlayer dai = ai(dutch);
        dai.rememberTurn();
        WarDeclarations.asKing(() ->
            english.csChangeStance(Stance.WAR, dutch, true, new ChangeSet()));
        nextTurn(dai);
        new AlliancePlanner(dai).plan(dai.getOutlook(), new LogBuilder(64));
        assertTrue(dai.getCommitments().inTruce(ENGLISH));
        assertFalse("no betrayal: it was the King",
                    remembers(dai, Memory.Kind.DEAL_BROKEN, ENGLISH));
        // It offers peace, and its soldiers leave the English alone.
        boolean peace = false;
        for (Offer o : dai.getOffers()) {
            peace |= o.kind == Offer.Kind.PEACE && o.audience.contains(ENGLISH);
        }
        assertTrue(peace);
        Unit soldier = new ServerUnit(game, game.getMap().getTile(5, 5), dutch,
            spec().getUnitType("model.unit.veteranSoldier"),
            spec().getRole("model.role.soldier"));
        assertEquals("target-local-truce", Mission.invalidAttackReason(
            aiMain.getAIUnit(soldier), english));
    }

    /** A disloyal one takes the chance. */
    public void testDisloyalAllyFights() {
        setUpGame();
        english.setAI(false);
        ally(dutch, english);
        dutch.getTension(english).setValue(
            net.sf.freecol.common.model.Tension.TENSION_MAX);
        EuropeanAIPlayer dai = ai(dutch);
        dai.rememberTurn();
        WarDeclarations.asKing(() ->
            english.csChangeStance(Stance.WAR, dutch, true, new ChangeSet()));
        nextTurn(dai);
        new AlliancePlanner(dai).plan(dai.getOutlook(), new LogBuilder(64));
        assertFalse(dai.getCommitments().inTruce(ENGLISH));
    }

    /** Two loyal computer allies make peace again. */
    public void testTwoLoyalComputersMakePeace() {
        setUpGame();
        ally(dutch, french);
        EuropeanAIPlayer dai = ai(dutch), fai = ai(french);
        dai.rememberTurn();
        fai.rememberTurn();
        WarDeclarations.asKing(() ->
            french.csChangeStance(Stance.WAR, dutch, true, new ChangeSet()));
        nextTurn(dai, fai);
        new AlliancePlanner(dai).plan(dai.getOutlook(), new LogBuilder(64));
        assertEquals(Stance.WAR, dutch.getStance(french));
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        assertEquals("both loyal: peace", Stance.PEACE, dutch.getStance(french));
    }

    /** A joint war: the player joins for gold, and gets the spoils. */
    public void testJointWarGoldAndSpoils() {
        setUpGame();
        english.setAI(false);
        dutch.setStance(spanish, Stance.WAR);
        spanish.setStance(dutch, Stance.WAR);
        dutch.modifyGold(2000 - dutch.getGold());
        final int englishGold = english.getGold();
        EuropeanAIPlayer dai = ai(dutch);
        net.sf.freecol.common.model.Colony havana = spanish.getColonyList().get(0);
        Offer o = new Offer("joint:test", Offer.Kind.JOINT_WAR, DUTCH, null, 0,
            500, havana.getId(), havana.getName(), game.getTurn().getNumber(), 0);
        o.against = SPANISH;
        o.audience.add(ENGLISH);
        dai.getOffers().add(o);
        assertTrue(new OfferPlanner(dai).answer(english, o.id, true, new ChangeSet()));
        assertEquals(Stance.WAR, english.getStance(spanish));
        assertEquals(englishGold + 500, english.getGold());
        assertEquals(1500, dutch.getGold());
        assertEquals(Offer.State.TAKEN, o.state);
        // No separate peace for the Dutch while the English fight.
        assertTrue(AlliancePlanner.boundToFight(dai, SPANISH));

        // The Dutch take Havana: it goes to the English, as promised.
        ((net.sf.freecol.server.model.ServerColony)havana)
            .csChangeOwner(dutch, false, null, new ChangeSet());
        dai.rememberTurn();
        new AlliancePlanner(dai).plan(dai.getOutlook(), new LogBuilder(64));
        assertEquals(english, havana.getOwner());

        // The English make peace alone: a broken word.
        english.setStance(spanish, Stance.PEACE);
        spanish.setStance(english, Stance.PEACE);
        new AlliancePlanner(dai).plan(dai.getOutlook(), new LogBuilder(64));
        assertEquals(Offer.State.FAILED, o.state);
        assertTrue(remembers(dai, Memory.Kind.DEAL_BROKEN, ENGLISH));
        assertFalse(AlliancePlanner.boundToFight(dai, SPANISH));
    }

    /** A computer partner that accepts goes to war and is paid. */
    public void testComputerJoinsAJointWar() {
        setUpGame();
        dutch.setStance(spanish, Stance.WAR);
        spanish.setStance(dutch, Stance.WAR);
        dutch.modifyGold(1000 - dutch.getGold());
        final int frenchGold = french.getGold();
        EuropeanAIPlayer dai = ai(dutch);
        Offer o = new Offer("joint:test2", Offer.Kind.JOINT_WAR, DUTCH, null,
            0, 400, null, "", game.getTurn().getNumber(), 0);
        o.against = SPANISH;
        o.audience.add(FRENCH);
        dai.getOffers().add(o);
        new AlliancePlanner(dai).accept(o, french, spanish, new ChangeSet());
        assertEquals(Stance.WAR, french.getStance(spanish));
        assertEquals(frenchGold + 400, french.getGold());
        assertTrue(remembers(ai(french), Memory.Kind.GIFT, DUTCH));
    }

    /** Many soldiers for a nation, to make it the runaway leader. */
    private void army(Player p, int n) {
        final net.sf.freecol.common.model.Tile t = p.getColonyList().get(0).getTile();
        for (int i = 0; i < n; i++) {
            new ServerUnit(game, t, p, spec().getUnitType("model.unit.veteranSoldier"),
                           spec().getRole("model.role.dragoon"));
        }
    }

    /** A runaway leader faces a coalition; the player is invited. */
    public void testCoalitionAgainstTheLeader() {
        setUpGame();
        english.setAI(false);
        army(spanish, 30);
        EuropeanAIPlayer fai = ai(french);
        assertEquals("the French keep the balance",
            net.sf.freecol.common.diplomacy.NationCharacter.BALANCE_KEEPER,
            fai.getOutlook().view.character);
        assertEquals(SPANISH, net.sf.freecol.common.diplomacy.Threat
            .leaderToContain(fai.getOutlook().view));
        fai.rememberTurn();
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        assertEquals(1, fai.getCoalitions().size());
        net.sf.freecol.common.diplomacy.Coalition c = fai.getCoalitions().get(0);
        assertEquals(SPANISH, c.target);
        // The Dutch see the same leader and like the French: they join.
        assertTrue(c.members.contains(DUTCH));
        assertEquals(Stance.ALLIANCE, french.getStance(dutch));
        // The player is invited, accepts, and is allied with every member.
        Offer inv = null;
        for (Offer o : fai.getOffers()) {
            if (o.kind == Offer.Kind.COALITION) inv = o;
        }
        assertNotNull(inv);
        assertEquals(SPANISH, inv.against);
        assertTrue(new OfferPlanner(fai).answer(english, inv.id, true,
                                                new ChangeSet()));
        assertTrue(c.members.contains(ENGLISH));
        assertEquals(Stance.ALLIANCE, english.getStance(french));
        assertEquals(Stance.ALLIANCE, english.getStance(dutch));
        // Only one coalition against the same leader.
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        assertEquals(1, fai.getCoalitions().size());
    }

    /** At war with the target, the founder calls the members. */
    public void testCoalitionMembersAreCalled() {
        setUpGame();
        english.setAI(false);
        EuropeanAIPlayer fai = ai(french), dai = ai(dutch);
        net.sf.freecol.common.diplomacy.Coalition c
            = new net.sf.freecol.common.diplomacy.Coalition(SPANISH,
                game.getTurn().getNumber());
        c.members.add(FRENCH);
        c.members.add(DUTCH);
        c.members.add(ENGLISH);
        fai.getCoalitions().add(c);
        ally(french, dutch);
        ally(french, english);
        ally(dutch, english);
        army(spanish, 30); // still the leader: the coalition goes on
        french.setStance(spanish, Stance.WAR);
        spanish.setStance(french, Stance.WAR);
        fai.rememberTurn();
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        // The Dutch fight, or leave the coalition (and the alliance).
        if (dutch.getStance(spanish) == Stance.WAR) {
            assertTrue(c.members.contains(DUTCH));
        } else {
            assertFalse(c.members.contains(DUTCH));
            assertEquals(Stance.PEACE, french.getStance(dutch));
        }
        // The player gets a call to arms.
        boolean called = false;
        for (Offer o : fai.getOffers()) {
            called |= o.kind == Offer.Kind.CALL_TO_ARMS
                && o.audience.contains(ENGLISH) && SPANISH.equals(o.against);
        }
        assertTrue(called);
        // At war together: one colony, one strike turn, for the members.
        if (dutch.getStance(spanish) == Stance.WAR) {
            assertNotNull(c.jointTarget);
            assertEquals(spanish, game.getFreeColGameObject(c.jointTarget,
                net.sf.freecol.common.model.Colony.class).getOwner());
            assertTrue(c.strikeTurn >= game.getTurn().getNumber()
                       + net.sf.freecol.common.diplomacy.Coalition.MIN_MUSTER);
            assertSame(c, AlliancePlanner.jointPlanFor(dai, SPANISH));
        }
    }

    public void testCoalitionsAreSaved() throws Exception {
        setUpGame();
        EuropeanAIPlayer fai = ai(french);
        net.sf.freecol.common.diplomacy.Coalition c
            = new net.sf.freecol.common.diplomacy.Coalition(SPANISH, 3);
        c.members.add(FRENCH);
        c.members.add(DUTCH);
        fai.getCoalitions().add(c);
        java.io.File file = java.io.File.createTempFile("coalition", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        net.sf.freecol.server.FreeColServer loaded
            = ServerTestHelper.startServer(file, false, true);
        EuropeanAIPlayer fai2 = (EuropeanAIPlayer)loaded.getAIMain()
            .getAIPlayer(loaded.getGame().getPlayerByNationId(FRENCH));
        assertEquals(1, fai2.getCoalitions().size());
        assertEquals(c.serialize(), fai2.getCoalitions().get(0).serialize());
    }

    /** A coalition of the French and a member, against the Spanish. */
    private net.sf.freecol.common.diplomacy.Coalition coalition(
            EuropeanAIPlayer founder, ServerPlayer... members) {
        net.sf.freecol.common.diplomacy.Coalition c
            = new net.sf.freecol.common.diplomacy.Coalition(SPANISH,
                game.getTurn().getNumber());
        c.members.add(founder.getPlayer().getNationId());
        for (ServerPlayer m : members) {
            c.members.add(m.getNationId());
            ally(founder.getPlayer(), m);
        }
        founder.getCoalitions().add(c);
        return c;
    }

    /** The target buys a trader out: it takes the gold and leaves. */
    public void testTargetBuysOutATrader() {
        setUpGame();
        english.setAI(false);
        EuropeanAIPlayer fai = ai(french), dai = ai(dutch), sai = ai(spanish);
        assertEquals(net.sf.freecol.common.diplomacy.NationCharacter.TRADING_EMPIRE,
                     dai.getOutlook().view.character);
        net.sf.freecol.common.diplomacy.Coalition c = coalition(fai, dutch);
        final int price = dai.buyoutPrice(c);
        assertTrue(price >= net.sf.freecol.common.diplomacy.Coalition.MIN_PRICE);
        spanish.setGold(10 * price);
        final int dutchGold = dutch.getGold();
        sai.rememberTurn();
        new AlliancePlanner(sai).plan(sai.getOutlook(), new LogBuilder(64));
        assertFalse(c.members.contains(DUTCH));
        assertEquals(Stance.PEACE, french.getStance(dutch));
        assertEquals(dutchGold + price, dutch.getGold());
        assertEquals(9 * price, spanish.getGold());
        assertTrue(remembers(fai, Memory.Kind.DEAL_BROKEN, DUTCH));
        assertTrue(remembers(dai, Memory.Kind.GIFT, SPANISH));
        // Too poor: no offer.
        net.sf.freecol.common.diplomacy.Coalition c2 = coalition(fai, dutch);
        spanish.setGold(dai.buyoutPrice(c2));
        game.setTurn(new Turn(game.getTurn().getNumber() + OfferPlanner.GAP));
        new AlliancePlanner(sai).plan(sai.getOutlook(), new LogBuilder(64));
        assertTrue(c2.members.contains(DUTCH));
    }

    /** The price grows with how well the member gets on with the others. */
    public void testPriceGrowsWithSynergy() {
        setUpGame();
        EuropeanAIPlayer fai = ai(french), dai = ai(dutch);
        net.sf.freecol.common.diplomacy.Coalition c = coalition(fai, dutch);
        final int before = dai.buyoutPrice(c);
        for (int i = 0; i < 3; i++) {
            dai.getMemories().add(new Memory(Memory.Kind.HELPED_IN_WAR, FRENCH,
                                             game.getTurn().getNumber(), 1));
        }
        assertTrue(dai.buyoutPrice(c) > before);
    }

    /** A balance keeper holds out, and the target remembers. */
    public void testBalanceKeeperRefuses() {
        setUpGame();
        english.setAI(false);
        EuropeanAIPlayer fai = ai(french), dai = ai(dutch), sai = ai(spanish);
        net.sf.freecol.common.diplomacy.Coalition c = coalition(dai, french);
        spanish.setGold(100000);
        sai.rememberTurn();
        new AlliancePlanner(sai).plan(sai.getOutlook(), new LogBuilder(64));
        assertTrue(c.members.contains(FRENCH));
        assertEquals(Stance.ALLIANCE, french.getStance(dutch));
        assertTrue(remembers(sai, Memory.Kind.REFUSED_OFFER, FRENCH));
    }

    /** The player in a coalition is offered gold to leave. */
    public void testPlayerIsOfferedABuyout() {
        setUpGame();
        english.setAI(false);
        EuropeanAIPlayer fai = ai(french), sai = ai(spanish);
        net.sf.freecol.common.diplomacy.Coalition c = coalition(fai, english);
        spanish.setGold(100000);
        sai.rememberTurn();
        new AlliancePlanner(sai).plan(sai.getOutlook(), new LogBuilder(64));
        Offer b = null;
        for (Offer o : sai.getOffers()) if (o.kind == Offer.Kind.BUYOUT) b = o;
        assertNotNull(b);
        assertEquals(FRENCH, b.against);
        assertTrue(b.visibleTo(ENGLISH, game.getTurn().getNumber()));
        final int gold = english.getGold();
        assertTrue(new OfferPlanner(sai).answer(english, b.id, true,
                                                new ChangeSet()));
        assertFalse(c.members.contains(ENGLISH));
        assertEquals(Stance.PEACE, english.getStance(french));
        assertEquals(gold + b.gold, english.getGold());
        assertTrue(remembers(fai, Memory.Kind.DEAL_BROKEN, ENGLISH));
    }

    /** The player as the target buys a member out with a gift. */
    public void testPlayerBuysOutWithAGift() {
        setUpGame();
        spanish.setAI(false);
        EuropeanAIPlayer fai = ai(french), dai = ai(dutch);
        net.sf.freecol.common.diplomacy.Coalition c = coalition(fai, dutch);
        final int price = dai.buyoutPrice(c);
        // Too little: they keep the gold and stay.
        dai.tradeDone(gift(spanish, dutch, price - 50));
        assertTrue(c.members.contains(DUTCH));
        dai.tradeDone(gift(spanish, dutch, price));
        assertFalse(c.members.contains(DUTCH));
        assertEquals(Stance.PEACE, french.getStance(dutch));
        // The founder can not be bought out.
        net.sf.freecol.common.diplomacy.Coalition c2 = coalition(fai, english);
        fai.tradeDone(gift(spanish, french, 100000));
        assertTrue(c2.members.contains(FRENCH));
    }

    private net.sf.freecol.common.model.DiplomaticTrade gift(Player from,
            Player to, int gold) {
        net.sf.freecol.common.model.DiplomaticTrade t
            = new net.sf.freecol.common.model.DiplomaticTrade(game,
                net.sf.freecol.common.model.DiplomaticTrade.TradeContext.DIPLOMATIC,
                from, to, null, 0);
        t.add(new net.sf.freecol.common.model.GoldTradeItem(game, from, to, gold));
        t.setStatus(net.sf.freecol.common.model.DiplomaticTrade.TradeStatus.ACCEPT_TRADE);
        return t;
    }

    /** At war together, the founder plans one strike for all. */
    public void testCoalitionPlansAJointStrike() {
        setUpGame();
        english.setAI(false);
        EuropeanAIPlayer fai = ai(french), dai = ai(dutch);
        net.sf.freecol.common.diplomacy.Coalition c = coalition(fai, dutch, english);
        army(spanish, 30);
        for (Player p : new Player[] { french, dutch, english }) {
            p.setStance(spanish, Stance.WAR);
            spanish.setStance(p, Stance.WAR);
        }
        fai.rememberTurn();
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        assertNotNull(c.jointTarget);
        assertEquals(spanish, game.getFreeColGameObject(c.jointTarget,
            net.sf.freecol.common.model.Colony.class).getOwner());
        assertTrue(c.strikeTurn >= game.getTurn().getNumber()
                   + net.sf.freecol.common.diplomacy.Coalition.MIN_MUSTER);
        assertSame(c, AlliancePlanner.jointPlanFor(dai, SPANISH));
        assertNull("not against others", AlliancePlanner.jointPlanFor(dai, ENGLISH));
        // The plan holds, and is saved with the coalition.
        final int strike = c.strikeTurn;
        new AlliancePlanner(fai).plan(fai.getOutlook(), new LogBuilder(64));
        assertEquals(strike, c.strikeTurn);
        assertEquals(c.serialize(), net.sf.freecol.common.diplomacy.Coalition
            .parse(c.serialize()).serialize());
    }
}
