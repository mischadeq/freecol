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
 * How a nation judges the player's envoy (step 8, mod): yes, no with a
 * reason, or its price. The same diplomat that makes the nation's own
 * offers scores the move; the player's gold sweetens it.
 *
 * Pure rules, no game objects.
 */
public final class EnvoyRules {

    /** Turns between two envoys to one nation. */
    public static final int WAIT = 5;
    /** Below this trust a nation does not bargain, it says no. */
    public static final double TRUST_LINE = 35;
    /** Below this opinion a nation does not bargain either (not for
        peace: see {@link #distrust}). */
    public static final double OPINION_LINE = -60;
    /** A nation names no price above this: it says no. */
    public static final int MAX_PRICE = 5000;
    /** ... but a war may cost more (8.2; the tribes ask 5000-10000). */
    public static final int MAX_WAR_PRICE = 10000;
    /** Prices are rounded up to this. */
    public static final int ROUND = 50;
    /** Tension taken off per gold of a gift. */
    public static final double GIFT_TENSION = 0.1;

    /** The answer. */
    public enum Verdict { ACCEPT, REFUSE, COUNTER }

    /** Why a nation says no. */
    public enum Reason {
        /** It chose this war itself only lately. */
        NOT_YET,
        /** It does not trust the player, or dislikes him too much. */
        TRUST,
        /** It would never ally with the player as things are. */
        NEVER,
        /** The proposal does not fit (not at war, not at peace...). */
        NOT_NOW,
        /** It sees nothing in it, or the price would be too high. */
        NO_INTEREST,
        /** It will not fight a nation it is allied with (8.2). */
        FRIENDS,
        /** It leads the coalition: it cannot be bought out (8.2). */
        LEADER,
        /** It is in no coalition against the player (8.2). */
        NO_COALITION
    }

    /**
     * What a nation asks to join a war it has no reason of its own for
     * (8.2): more if it likes the enemy, less if it dislikes it.
     *
     * @param opinionOfEnemy Its opinion of the enemy, -100..100.
     * @return The worth of the move, negative.
     */
    public static int warWithoutReason(double opinionOfEnemy) {
        return -(int)Math.round(1500 + 20 * Math.max(-50, opinionOfEnemy));
    }

    /** One answer. */
    public static final class Answer {
        public final Verdict verdict;
        public final Reason reason;
        /** The gold asked in all (COUNTER), 0 otherwise. */
        public final int price;

        Answer(Verdict verdict, Reason reason, int price) {
            this.verdict = verdict;
            this.reason = reason;
            this.price = price;
        }

        static Answer yes() { return new Answer(Verdict.ACCEPT, null, 0); }
        static Answer no(Reason r) { return new Answer(Verdict.REFUSE, r, 0); }
        static Answer price(int p) { return new Answer(Verdict.COUNTER, null, p); }

        @Override
        public String toString() {
            return verdict + ((reason != null) ? " " + reason : "")
                + ((price > 0) ? " " + price + "g" : "");
        }
    }

    /** What the nation brings to the decision. */
    public static final class Input {
        /** What is proposed. */
        public Offer.Kind kind;
        /** The diplomat's worth of the move in gold, or a
            {@link DealValue} marker. */
        public int value = DealValue.NO_OPINION;
        /** Gold the player offers with it. */
        public int gold;
        /** Trust in the player, 0..100. */
        public double trust = Relations.DEFAULT_TRUST;
        /** Opinion of the player, -100..100. */
        public double opinion;
        /** The nation chose its war with the player itself, lately. */
        public boolean committed;
        /** The proposal fits the stance (peace at war, alliance at
            peace). */
        public boolean fits = true;
        /** Why it does not fit, when there is a better word than
            {@link Reason#NOT_NOW} (8.2). */
        public Reason misfit = null;
    }

    private EnvoyRules() {}


    /**
     * Turns until another envoy may go to a nation.
     *
     * @param lastSent The turn the last one went, or -1.
     * @param now The current turn.
     * @return 0 if one may go now.
     */
    public static int waitLeft(int lastSent, int now) {
        return (lastSent < 0) ? 0 : Math.max(0, lastSent + WAIT - now);
    }

    /**
     * Round a price up to {@link #ROUND} gold.
     *
     * @param gold The gold.
     * @return The rounded price.
     */
    public static int roundUp(int gold) {
        return ((Math.max(0, gold) + ROUND - 1) / ROUND) * ROUND;
    }

    /**
     * Judge an envoy.
     *
     * @param in The {@code Input}.
     * @return The {@code Answer}.
     */
    public static Answer judge(Input in) {
        if (in.kind == Offer.Kind.ENVOY_GIFT) return Answer.yes();
        if (!in.fits) {
            return Answer.no((in.misfit != null) ? in.misfit : Reason.NOT_NOW);
        }
        if (in.kind == Offer.Kind.ENVOY_PEACE && in.committed) {
            return Answer.no(Reason.NOT_YET);
        }
        if (in.value == DealValue.NEVER) {
            return Answer.no((distrust(in)) ? Reason.TRUST : Reason.NEVER);
        }
        if (in.value == DealValue.NO_OPINION) {
            return Answer.no(Reason.NO_INTEREST);
        }
        final long total = (long)in.value + in.gold;
        if (total >= 0) return Answer.yes();
        if (distrust(in)) return Answer.no(Reason.TRUST);
        final int price = roundUp((int)Math.min(Integer.MAX_VALUE,
                                                -(long)in.value));
        if (price > ((in.kind == Offer.Kind.ENVOY_WAR) ? MAX_WAR_PRICE
                     : MAX_PRICE)) return Answer.no(Reason.NO_INTEREST);
        return Answer.price(price);
    }

    /**
     * Too little trust to bargain.  For peace only trust counts: a
     * nation makes peace with an enemy it dislikes (the diplomat's
     * score of peace already weighs the dislike), but not with one that
     * broke its word.
     */
    private static boolean distrust(Input in) {
        return in.trust < TRUST_LINE
            || (in.kind != Offer.Kind.ENVOY_PEACE && in.opinion < OPINION_LINE);
    }

    /**
     * Tension a gift takes off.
     *
     * @param gold The gold given.
     * @return The tension to take off (positive).
     */
    public static int giftTension(int gold) {
        return (int)Math.round(Math.max(0, gold) * GIFT_TENSION);
    }
}
