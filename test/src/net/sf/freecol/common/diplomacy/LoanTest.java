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

package net.sf.freecol.common.diplomacy;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;


/**
 * Ship loans (mod): the pure rules in {@link Loan}, {@link LoanRules}
 * and {@link CreditRecord}.
 */
public class LoanTest extends TestCase {

    private static Loan loan(int principal, int rent, int term) {
        return new Loan("l", "model.nation.english", "model.nation.dutch",
                        principal, rent, term, 10, "model.unit.caravel");
    }

    public void testScheduleAndTotalCost() {
        // 2% over 30 turns: about 31% extra.
        final int t = Loan.totalRent(1000, 20, 30);
        assertTrue("about 31%: " + t, t >= 300 && t <= 320);
        assertEquals(0, Loan.totalRent(1000, 0, 30));
        // 6% over 40 turns: about 123%.
        final int t6 = Loan.totalRent(1000, 60, 40);
        assertTrue("about 123%: " + t6, t6 >= 1200 && t6 <= 1260);
        // 1% over 15 turns: about 8%.
        final int t1 = Loan.totalRent(600, 10, 15);
        assertTrue(t1 >= 45 && t1 <= 50);
        assertEquals(34 + 20, Loan.firstInstallment(1000, 20, 30));
        // Paid on time, it closes after the term with the total rent.
        final Loan l = loan(1000, 20, 30);
        int paid = 0, turns = 0;
        while (l.state == Loan.State.ACTIVE) {
            paid += l.pay(11 + turns);
            turns++;
        }
        assertEquals(Loan.State.PAID, l.state);
        assertEquals(30, turns);
        assertEquals(1000 + Loan.totalRent(1000, 20, 30), paid);
        assertEquals(40, l.closed);
        // Terms are clamped.
        assertEquals(Loan.MAX_RENT, loan(500, 99, 30).rent);
        assertEquals(Loan.MIN_TERM, loan(500, 20, 3).term);
    }

    public void testPrincipal() {
        assertEquals(0, LoanRules.principal(1000, 1200));
        assertEquals(LoanRules.MIN_PRINCIPAL, LoanRules.principal(1000, 980));
        assertEquals(650, LoanRules.principal(1000, 351));
        assertEquals(1000, LoanRules.principal(1000, 0));
    }

    private static LoanRules.Lender human(String id, int gold) {
        LoanRules.Lender l = new LoanRules.Lender(id, true);
        l.gold = gold;
        return l;
    }

    private static LoanRules.Lender computer(String id, int spendable,
                                             double opinion) {
        LoanRules.Lender l = new LoanRules.Lender(id, false);
        l.gold = spendable;
        l.opinion = opinion;
        l.character = NationCharacter.TRADING_EMPIRE;
        return l;
    }

    public void testMeansFilter() {
        final CreditRecord.Standing n = CreditRecord.Standing.NEW;
        // Needs 500: only lenders with 500 or more.
        final LoanRules.Lender rich = human("rich", 500);
        final LoanRules.Lender poor = human("poor", 499);
        assertNull(LoanRules.refuse(rich, 500, n, false));
        assertEquals("too poor", LoanRules.refuse(poor, 500, n, false));
        // A computer lender's own treasury is kept: its spendable counts.
        final LoanRules.Lender c = computer("c", 400, 50);
        assertEquals("too poor", LoanRules.refuse(c, 500, n, false));
        c.gold = 600;
        assertNull(LoanRules.refuse(c, 500, n, false));
        // War, no contact, the King, the dead.
        LoanRules.Lender w = human("w", 9999);
        w.atWar = true;
        assertNotNull(LoanRules.refuse(w, 500, n, false));
        w = human("x", 9999);
        w.contact = false;
        assertNotNull(LoanRules.refuse(w, 500, n, false));
        w = human("y", 9999);
        w.ref = true;
        assertNotNull(LoanRules.refuse(w, 500, n, false));
        // Defaulted with them: never again.
        w = human("z", 9999);
        w.blacklisted = true;
        assertNotNull(LoanRules.refuse(w, 500, n, false));
        // The cap: three running.
        w = human("cap", 9999);
        w.running = LoanRules.MAX_RUNNING;
        assertEquals("lends enough", LoanRules.refuse(w, 500, n, false));
        w.running = LoanRules.MAX_RUNNING - 1;
        assertNull(LoanRules.refuse(w, 500, n, false));
        // The borrower has one running.
        assertNotNull(LoanRules.refuse(rich, 500, n, true));
        // Liked most first.
        final List<LoanRules.Lender> all = new ArrayList<>();
        final LoanRules.Lender a = human("a", 1000), b = human("b", 1000);
        a.liked = 10;
        b.liked = 40;
        all.add(a);
        all.add(poor);
        all.add(b);
        final List<LoanRules.Lender> cands = LoanRules.candidates(all, 500, n,
                                                                  false);
        assertEquals(2, cands.size());
        assertEquals("b", cands.get(0).id);
    }

    public void testComputerLenders() {
        final LoanRules.Lender c = computer("c", 2000, 50);
        // Bad borrowers are refused; the player is only warned.
        assertEquals("bad payer", LoanRules.refuse(c, 500,
            CreditRecord.Standing.BAD, false));
        assertNull(LoanRules.refuse(human("h", 2000), 500,
            CreditRecord.Standing.BAD, false));
        // Good borrowers are never refused for their record.
        assertNull(LoanRules.refuse(c, 500, CreditRecord.Standing.GOOD, false));
        // Friends only; not the borrower's rival; only some characters.
        c.opinion = 0;
        assertEquals("not friends", LoanRules.refuse(c, 500,
            CreditRecord.Standing.NEW, false));
        c.opinion = -50;
        assertEquals("dislikes us", LoanRules.refuse(c, 500,
            CreditRecord.Standing.NEW, false));
        c.opinion = 50;
        c.rival = true;
        assertEquals("rival", LoanRules.refuse(c, 500,
            CreditRecord.Standing.NEW, false));
        c.rival = false;
        c.character = NationCharacter.CONQUEROR;
        assertEquals("does not lend", LoanRules.refuse(c, 500,
            CreditRecord.Standing.NEW, false));
        // Their rent: +1.0% for a Wary borrower, never under 1.5%.
        assertEquals(LoanRules.LENDER_RENT, LoanRules.lenderRent(
            NationCharacter.SETTLER, CreditRecord.Standing.NEW));
        assertEquals(LoanRules.LENDER_RENT + 10, LoanRules.lenderRent(
            NationCharacter.SETTLER, CreditRecord.Standing.WARY));
        assertEquals(LoanRules.LENDER_RENT, LoanRules.lenderRent(
            NationCharacter.SETTLER, CreditRecord.Standing.GOOD));
        assertTrue(LoanRules.lenderRent(NationCharacter.TRADING_EMPIRE,
            CreditRecord.Standing.NEW) >= LoanRules.MIN_LENDER_RENT);
    }

    public void testCeiling() {
        final CreditRecord.Standing n = CreditRecord.Standing.NEW;
        assertEquals(30, LoanRules.ceiling(NationCharacter.SETTLER, false, n, true));
        assertEquals(35, LoanRules.ceiling(NationCharacter.CONQUEROR, false, n, true));
        assertEquals(25, LoanRules.ceiling(NationCharacter.INDEPENDENCE_FIRST,
                                           false, n, true));
        assertEquals(40, LoanRules.ceiling(NationCharacter.SETTLER, true, n, true));
        // A Bad payer asking the player is desperate: +1.0%.
        assertEquals(40, LoanRules.ceiling(NationCharacter.SETTLER, false,
            CreditRecord.Standing.BAD, true));
        // The squeeze grows with the rent; a grudge from 5.0%.
        assertEquals(0, LoanRules.squeeze(30));
        assertEquals(1, LoanRules.squeeze(35));
        assertEquals(2, LoanRules.squeeze(40));
        assertEquals(8, LoanRules.squeeze(50));
    }

    public void testWarFreezeAndResume() {
        final Loan l = loan(600, 20, 30);
        l.pay(11);
        final int balance = l.balance;
        l.freeze();
        assertEquals(Loan.State.FROZEN, l.state);
        assertFalse("nothing due at war", l.collectible(true));
        assertFalse(l.collectible(false)); // frozen until thawed
        assertFalse("not paid off early at war", l.paysOffEarly(99999));
        l.thaw();
        assertEquals(Loan.State.ACTIVE, l.state);
        assertEquals("no rent added at war", balance, l.balance);
        assertEquals(0, l.rentOwed);
        assertTrue(l.collectible(false));
    }

    public void testDefaultAfterThreeMissed() {
        final Loan l = loan(600, 20, 30);
        final int due = l.due();
        assertFalse(l.miss());
        assertTrue("the amount stays due", l.due() > due);
        assertFalse(l.miss());
        assertTrue("third in a row", l.miss());
        assertEquals(Loan.State.DEFAULT, l.state);
        assertTrue(l.state.inDefault());
        // A payment in between resets the count.
        final Loan k = loan(600, 20, 30);
        k.miss();
        k.miss();
        k.pay(12);
        assertEquals(0, k.missed);
        assertFalse(k.miss());
        assertEquals(Loan.State.ACTIVE, k.state);
    }

    public void testVoidAndCancel() {
        final Loan l = loan(600, 20, 30);
        l.end(Loan.State.VOID, 20);
        assertTrue(l.over());
        assertFalse(l.open());
        final Loan c = loan(600, 20, 30);
        c.end(Loan.State.CANCELLED, 20);
        assertEquals(0, c.claim());
    }

    public void testSeizure() {
        assertEquals(500, Loan.seizeValue(1000, false));
        assertEquals(250, Loan.seizeValue(1000, true));
        // Worth less than the balance: the rest is a claim.
        Loan l = loan(800, 20, 30);
        l.state = Loan.State.SEIZURE_PENDING;
        assertTrue("waits for the ship to dock", l.state.inDefault());
        l.seize(500, 30);
        assertEquals(Loan.State.SEIZED, l.state);
        assertEquals(300, l.claim());
        assertTrue(l.open());
        // Equal: closed.
        l = loan(500, 20, 30);
        l.seize(500, 30);
        assertEquals(0, l.claim());
        assertTrue(l.over());
        // Worth more: closed, no refund.
        l = loan(300, 20, 30);
        l.seize(500, 30);
        assertEquals(0, l.balance);
        assertTrue(l.over());
        // Ship gone: the whole balance with its rent stays.
        l = loan(600, 20, 30);
        l.miss();
        final int claim = l.claim();
        l.seize(0, 30);
        assertEquals(claim, l.claim());
        assertTrue(l.open());
    }

    public void testForgiveAndWait() {
        Loan l = loan(600, 20, 30);
        l.miss();
        l.miss();
        l.miss();
        l.forgive(25);
        assertEquals(Loan.State.FORGIVEN, l.state);
        assertEquals(0, l.claim());
        assertTrue(l.over());
        // Wait: paying the arrears cures it.
        l = loan(600, 20, 30);
        l.miss();
        l.miss();
        l.miss();
        l.waitFor(20);
        assertEquals(Loan.State.WAITING, l.state);
        assertTrue(l.collectible(false));
        assertFalse(l.waitOver(29));
        assertTrue("ten turns, then asked again", l.waitOver(30));
        l.pay(25);
        assertTrue(l.cured());
        // Unanswered for five turns counts as wait.
        l = loan(600, 20, 30);
        l.state = Loan.State.DEFAULT;
        l.asked = 20;
        assertFalse(l.unanswered(24));
        assertTrue(l.unanswered(25));
    }

    public void testEarlyPayoff() {
        final Loan l = loan(1000, 20, 30);
        l.pay(11);
        final int payoff = l.balance + Loan.rentFor(l.balance, 20);
        assertEquals("rent to this turn only", payoff, l.payoff());
        assertFalse(l.paysOffEarly(payoff + Loan.CUSHION - 1));
        assertTrue(l.paysOffEarly(payoff + Loan.CUSHION));
        // Dear rent: a smaller cushion.
        final Loan d = loan(1000, 40, 30);
        assertTrue(d.paysOffEarly(d.payoff() + Loan.CUSHION_DEAR));
        // Not while frozen or in default.
        l.freeze();
        assertFalse(l.paysOffEarly(99999));
        l.thaw();
        l.miss();
        assertFalse("arrears first", l.paysOffEarly(99999));
        final Loan e = loan(1000, 20, 30);
        assertEquals(e.payoff(), e.payOff(20));
        assertEquals(Loan.State.PAID_EARLY, e.state);
        assertEquals(0, e.balance);
    }

    public void testSerialize() {
        final Loan l = loan(600, 25, 20);
        l.miss();
        l.shipId = "unit:42";
        l.asked = 15;
        final Loan b = Loan.parse(l.serialize());
        assertNotNull(b);
        assertEquals(l.serialize(), b.serialize());
        assertEquals("unit:42", b.shipId);
        assertNull(Loan.parse("bad"));
        final CreditRecord r = new CreditRecord();
        r.repaid = 2;
        r.defaulted(40, "model.nation.english");
        r.mark(CreditRecord.Mark.CURED, "model.nation.english");
        r.defaulted(60, "model.nation.french");
        final CreditRecord q = CreditRecord.parse(r.serialize());
        assertEquals(r.serialize(), q.serialize());
        assertTrue(q.burned.contains("model.nation.french"));
        assertFalse(q.burned.contains("model.nation.english"));
        assertTrue(CreditRecord.parse(null).empty());
    }

    public void testStanding() {
        final CreditRecord r = new CreditRecord();
        assertEquals(CreditRecord.Standing.NEW, r.standing(10));
        r.repaid = 1;
        assertEquals(CreditRecord.Standing.NEW, r.standing(10));
        r.early = 1;
        assertEquals(CreditRecord.Standing.GOOD, r.standing(10));
        // A default: Bad now, Bad for 30 turns, then Wary, forgotten at 100.
        r.defaulted(100, "x");
        assertEquals(CreditRecord.Standing.BAD, r.standing(100));
        r.inDefault = false; // the ship was taken, the loan closed
        assertEquals(CreditRecord.Standing.BAD, r.standing(129));
        assertEquals(CreditRecord.Standing.WARY, r.standing(130));
        assertEquals(CreditRecord.Standing.WARY, r.standing(199));
        assertEquals(CreditRecord.Standing.GOOD, r.standing(200));
        // Forgiven or cured: Wary at once, counts half.
        final CreditRecord f = new CreditRecord();
        f.defaulted(100, "x");
        f.mark(CreditRecord.Mark.FORGIVEN, "x");
        assertEquals(CreditRecord.Standing.WARY, f.standing(101));
        assertTrue("the blacklist is lifted", f.burned.isEmpty());
        assertEquals(CreditRecord.Standing.NEW, f.standing(200));
    }
}
