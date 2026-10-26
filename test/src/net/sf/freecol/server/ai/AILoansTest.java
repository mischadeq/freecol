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

import net.sf.freecol.common.diplomacy.CreditRecord;
import net.sf.freecol.common.diplomacy.Loan;
import net.sf.freecol.common.diplomacy.LoanRules;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.diplomacy.LoanDesk;
import net.sf.freecol.server.ai.diplomacy.OfferPlanner;
import net.sf.freecol.server.model.CreditRecords;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * Ship loans (mod): a computer nation without ships asks the player
 * (or another computer nation) for the gold, buys the ship at once,
 * pays it back, and loses the ship in default.
 */
public class AILoansTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");
    private static final TileType ocean = spec().getTileType("model.tile.ocean");
    private static final String DUTCH = "model.nation.dutch",
        ENGLISH = "model.nation.english", FRENCH = "model.nation.french",
        SPANISH = "model.nation.spanish";

    private Game game;
    private ServerPlayer dutch, english, french;
    private EuropeanAIPlayer ai;


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** Dutch (computer, no ships, no gold) on the coast; English human. */
    private void setUpGame() {
        FreeColTestCase.MapBuilder mb = new FreeColTestCase.MapBuilder(getGame());
        mb.setDimensions(20, 20).setBaseTileType(ocean);
        for (int y = 2; y < 18; y++) {
            for (int x = 2; x <= 8; x++) mb.setTileType(x, y, plains);
        }
        mb.setTileType(19, 0, spec().getTileType("model.tile.highSeas"));
        game = ServerTestHelper.startServerGame(mb.build());
        dutch = getServerPlayer(game, DUTCH);
        english = getServerPlayer(game, ENGLISH);
        french = getServerPlayer(game, FRENCH);
        english.setAI(false);
        for (ServerPlayer p : new ServerPlayer[] { english, french }) {
            dutch.setStance(p, Stance.PEACE);
            p.setStance(dutch, Stance.PEACE);
        }
        FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(game.getMap().getTile(8, 10)).initialColonists(3).build();
        FreeColTestUtils.getColonyBuilder().player(english)
            .colonyTile(game.getMap().getTile(2, 10)).initialColonists(2).build();
        ai = ai(dutch);
        dutch.setGold(0);
        english.setGold(5000);
        french.setGold(0);
    }

    private static EuropeanAIPlayer ai(ServerPlayer p) {
        return (EuropeanAIPlayer)ServerTestHelper.getServer().getAIMain()
            .getAIPlayer(p);
    }

    private void plan() {
        new LoanDesk(ai).plan(new LogBuilder(64));
    }

    private void nextTurn() {
        game.setTurn(new Turn(game.getTurn().getNumber() + 1));
    }

    private Offer open(EuropeanAIPlayer a, Offer.Kind k) {
        for (Offer o : a.getOffers()) {
            if (o.kind == k && o.state == Offer.State.OPEN) return o;
        }
        return null;
    }

    private boolean answer(Offer o, ServerPlayer h, boolean yes, int amount) {
        return new OfferPlanner(ai).answer(h, o.id, yes, amount, new ChangeSet());
    }

    private int ships(ServerPlayer p) {
        int n = 0;
        for (Unit u : p.getUnitSet()) if (u.isNaval()) n++;
        return n;
    }

    /** Ask, and the player lends at 2.0% for 30 turns. */
    private Loan borrow() {
        plan();
        final Offer o = open(ai, Offer.Kind.LOAN);
        assertNotNull("asks the player", o);
        assertTrue(answer(o, english, true, 20 * 100 + 30));
        assertEquals(1, ai.getLoans().size());
        return ai.getLoans().get(0);
    }

    public void testAsksWhenNoShipAndNoGold() {
        setUpGame();
        assertEquals(0, ships(dutch));
        plan();
        final Offer o = open(ai, Offer.Kind.LOAN);
        assertNotNull(o);
        assertEquals(ENGLISH, o.audience.get(0));
        assertTrue(o.amount >= LoanRules.MIN_PRINCIPAL);
        assertEquals(0, o.amount % LoanRules.ROUND);
        assertTrue("the whole price, it has nothing", o.amount >= o.gold);
        assertTrue(o.placeName.contains("ships=0"));
        assertTrue(o.placeName.contains("standing=NEW"));
        // One request at a time.
        plan();
        int n = 0;
        for (Offer x : ai.getOffers()) if (x.kind == Offer.Kind.LOAN) n++;
        assertEquals(1, n);
    }

    public void testRichNationDoesNotAsk() {
        setUpGame();
        dutch.setGold(100000);
        plan();
        assertNull(open(ai, Offer.Kind.LOAN));
    }

    public void testPoorPlayerIsNotAsked() {
        setUpGame();
        english.setGold(10);
        plan();
        assertNull(open(ai, Offer.Kind.LOAN));
    }

    public void testAcceptMovesGoldAndMakesShip() {
        setUpGame();
        plan();
        final Offer o = open(ai, Offer.Kind.LOAN);
        final int gold = english.getGold();
        assertTrue(answer(o, english, true, 20 * 100 + 30));
        assertEquals(Offer.State.DONE, o.state);
        final Loan l = ai.getLoans().get(0);
        assertEquals(o.amount, l.principal);
        assertEquals(20, l.rent);
        assertEquals(30, l.term);
        assertEquals(gold - o.amount, english.getGold());
        assertEquals(1, ships(dutch));
        final Unit ship = game.getFreeColGameObject(l.shipId, Unit.class);
        assertNotNull(ship);
        assertTrue("bought in their Europe", ship.isInEurope());
        assertEquals(dutch, ship.getOwner());
        assertNotNull("an AI unit for it",
            ServerTestHelper.getServer().getAIMain().getAIUnit(ship));
        boolean given = false;
        for (Memory m : ai.getMemories().list()) {
            given |= m.kind == Memory.Kind.LOAN_GIVEN && ENGLISH.equals(m.about);
        }
        assertTrue(given);
        // A ship now: no more requests.
        plan();
        assertNull(open(ai, Offer.Kind.LOAN));
    }

    public void testCounterOffer() {
        setUpGame();
        plan();
        final Offer o = open(ai, Offer.Kind.LOAN);
        assertTrue(answer(o, english, true, 55 * 100 + 30));
        assertTrue(ai.getLoans().isEmpty());
        final Offer c = open(ai, Offer.Kind.LOAN);
        assertNotNull("their highest rent", c);
        assertTrue(c.id.endsWith(Offer.COUNTER));
        assertTrue(c.delivered < 55 && c.delivered >= LoanRules.BASE_CEILING);
        assertTrue(answer(c, english, true, -1));
        assertEquals(c.delivered, ai.getLoans().get(0).rent);
    }

    public void testRefusalTriesTheNextLender() {
        setUpGame();
        french.setAI(false);
        french.setGold(5000);
        plan();
        final Offer o = open(ai, Offer.Kind.LOAN);
        final String first = o.audience.get(0);
        assertTrue(answer(o, (ServerPlayer)game.getPlayerByNationId(first),
                          false, -1));
        plan();
        final Offer o2 = open(ai, Offer.Kind.LOAN);
        assertNotNull(o2);
        assertFalse("the next one", first.equals(o2.audience.get(0)));
        assertTrue(answer(o2, (ServerPlayer)game.getPlayerByNationId(
            o2.audience.get(0)), false, -1));
        // Both said no: a while before asking again.
        plan();
        assertNull(open(ai, Offer.Kind.LOAN));
        for (int i = 0; i < LoanRules.REFUSAL_TURNS; i++) nextTurn();
        plan();
        assertNotNull(open(ai, Offer.Kind.LOAN));
    }

    public void testPaymentsFromSpendableAndPromise() {
        setUpGame();
        final Loan l = borrow();
        final int noPromise = ai.reserve() - ai.loanPromise();
        assertEquals("the next payment is kept back", noPromise + l.due(),
                     ai.reserve());
        // Gold above the reserve (without its own promise): it pays.
        dutch.setGold(noPromise + l.due());
        final int due = l.due();
        final int gold = english.getGold();
        nextTurn();
        plan();
        assertEquals(1, l.paidTurns);
        assertEquals(gold + due, english.getGold());
        assertEquals(noPromise, dutch.getGold());
        // Only the reserve left: a missed payment.
        nextTurn();
        plan();
        assertEquals(1, l.missed);
        assertEquals(1, l.paidTurns);
    }

    public void testEarlyPayoff() {
        setUpGame();
        final Loan l = borrow();
        dutch.setGold(ai.reserve() - ai.loanPromise() + l.payoff()
                      + Loan.CUSHION);
        final int gold = english.getGold();
        final int payoff = l.payoff();
        nextTurn();
        plan();
        assertEquals(Loan.State.PAID_EARLY, l.state);
        assertEquals(gold + payoff, english.getGold());
        assertEquals(1, CreditRecords.of(dutch).early);
    }

    public void testDefaultAndSeizure() throws Exception {
        setUpGame();
        final Loan l = borrow();
        final Unit ship = game.getFreeColGameObject(l.shipId, Unit.class);
        // A colonist aboard stays the borrower's.
        final Unit colonist = new ServerUnit(game, ship, dutch,
            spec().getUnitType("model.unit.freeColonist"));
        for (int i = 0; i < Loan.MISSES; i++) {
            nextTurn();
            plan();
        }
        assertEquals(Loan.State.DEFAULT, l.state);
        assertEquals(CreditRecord.Standing.BAD, CreditRecords.standing(dutch));
        assertTrue(CreditRecords.of(dutch).burned.contains(ENGLISH));
        final Offer q = open(ai, Offer.Kind.LOAN_DEFAULT);
        assertNotNull("the player decides", q);
        assertTrue(answer(q, english, true, 0)); // seize
        assertEquals(Loan.State.SEIZED, l.state);
        assertEquals(english, ship.getOwner());
        assertTrue(english.getEurope().getUnitList().contains(ship));
        assertFalse(dutch.getUnitSet().contains(ship));
        assertTrue(english.getUnitSet().contains(ship));
        assertEquals(dutch, colonist.getOwner());
        assertTrue(dutch.getEurope().getUnitList().contains(colonist));
        final AIUnit aiu = ServerTestHelper.getServer().getAIMain()
            .getAIUnit(ship);
        assertNotNull(aiu);
        assertEquals(english, aiu.getUnit().getOwner());
        for (AIUnit a : ai.getAIUnits()) assertTrue(a.getUnit() != ship);
        assertEquals(1, CreditRecords.of(dutch).seized);
        // Saved and loaded: the same on both sides.
        java.io.File file = java.io.File.createTempFile("loans", ".fsg");
        file.deleteOnExit();
        ServerTestHelper.getServer().saveGame(file, null, null);
        final String loan = l.serialize();
        final String credit = CreditRecords.serialize(dutch);
        FreeColServer loaded = ServerTestHelper.startServer(file, false, true);
        final Game g2 = loaded.getGame();
        final EuropeanAIPlayer ai2 = (EuropeanAIPlayer)loaded.getAIMain()
            .getAIPlayer(g2.getPlayerByNationId(DUTCH));
        assertEquals(1, ai2.getLoans().size());
        assertEquals(loan, ai2.getLoans().get(0).serialize());
        assertEquals(credit, CreditRecords.serialize(
            g2.getPlayerByNationId(DUTCH)));
        final Unit ship2 = g2.getFreeColGameObject(ship.getId(), Unit.class);
        assertEquals(ENGLISH, ship2.getOwner().getNationId());
        assertTrue(ship2.getOwner().getUnitSet().contains(ship2));
        assertFalse(g2.getPlayerByNationId(DUTCH).getUnitSet().contains(ship2));
        final AIUnit aiu2 = loaded.getAIMain().getAIUnit(ship2);
        assertNotNull(aiu2);
        for (AIUnit a : ai2.getAIUnits()) assertTrue(a.getUnit() != ship2);
    }

    public void testForgiveAndWait() {
        setUpGame();
        final Loan l = borrow();
        for (int i = 0; i < Loan.MISSES; i++) {
            nextTurn();
            plan();
        }
        Offer q = open(ai, Offer.Kind.LOAN_DEFAULT);
        assertTrue(answer(q, english, true, 2)); // wait
        assertEquals(Loan.State.WAITING, l.state);
        // The arrears are paid: cured.
        dutch.setGold(ai.reserve() - ai.loanPromise() + l.due());
        nextTurn();
        plan();
        assertEquals(Loan.State.ACTIVE, l.state);
        assertFalse(CreditRecords.of(dutch).burned.contains(ENGLISH));
        assertEquals(CreditRecord.Standing.WARY, CreditRecords.standing(dutch));
        // Default again, and forgiven.
        dutch.setGold(0);
        for (int i = 0; i < Loan.MISSES; i++) {
            nextTurn();
            plan();
        }
        assertEquals(Loan.State.DEFAULT, l.state);
        q = open(ai, Offer.Kind.LOAN_DEFAULT);
        assertTrue(answer(q, english, true, 1)); // forgive
        assertEquals(Loan.State.FORGIVEN, l.state);
        assertEquals(0, l.claim());
        boolean forgiven = false;
        for (Memory m : ai.getMemories().list()) {
            forgiven |= m.kind == Memory.Kind.LOAN_FORGIVEN;
        }
        assertTrue(forgiven);
    }

    public void testUnansweredCountsAsWait() {
        setUpGame();
        final Loan l = borrow();
        for (int i = 0; i < Loan.MISSES; i++) {
            nextTurn();
            plan();
        }
        for (int i = 0; i < Loan.ANSWER_TURNS; i++) {
            nextTurn();
            plan();
        }
        assertEquals(Loan.State.WAITING, l.state);
    }

    public void testWarFreezesPayments() {
        setUpGame();
        final Loan l = borrow();
        dutch.setStance(english, Stance.WAR);
        english.setStance(dutch, Stance.WAR);
        dutch.setGold(10000);
        nextTurn();
        plan();
        assertEquals(Loan.State.FROZEN, l.state);
        assertEquals(0, l.paidTurns);
        assertEquals(l.principal, l.balance);
        dutch.setStance(english, Stance.PEACE);
        english.setStance(dutch, Stance.PEACE);
        dutch.setGold(ai.reserve() - ai.loanPromise() + l.due());
        nextTurn();
        plan();
        assertEquals("payments resume", 1, l.paidTurns);
    }

    public void testPeaceTermsCancelTheDebt() {
        setUpGame();
        final Loan l = borrow();
        l.freeze();
        // Their peace terms ask to cancel the debt: accepted.
        final Offer p = new Offer("peace", Offer.Kind.PEACE, DUTCH, null,
            l.claim(), 0, null, "", game.getTurn().getNumber(), 0);
        p.audience.add(ENGLISH);
        ai.getOffers().add(p);
        dutch.setStance(english, Stance.WAR);
        english.setStance(dutch, Stance.WAR);
        assertTrue(answer(p, english, true, -1));
        assertEquals(Loan.State.CANCELLED, l.state);
        assertEquals(0, l.claim());
        // The Dutch (traders) would not insist on it.
        assertEquals(0, new LoanDesk(ai).debtToCancel(english));
        assertEquals(LoanRules.debtValue(1000), 500);
    }

    public void testComputerLender() {
        setUpGame();
        english.setGold(0); // the player cannot lend
        french.setGold(20000);
        final EuropeanAIPlayer fai = ai(french);
        final int spend = fai.spendable();
        plan();
        assertEquals("signed at once", 1, ai.getLoans().size());
        final Loan l = ai.getLoans().get(0);
        assertEquals(FRENCH, l.lender);
        assertEquals(LoanRules.lenderRent(fai.character(),
            CreditRecord.Standing.NEW), l.rent);
        assertEquals(spend - l.principal, fai.spendable());
        assertEquals(1, ships(dutch));
        // In default the French seize at once (the ship is in Europe).
        for (int i = 0; i < Loan.MISSES; i++) {
            nextTurn();
            plan();
        }
        assertEquals(Loan.State.SEIZED, l.state);
        assertEquals(french, game.getFreeColGameObject(l.shipId, Unit.class)
                     .getOwner());
        boolean bad = false;
        for (Memory m : fai.getMemories().list()) {
            bad |= m.kind == Memory.Kind.LOAN_DEFAULT && DUTCH.equals(m.about);
        }
        assertTrue(bad);
    }

    public void testComputerLendersRefuseBadPayers() {
        setUpGame();
        english.setGold(0);
        french.setGold(20000);
        CreditRecords.of(dutch).defaulted(game.getTurn().getNumber(),
                                          SPANISH);
        plan();
        assertTrue(ai.getLoans().isEmpty());
        // The player still gets asked (and sees the warning).
        english.setGold(5000);
        nextTurn();
        plan();
        final Offer o = open(ai, Offer.Kind.LOAN);
        assertNotNull(o);
        assertTrue(o.placeName.contains("standing=BAD"));
    }
}
