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


/**
 * A ship loan (mod): a nation that lost its ships borrows the price of
 * one and pays it back in equal parts, with rent on what it still owes.
 * The ship is the collateral.  Rent is kept in tenths of a percent per
 * turn (20 is 2.0%), so no doubles in the saved game.
 */
public final class Loan {

    /** Rent per turn, tenths of a percent: lowest, highest, default. */
    public static final int MIN_RENT = 0, MAX_RENT = 60, DEFAULT_RENT = 20;
    /** Turns to pay it back: shortest, longest, default. */
    public static final int MIN_TERM = 15, MAX_TERM = 40, DEFAULT_TERM = 30;
    /** Missed payments in a row that make a default. */
    public static final int MISSES = 3;
    /** Turns a lender who waits gives the borrower. */
    public static final int WAIT_TURNS = 10;
    /** Turns before an unanswered default question counts as "wait". */
    public static final int ANSWER_TURNS = 5;
    /** Gold kept over the payoff before paying off early... */
    public static final int CUSHION = 300;
    /** ... less when the rent is above {@link #DEAR_RENT}. */
    public static final int CUSHION_DEAR = 100;
    /** Rent above this hurts: paid off sooner, a squeeze. */
    public static final int DEAR_RENT = 30;
    /** A seized ship is worth this share of its Europe price... */
    public static final double SEIZE_SHARE = 0.5;
    /** ... times this when damaged. */
    public static final double DAMAGED_SHARE = 0.5;

    public enum State {
        /** Being paid back. */
        ACTIVE,
        /** The two are at war: nothing due, no rent. */
        FROZEN,
        /** Paid back in full. */
        PAID,
        /** Paid off before the end. */
        PAID_EARLY,
        /** Three payments missed; the lender decides. */
        DEFAULT,
        /** The lender waits {@link #WAIT_TURNS} turns for the arrears. */
        WAITING,
        /** The ship goes to the lender once it is docked in Europe. */
        SEIZURE_PENDING,
        /** The ship was taken; any balance left is a claim. */
        SEIZED,
        /** The lender cancelled the debt. */
        FORGIVEN,
        /** Cancelled in a peace deal. */
        CANCELLED,
        /** The borrower or the lender is gone. */
        VOID;

        /** Still being paid (or due). */
        public boolean running() {
            return this == ACTIVE || this == FROZEN || this == DEFAULT
                || this == WAITING;
        }

        /** In default (on the credit record as such now). */
        public boolean inDefault() {
            return this == DEFAULT || this == WAITING
                || this == SEIZURE_PENDING;
        }
    }

    public final String id;
    /** Nation ids. */
    public final String lender, borrower;
    /** Gold lent. */
    public final int principal;
    /** Rent per turn in tenths of a percent, and turns to pay back. */
    public final int rent, term;
    /** The turn it was signed. */
    public final int signed;
    /** The ship type bought with it. */
    public final String shipType;

    /** Principal still owed. */
    public int balance;
    /** Rent due but not paid (missed payments). */
    public int rentOwed;
    /** Payments made. */
    public int paidTurns;
    /** Payments missed in a row. */
    public int missed;
    public State state = State.ACTIVE;
    /** The ship (the collateral), once bought. */
    public String shipId;
    /** The turn the lender was last asked what to do (or started to
        wait), -1 if never. */
    public int asked = -1;
    /** The turn it was closed, -1 while open. */
    public int closed = -1;


    public Loan(String id, String lender, String borrower, int principal,
                int rent, int term, int signed, String shipType) {
        this.id = id;
        this.lender = lender;
        this.borrower = borrower;
        this.principal = principal;
        this.rent = clampRent(rent);
        this.term = clampTerm(term);
        this.signed = signed;
        this.shipType = shipType;
        this.balance = principal;
    }

    public static int clampRent(int rent) {
        return Math.max(MIN_RENT, Math.min(MAX_RENT, rent));
    }

    public static int clampTerm(int term) {
        return Math.max(MIN_TERM, Math.min(MAX_TERM, term));
    }

    /**
     * Rent on a balance for one turn.
     *
     * @param balance The gold owed.
     * @param rent The rent in tenths of a percent.
     * @return The rent, rounded.
     */
    public static int rentFor(int balance, int rent) {
        return (int)Math.round(Math.max(0, balance) * rent / 1000.0);
    }

    /**
     * The principal part paid each turn.
     *
     * @param principal The gold lent.
     * @param term The turns.
     * @return The part, rounded up.
     */
    public static int part(int principal, int term) {
        return (principal + term - 1) / Math.max(1, term);
    }

    /**
     * What a loan costs over its whole term, beyond the principal, if
     * every payment is made on time.
     *
     * @param principal The gold lent.
     * @param rent The rent in tenths of a percent.
     * @param term The turns.
     * @return The total rent.
     */
    public static int totalRent(int principal, int rent, int term) {
        final int part = part(principal, term);
        int balance = principal, total = 0;
        while (balance > 0) {
            total += rentFor(balance, rent);
            balance -= Math.min(balance, part);
        }
        return total;
    }

    /**
     * The installment of a loan on time, for the first turn.
     *
     * @param principal The gold lent.
     * @param rent The rent in tenths of a percent.
     * @param term The turns.
     * @return The first installment.
     */
    public static int firstInstallment(int principal, int rent, int term) {
        return part(principal, term) + rentFor(principal, rent);
    }

    /** The principal due now: this turn's part and the missed ones. */
    public int principalDue() {
        return Math.min(balance, part(principal, term) * (missed + 1));
    }

    /** All that is due now. */
    public int due() {
        return principalDue() + rentOwed + rentFor(balance, rent);
    }

    /** Is a payment due this turn (not at war, not closed)? */
    public boolean collectible(boolean atWar) {
        return !atWar && balance > 0
            && (state == State.ACTIVE || state == State.DEFAULT
                || state == State.WAITING);
    }

    /**
     * A payment made: the parts due are off the balance.
     *
     * @param now The turn.
     * @return The gold paid.
     */
    public int pay(int now) {
        final int gold = due();
        balance -= principalDue();
        rentOwed = 0;
        missed = 0;
        paidTurns++;
        if (balance <= 0) {
            balance = 0;
            if (state.running()) {
                state = State.PAID;
                closed = now;
            }
        }
        return gold;
    }

    /**
     * A payment missed: the rent stays owed, the part is due next turn.
     *
     * @return True if this made a default just now.
     */
    public boolean miss() {
        rentOwed += rentFor(balance, rent);
        missed++;
        if (state == State.ACTIVE && missed >= MISSES) {
            state = State.DEFAULT;
            return true;
        }
        return false;
    }

    /** All a payoff now costs: the balance, rent owed, this turn's rent. */
    public int payoff() {
        return balance + rentOwed + rentFor(balance, rent);
    }

    /** The cushion a borrower keeps over the payoff. */
    public int cushion() {
        return (rent > DEAR_RENT) ? CUSHION_DEAR : CUSHION;
    }

    /**
     * Should the borrower pay it all off now?
     *
     * @param spendable Its gold above its reserve.
     * @return True for a running loan (not frozen, not in default) it
     *     can pay off and keep its cushion.
     */
    public boolean paysOffEarly(int spendable) {
        return state == State.ACTIVE && missed == 0 && balance > 0
            && spendable >= payoff() + cushion();
    }

    /**
     * Pay it all off.
     *
     * @param now The turn.
     * @return The gold paid.
     */
    public int payOff(int now) {
        final int gold = payoff();
        balance = 0;
        rentOwed = 0;
        paidTurns++;
        state = State.PAID_EARLY;
        closed = now;
        return gold;
    }

    /** At war with each other: payments stop. */
    public void freeze() {
        if (state == State.ACTIVE) state = State.FROZEN;
    }

    /** Peace again: payments go on where they stopped. */
    public void thaw() {
        if (state == State.FROZEN) state = State.ACTIVE;
    }

    /** What is still owed, all in. */
    public int claim() {
        return balance + rentOwed;
    }

    /**
     * What a seized ship is worth off the debt.
     *
     * @param price Its price in Europe.
     * @param damaged True if damaged.
     * @return The worth.
     */
    public static int seizeValue(int price, boolean damaged) {
        return (int)Math.round(Math.max(0, price) * SEIZE_SHARE
                               * ((damaged) ? DAMAGED_SHARE : 1.0));
    }

    /**
     * The ship is taken (or found gone, worth 0): its worth comes off
     * what is owed; the rest is a claim.  Worth more: the loan closes,
     * no refund.
     *
     * @param worth The ship's worth.
     * @param now The turn.
     */
    public void seize(int worth, int now) {
        balance = Math.max(0, claim() - Math.max(0, worth));
        rentOwed = 0;
        state = State.SEIZED;
        if (balance == 0) closed = now;
    }

    /** The lender forgives the rest. */
    public void forgive(int now) {
        balance = 0;
        rentOwed = 0;
        state = State.FORGIVEN;
        closed = now;
    }

    /** The lender waits for the arrears. */
    public void waitFor(int now) {
        state = State.WAITING;
        asked = now;
    }

    /** Waiting, and all arrears were paid: back to normal. */
    public boolean cured() {
        return state == State.WAITING && missed == 0 && balance > 0;
    }

    /** Waited long enough without the arrears: ask again. */
    public boolean waitOver(int now) {
        return state == State.WAITING && missed > 0
            && now - asked >= WAIT_TURNS;
    }

    /** Asked what to do and no answer for a while: counts as "wait". */
    public boolean unanswered(int now) {
        return state == State.DEFAULT && asked >= 0
            && now - asked >= ANSWER_TURNS;
    }

    /** Cancelled in a peace deal, or a side is gone. */
    public void end(State s, int now) {
        state = s;
        balance = 0;
        rentOwed = 0;
        closed = now;
    }

    /** Is it finished (nothing more will happen)? */
    public boolean over() {
        return closed >= 0 || state == State.VOID;
    }

    /** Is something owed on it (running, in default, or a claim)? */
    public boolean open() {
        return !over() && (balance > 0 || rentOwed > 0);
    }


    // Serialization: fields separated by '|'.

    public String serialize() {
        return String.join("|", id, lender, borrower,
            String.valueOf(principal), String.valueOf(balance),
            String.valueOf(rent), String.valueOf(term),
            String.valueOf(paidTurns), String.valueOf(missed),
            String.valueOf(rentOwed), state.name(), String.valueOf(signed),
            (shipType == null) ? "" : shipType,
            (shipId == null) ? "" : shipId, String.valueOf(asked),
            String.valueOf(closed));
    }

    /**
     * Read a loan back.
     *
     * @param s The line from {@link #serialize}.
     * @return The {@code Loan}, or null if it can not be read.
     */
    public static Loan parse(String s) {
        final String[] f = s.split("\\|", -1);
        if (f.length != 16) return null;
        try {
            Loan l = new Loan(f[0], f[1], f[2], Integer.parseInt(f[3]),
                Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                Integer.parseInt(f[11]), f[12].isEmpty() ? null : f[12]);
            l.balance = Integer.parseInt(f[4]);
            l.paidTurns = Integer.parseInt(f[7]);
            l.missed = Integer.parseInt(f[8]);
            l.rentOwed = Integer.parseInt(f[9]);
            l.state = State.valueOf(f[10]);
            l.shipId = f[13].isEmpty() ? null : f[13];
            l.asked = Integer.parseInt(f[14]);
            l.closed = Integer.parseInt(f[15]);
            return l;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return id + " " + Action.shortId(lender) + "->"
            + Action.shortId(borrower) + " " + balance + "/" + principal
            + " " + rent / 10 + "." + rent % 10 + "% " + state;
    }
}
