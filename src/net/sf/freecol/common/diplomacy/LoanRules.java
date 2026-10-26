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
import java.util.Comparator;
import java.util.List;


/**
 * The rules of ship loans (mod) that do not need the game: who may
 * lend, what a borrower accepts, what a computer lender asks, which
 * memories a deal leaves.  See {@link Loan} for one loan.
 */
public final class LoanRules {

    /** Least and rounding of a loan. */
    public static final int MIN_PRINCIPAL = 100, ROUND = 50;
    /** Loans a lender may have running. */
    public static final int MAX_RUNNING = 3;
    /** Turns after a loan closed before the next request. */
    public static final int COOLDOWN = 30;
    /** Turns a refusal holds, and the wait once every lender said no. */
    public static final int REFUSAL_TURNS = 10;
    /** Finished loans are kept this long (the page, the cooldown). */
    public static final int KEEP = 60;

    /** The borrower's ceiling for a normal character... */
    public static final int BASE_CEILING = 30;
    /** ... more for proud (Conqueror) or greedy (Trading empire) ones ... */
    public static final int PROUD_CEILING = 5;
    /** ... less for careful ones (Independence first) ... */
    public static final int CAREFUL_CEILING = -5;
    /** ... more with no ship at all and a load waiting ... */
    public static final int DESPERATE_CEILING = 10;
    /** ... more for a Bad payer asking the player. */
    public static final int BAD_CEILING = 10;

    /** A computer lender's rent, a trading empire's, the least. */
    public static final int LENDER_RENT = 20, TRADER_RENT = 25,
        MIN_LENDER_RENT = 15;
    /** What a computer lender adds for a Wary borrower. */
    public static final int WARY_RENT = 10;

    /** Rent under this is nearly a gift. */
    public static final int GIFT_RENT = 10;
    /** Rent from this on is a grudge (the squeeze counts double). */
    public static final int GRUDGE_RENT = 50;

    /** War loans: dragoons a nation at war wants to afford, and the
        most it borrows. */
    public static final int WAR_SOLDIERS = 3, MAX_WAR_LOAN = 3000;

    /** The share of a debt a peace deal values it at. */
    public static final double DEBT_SHARE = 0.5;

    private LoanRules() {}


    /**
     * The gold to borrow for a ship.
     *
     * @param price The ship's price.
     * @param spendable The borrower's gold above its reserve.
     * @return The principal, 0 if it can pay itself.
     */
    public static int principal(int price, int spendable) {
        final int short_ = price - Math.max(0, spendable);
        if (short_ <= 0) return 0;
        return Math.max(MIN_PRINCIPAL, (short_ + ROUND - 1) / ROUND * ROUND);
    }

    /**
     * The highest rent a borrower accepts.
     *
     * @param ch Its character.
     * @param desperate No ship at all, and a colony or load waiting.
     * @param standing Its credit standing.
     * @param human True when asking the player.
     * @return The ceiling, tenths of a percent.
     */
    public static int ceiling(NationCharacter ch, boolean desperate,
                              CreditRecord.Standing standing, boolean human) {
        int c = BASE_CEILING;
        if (ch == NationCharacter.CONQUEROR
            || ch == NationCharacter.TRADING_EMPIRE) c += PROUD_CEILING;
        if (ch == NationCharacter.INDEPENDENCE_FIRST) c += CAREFUL_CEILING;
        if (desperate) c += DESPERATE_CEILING;
        if (human && standing == CreditRecord.Standing.BAD) c += BAD_CEILING;
        return Loan.clampRent(c);
    }

    /** Does a character lend at all (computer lenders)? */
    public static boolean lends(NationCharacter ch) {
        return ch == NationCharacter.TRADING_EMPIRE
            || ch == NationCharacter.BALANCE_KEEPER
            || ch == NationCharacter.SETTLER;
    }

    /**
     * The rent a computer lender asks.
     *
     * @param ch The lender's character.
     * @param standing The borrower's standing.
     * @return The rent, tenths of a percent.
     */
    public static int lenderRent(NationCharacter ch,
                                 CreditRecord.Standing standing) {
        int r = (ch == NationCharacter.TRADING_EMPIRE) ? TRADER_RENT
            : LENDER_RENT;
        if (standing == CreditRecord.Standing.WARY) r += WARY_RENT;
        return Loan.clampRent(Math.max(MIN_LENDER_RENT, r));
    }

    /**
     * The squeeze a rent leaves with the borrower: the count of a
     * LOAN_SQUEEZED memory, 0 for a fair rent.
     *
     * @param rent The rent, tenths of a percent.
     * @return One per 0.5% above {@link Loan#DEAR_RENT}, doubled from
     *     {@link #GRUDGE_RENT}.
     */
    public static int squeeze(int rent) {
        if (rent <= Loan.DEAR_RENT) return 0;
        final int n = (rent - Loan.DEAR_RENT + 4) / 5;
        return (rent >= GRUDGE_RENT) ? 2 * n : n;
    }

    /** What a debt is worth in a peace deal. */
    public static int debtValue(int balance) {
        return (int)Math.round(Math.max(0, balance) * DEBT_SHARE);
    }


    /** A nation the borrower might ask, as the borrower sees it. */
    public static final class Lender {
        public final String id;
        /** The player (not a computer nation). */
        public final boolean human;
        /** Gold it could lend: the player's (public), a computer's
            above its reserve. */
        public int gold;
        public boolean contact = true, atWar, ref, dead;
        /** The lender's opinion of the borrower (computer lenders). */
        public double opinion;
        /** The borrower's opinion of the lender (who is asked first). */
        public double liked;
        /** Loans it has running. */
        public int running;
        /** The borrower defaulted on it (never again). */
        public boolean blacklisted;
        /** Computer lenders: its character, and whether the borrower
            is its rival (a coalition, an ally's enemy). */
        public NationCharacter character;
        public boolean rival;

        public Lender(String id, boolean human) {
            this.id = id;
            this.human = human;
        }
    }

    /**
     * Why a nation can not lend, or null if it can.
     *
     * @param l The {@code Lender}.
     * @param principal The gold asked.
     * @param standing The borrower's standing.
     * @param borrowerRunning The borrower has a loan running.
     * @return A short reason, or null.
     */
    public static String refuse(Lender l, int principal,
                                CreditRecord.Standing standing,
                                boolean borrowerRunning) {
        if (!l.contact) return "no contact";
        if (l.atWar) return "at war";
        if (l.ref || l.dead) return "not a lender";
        if (borrowerRunning) return "a loan running";
        if (l.blacklisted) return "defaulted with them";
        if (l.running >= MAX_RUNNING) return "lends enough";
        if (l.gold < principal) return "too poor";
        if (l.human) return null;
        if (l.opinion < Mood.NEUTRAL) return "dislikes us";
        if (standing == CreditRecord.Standing.BAD) return "bad payer";
        if (l.character != null && !lends(l.character)) return "does not lend";
        if (l.opinion < Mood.FRIENDLY) return "not friends";
        if (l.rival) return "rival";
        return null;
    }

    /**
     * The nations that can lend, the borrower's favourite first.
     *
     * @param all Every nation it knows.
     * @param principal The gold asked.
     * @param standing The borrower's standing.
     * @param borrowerRunning The borrower has a loan running.
     * @return The candidates.
     */
    public static List<Lender> candidates(List<Lender> all, int principal,
                                          CreditRecord.Standing standing,
                                          boolean borrowerRunning) {
        final List<Lender> ret = new ArrayList<>();
        for (Lender l : all) {
            if (refuse(l, principal, standing, borrowerRunning) == null) {
                ret.add(l);
            }
        }
        ret.sort(Comparator.comparingDouble((Lender l) -> -l.liked));
        return ret;
    }
}
