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
import java.util.Locale;


/**
 * What a nation is willing to sell another at one of its colonies, and
 * for how much (mod, 2026-10-16).
 *
 * Two separate questions:
 * <ul>
 * <li><b>How much</b>: can we spare it, and is it safe to hand to them?
 *     The stock after the colony's own needs, times a share set by our
 *     character (traders let more go) and by how we feel about the
 *     buyer. Weapons and horses go to nobody we dislike or fear. Rounded
 *     down to tens: the buyer learns what we will sell, not what we
 *     hold.</li>
 * <li><b>Price</b>: what is it worth to us? The Europe price after tax
 *     is what we would get by shipping it there, but that trip takes
 *     turns, and a colony full of rebels does not like arming the
 *     Crown, so selling here is worth taking less. Then our margin, and
 *     more for a buyer we dislike, less for a friend.</li>
 * </ul>
 * Pure rules, no game objects, so they can be tested on their own.
 */
public final class SaleTerms {

    /** Whether goods are on offer. */
    public enum Status {
        /** On offer, up to {@link Line#max}. */
        FOR_SALE,
        /** Not (enough) in store: less than one step of ten. */
        OUT_OF_STOCK,
        /** In store, but not for sale to this buyer. */
        NOT_FOR_SALE
    }

    /** Amounts are offered in steps of this many. */
    public static final int STEP = 10;

    /** Turns of food, raw materials... a colony keeps for itself. */
    public static final int KEEP_TURNS = 5;

    /** Weapons and horses a colony keeps: in peace, and at war. */
    public static final int MILITARY_KEEP = 50, MILITARY_KEEP_AT_WAR = 100;

    /** Price taken off for each turn of the trip to Europe and back. */
    public static final double TRIP_PER_TURN = 0.025;
    /** At most this much off for the trip. */
    public static final double TRIP_MAX = 0.25;
    /** Off the price when every colonist is a rebel. */
    public static final double REBELS_MAX = 0.15;
    /** Off for a buyer we love (opinion 100). */
    public static final double FRIEND_DISCOUNT = 0.10;
    /** On top for a buyer we hate (opinion -100). */
    public static final double FOE_PREMIUM = 0.30;
    /** Below this opinion nothing is for sale to the buyer. */
    public static final double NO_TRADE_OPINION = -40;
    /** Fear above which weapons and horses are not sold to the buyer. */
    public static final double MILITARY_FEAR = 50;


    /** One kind of goods on the list. */
    public static final class Line {

        public final String goodsId;
        public final Status status;
        /** Most they sell, in steps of {@link #STEP}; 0 if none. */
        public final int max;
        /** Gold per unit. */
        public final double unitPrice;

        public Line(String goodsId, Status status, int max,
                    double unitPrice) {
            this.goodsId = goodsId;
            this.status = status;
            this.max = (status == Status.FOR_SALE) ? max : 0;
            this.unitPrice = unitPrice;
        }

        /**
         * The gold asked for an amount.
         *
         * @param amount How many.
         * @return The price, at least 1 gold for any goods.
         */
        public int priceFor(int amount) {
            if (amount <= 0) return 0;
            return Math.max(1, (int)Math.round(amount * unitPrice));
        }

        /**
         * Is an amount for this price a deal on these terms?
         *
         * @param amount How many.
         * @param gold The gold offered for them.
         * @return True if we sell that many for that gold.
         */
        public boolean accepts(int amount, int gold) {
            return status == Status.FOR_SALE && amount > 0
                && amount <= max && gold >= priceFor(amount);
        }
    }

    /** What we bring to the decision about one kind of goods. */
    public static final class Input {
        public String goodsId;
        /** Weapons or horses. */
        public boolean military;
        /** In the colony's store. */
        public int stock;
        /** Net made per turn in the colony (negative when used up). */
        public int net;
        /** We are at war with someone. */
        public boolean atWar;
        /** Paid per unit in our Europe market, before tax. */
        public int europePrice;
        /** Our tax rate, percent. */
        public int tax;
        /** Boycotted in our Europe market. */
        public boolean boycotted;
        /** Turns to Europe and back. */
        public int tripTurns;
        /** Our rebels, percent. */
        public int rebels;
        /** Our opinion of the buyer, -100..100. */
        public double opinion;
        /** Our fear of the buyer, 0..100. */
        public double fear;
        /** Our character. */
        public NationCharacter character = NationCharacter.SETTLER;
    }

    private SaleTerms() {}


    /**
     * What the colony keeps for itself.
     *
     * @param in The {@code Input}.
     * @return The amount kept.
     */
    public static int keep(Input in) {
        int keep = Math.max(0, -in.net) * KEEP_TURNS;
        if (in.military) {
            keep += (in.atWar) ? MILITARY_KEEP_AT_WAR : MILITARY_KEEP;
        }
        return keep;
    }

    /**
     * The share of what can be spared that a character lets go.
     *
     * @param ch The {@code NationCharacter}.
     * @param military True for weapons and horses.
     * @return The share, 0..1.
     */
    public static double share(NationCharacter ch, boolean military) {
        double s;
        switch (ch) {
        case TRADING_EMPIRE: s = 0.9; break;
        case CONQUEROR: s = 0.5; break;
        case INDEPENDENCE_FIRST: s = 0.5; break;
        default: s = 0.65; break;
        }
        // Everyone but a trader holds on to weapons.
        if (military && ch != NationCharacter.TRADING_EMPIRE) s *= 0.6;
        return s;
    }

    /**
     * How much the opinion of the buyer lets go: half at neutral, all
     * for a good friend, little for one we do not like.
     *
     * @param opinion The opinion, -100..100.
     * @return The factor, 0..1.
     */
    public static double goodwill(double opinion) {
        if (opinion <= NO_TRADE_OPINION) return 0;
        return Math.max(0.15, Math.min(1.0, 0.5 + opinion / 100.0));
    }

    /**
     * The margin a character takes.
     *
     * @param ch The {@code NationCharacter}.
     * @return The margin, as a fraction.
     */
    public static double margin(NationCharacter ch) {
        switch (ch) {
        case TRADING_EMPIRE: return 0.10;
        case CONQUEROR: return 0.20;
        default: return 0.15;
        }
    }

    /**
     * Gold per unit.
     *
     * @param in The {@code Input}.
     * @return The price per unit, never below a tenth of a gold.
     */
    public static double unitPrice(Input in) {
        double base = in.europePrice * (1 - in.tax / 100.0);
        // Boycotted: Europe will not take it at all, so it is worth less.
        if (in.boycotted) base *= 0.75;
        final double trip = Math.min(TRIP_MAX, TRIP_PER_TURN * in.tripTurns);
        final double rebels = REBELS_MAX
            * Math.max(0, Math.min(100, in.rebels)) / 100.0;
        final double feel = (in.opinion >= 0)
            ? -FRIEND_DISCOUNT * Math.min(100, in.opinion) / 100.0
            : FOE_PREMIUM * Math.min(100, -in.opinion) / 100.0;
        final double p = base * (1 - trip - rebels)
            * (1 + margin(in.character) + feel);
        return Math.max(0.1, p);
    }

    /**
     * The terms for one kind of goods.
     *
     * @param in The {@code Input}.
     * @return The {@code Line}.
     */
    public static Line line(Input in) {
        final double price = unitPrice(in);
        if (in.stock < STEP) {
            return new Line(in.goodsId, Status.OUT_OF_STOCK, 0, price);
        }
        if (in.military && (in.opinion < 0 || in.fear >= MILITARY_FEAR)) {
            return new Line(in.goodsId, Status.NOT_FOR_SALE, 0, price);
        }
        final int spare = in.stock - keep(in);
        final double willing = spare * share(in.character, in.military)
            * goodwill(in.opinion);
        final int max = ((int)Math.floor(willing / STEP)) * STEP;
        return (max < STEP)
            ? new Line(in.goodsId, Status.NOT_FOR_SALE, 0, price)
            : new Line(in.goodsId, Status.FOR_SALE, max, price);
    }


    // Sent from the server to the buyer as text: id,status,max,price;...

    /**
     * Write lines for the buyer.
     *
     * @param lines The {@code Line}s.
     * @return The text.
     */
    public static String serialize(List<Line> lines) {
        final StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            if (sb.length() > 0) sb.append(';');
            sb.append(l.goodsId).append(',').append(l.status.name())
                .append(',').append(l.max).append(',')
                .append(String.format(Locale.ROOT, "%.3f", l.unitPrice));
        }
        return sb.toString();
    }

    /**
     * Read lines written by {@link #serialize}.
     *
     * @param text The text.
     * @return The {@code Line}s (bad entries are skipped).
     */
    public static List<Line> parse(String text) {
        final List<Line> ret = new ArrayList<>();
        if (text == null || text.isEmpty()) return ret;
        for (String part : text.split(";")) {
            final String[] f = part.split(",");
            if (f.length != 4) continue;
            try {
                ret.add(new Line(f[0], Status.valueOf(f[1]),
                                 Integer.parseInt(f[2]),
                                 Double.parseDouble(f[3])));
            } catch (IllegalArgumentException e) {
                // Skip a bad entry.
            }
        }
        return ret;
    }

    /**
     * Find the line for some goods.
     *
     * @param lines The {@code Line}s.
     * @param goodsId The goods id.
     * @return The {@code Line}, or null.
     */
    public static Line find(List<Line> lines, String goodsId) {
        if (lines == null) return null;
        for (Line l : lines) if (l.goodsId.equals(goodsId)) return l;
        return null;
    }
}
