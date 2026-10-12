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

package net.sf.freecol.client.control;

import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsTradeItem;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.UnitTradeItem;


/**
 * An estimate of what a computer player makes of a trade offer, so
 * the player can see if he is high balling or low balling them.
 *
 * It follows the rules the computer players use
 * ({@code EuropeanAIPlayer.acceptDiplomaticTrade}): every item has a
 * value to them, and they accept when the values add up to zero or
 * more.  Goods they receive are worth what they would get selling
 * them in Europe after their tax, units what the unit costs in
 * Europe, gold is gold.  When the gold asked of them is too much,
 * they reduce it rather than refuse outright.
 *
 * We can not see their market, so our own prices stand in for theirs.
 */
public final class TradeAppraisal {

    /** How the gold asked compares to what the deal is worth to them. */
    public enum Verdict {
        /** Nothing to judge: no gold involved and nothing of value. */
        NONE,
        /** No gold asked yet, but they would pay some. */
        ASK,
        /** Asking clearly less than they would pay. */
        LOW_BALL,
        /** Asking about what they would pay. */
        FAIR,
        /** Asking more than they would pay. */
        HIGH_BALL,
        /** They give more than they get, even without gold. */
        SHORT
    }

    /** Within this many percent (or {@link #FAIR_GOLD}) is fair. */
    public static final int FAIR_PERCENT = 10;
    public static final int FAIR_GOLD = 20;

    /** Below this value the computer drops gold instead of reducing it. */
    public static final int COUNTER_MINIMUM = 50;

    /** The verdict. */
    public final Verdict verdict;
    /** What the deal is worth to them before any gold they pay. */
    public final int value;
    /** The gold asked of them. */
    public final int ask;
    /** The most they can pay, or -1 if not known. */
    public final int theirGold;
    /**
     * The gold they would offer back when high balled, 0 if they would
     * drop the gold, or -1 if they can not pay what is asked.
     */
    public final int counter;


    private TradeAppraisal(Verdict verdict, int value, int ask, int theirGold,
                           int counter) {
        this.verdict = verdict;
        this.value = value;
        this.ask = ask;
        this.theirGold = theirGold;
        this.counter = counter;
    }

    /**
     * Appraise a trade from the other player's point of view.
     *
     * @param agreement The {@code DiplomaticTrade} under negotiation.
     * @param me The client {@code Player}.
     * @param other The other {@code Player}.
     * @param otherTax Their tax rate, or a negative number if unknown
     *     (our own rate is used instead).
     * @param otherUnits How many units they have, or -1 if unknown.
     * @param otherGold How much gold they have, or -1 if unknown.
     * @return The {@code TradeAppraisal}.
     */
    public static TradeAppraisal appraise(DiplomaticTrade agreement,
                                          Player me, Player other,
                                          int otherTax, int otherUnits,
                                          int otherGold) {
        return appraise(agreement, me, other, me.getMarket(),
                        me.getEurope(), otherTax, otherUnits, otherGold);
    }

    /**
     * Appraise a trade from the other player's point of view, with
     * given prices.
     *
     * @param agreement The {@code DiplomaticTrade} under negotiation.
     * @param me The client {@code Player}.
     * @param other The other {@code Player}.
     * @param market The {@code Market} to take goods prices from.
     * @param europe The {@code Europe} to take unit prices from.
     * @param otherTax Their tax rate, or a negative number if unknown
     *     (our own rate is used instead).
     * @param otherUnits How many units they have, or -1 if unknown.
     * @param otherGold How much gold they have, or -1 if unknown.
     * @return The {@code TradeAppraisal}.
     */
    public static TradeAppraisal appraise(DiplomaticTrade agreement,
                                          Player me, Player other,
                                          Market market, Europe europe,
                                          int otherTax, int otherUnits,
                                          int otherGold) {
        final int tax = (otherTax >= 0) ? otherTax : me.getTax();
        int value = 0, ask = 0;
        for (TradeItem item : agreement.getItems()) {
            final boolean theirs = item.getSource() == other;
            if (item instanceof GoldTradeItem) {
                if (theirs) {
                    ask += item.getGold();
                } else {
                    value += item.getGold();
                }
            } else if (item instanceof GoodsTradeItem) {
                final Goods g = item.getGoods();
                if (g == null || market == null) continue;
                if (theirs) {
                    // The computer counts goods it gives away at what
                    // they would cost in Europe, and as a gain: a
                    // quirk of its rules that we have to follow here.
                    value += market.getBidPrice(g.getType(), g.getAmount());
                } else {
                    value += (int)Math.round(market.getSalePrice(g.getType(),
                            g.getAmount()) * (1.0 - tax / 100.0));
                }
            } else if (item instanceof UnitTradeItem) {
                if (item.getUnit() == null) continue;
                final int price = (europe == null) ? 0
                    : europe.getUnitPrice(item.getUnit().getType());
                if (!theirs) {
                    value += Math.max(0, price);
                } else if (otherUnits < 0 || otherUnits >= 10) {
                    value -= Math.max(0, price);
                } // else they refuse to give units away, the item is dropped
            }
        }

        Verdict verdict;
        int counter = -1;
        if (ask <= 0) {
            verdict = (value > 0) ? Verdict.ASK
                : (value < 0) ? Verdict.SHORT : Verdict.NONE;
        } else if (otherGold >= 0 && ask > otherGold) {
            verdict = Verdict.HIGH_BALL; // They can not pay it at all
            counter = -1;
        } else if (ask > value) {
            verdict = Verdict.HIGH_BALL;
            // They take the gold down by half of what the deal is
            // worth to them, or drop it when that is very little.
            counter = (value >= COUNTER_MINIMUM) ? ask - value / 2 : 0;
        } else {
            final int band = Math.max(FAIR_GOLD, value * FAIR_PERCENT / 100);
            verdict = (value - ask <= band) ? Verdict.FAIR : Verdict.LOW_BALL;
        }
        return new TradeAppraisal(verdict, value, ask, otherGold, counter);
    }
}
