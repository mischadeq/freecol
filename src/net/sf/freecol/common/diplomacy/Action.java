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

import java.util.Objects;


/**
 * One move a nation could make (or was seen to make): its kind, the
 * nation it concerns, and the details that matter for that kind.
 */
public final class Action {

    /** The kinds of move. */
    public enum Kind {
        NOTHING(0, false, false, true),
        DECLARE_WAR(-1, true, true, true),
        ATTACK_COLONY(-1, false, true, true),
        MAKE_PEACE(1, true, true, true),
        ALLIANCE(1, true, true, false),
        INCITE(1, false, true, false),
        SELL_GOODS(1, false, false, false),
        BUY_GOODS(1, false, false, false),
        TRADE_AGREEMENT(1, false, false, false),
        ARM(0, false, false, true),
        /** Sell muskets or horses to a tribe living next to a rival. */
        ARM_TRIBE(1, false, false, true);

        /** +1 friendly to the target, -1 hostile, 0 no target. */
        public final int side;
        /** A big move: at most one per turn. */
        public final boolean big;
        /** Opinion of the target counts in the score. */
        public final boolean political;
        /** Can be seen in a save (to compare with what nations did). */
        public final boolean visible;

        /**
         * A small move: trade, or arming our own colonists.  Judged
         * against the lower threshold; the high one is there to keep
         * war, peace and alliances from flip-flopping.
         */
        public boolean small() {
            return this == SELL_GOODS || this == BUY_GOODS
                || this == TRADE_AGREEMENT || this == ARM_TRIBE
                || this == ARM;
        }

        Kind(int side, boolean big, boolean political, boolean visible) {
            this.side = side;
            this.big = big;
            this.political = political;
            this.visible = visible;
        }
    }

    public final Kind kind;
    /** The nation it concerns (id), or null. */
    public final String target;
    /** For incite: the nation the target should fight. */
    public final String against;
    /** Goods type id, for trade moves. */
    public final String goods;
    /** Amount (goods, soldiers) or per-turn amount for agreements. */
    public final int amount;
    /** Price per unit of goods, or the gold paid for incite. */
    public final int price;
    /** Colony or settlement id, for an attack. */
    public final String place;


    private Action(Kind kind, String target, String against, String goods,
                   int amount, int price, String place) {
        this.kind = kind;
        this.target = target;
        this.against = against;
        this.goods = goods;
        this.amount = amount;
        this.price = price;
        this.place = place;
    }

    public static Action nothing() {
        return new Action(Kind.NOTHING, null, null, null, 0, 0, null);
    }

    public static Action war(String target) {
        return new Action(Kind.DECLARE_WAR, target, null, null, 0, 0, null);
    }

    public static Action attack(String target, String place) {
        return new Action(Kind.ATTACK_COLONY, target, null, null, 0, 0, place);
    }

    public static Action peace(String target) {
        return new Action(Kind.MAKE_PEACE, target, null, null, 0, 0, null);
    }

    public static Action alliance(String target) {
        return new Action(Kind.ALLIANCE, target, null, null, 0, 0, null);
    }

    public static Action incite(String target, String against, int gold) {
        return new Action(Kind.INCITE, target, against, null, 0, gold, null);
    }

    public static Action sell(String target, String goods, int amount,
                              int price) {
        return new Action(Kind.SELL_GOODS, target, null, goods, amount,
                          price, null);
    }

    public static Action buy(String target, String goods, int amount,
                             int price) {
        return new Action(Kind.BUY_GOODS, target, null, goods, amount,
                          price, null);
    }

    public static Action agreement(String target, String goods,
                                   int perTurn, int price) {
        return new Action(Kind.TRADE_AGREEMENT, target, null, goods,
                          perTurn, price, null);
    }

    public static Action armTribe(String tribe, String against, String goods,
                                  int amount, int price) {
        return new Action(Kind.ARM_TRIBE, tribe, against, goods, amount,
                          price, null);
    }

    public static Action arm(int soldiers) {
        return new Action(Kind.ARM, null, null, null, soldiers, 0, null);
    }

    /**
     * Is this the same move as another, for comparing what a nation
     * did with what the diplomat proposed: same kind and target (the
     * amounts and the exact colony do not have to match).
     *
     * @param o The other {@code Action}.
     * @return True if they match.
     */
    public boolean matches(Action o) {
        return o != null && kind == o.kind
            && Objects.equals(target, o.target);
    }

    /**
     * A short label: kind, then target and details.
     *
     * @return The label.
     */
    public String label() {
        StringBuilder sb = new StringBuilder(kind.name().toLowerCase());
        if (target != null) sb.append(' ').append(shortId(target));
        if (against != null) sb.append(" vs ").append(shortId(against));
        if (goods != null) {
            sb.append(' ').append(amount).append(' ')
                .append(shortId(goods)).append(" @").append(price);
        } else if (kind == Kind.ARM) {
            sb.append(' ').append(amount);
        }
        if (place != null) sb.append(" at ").append(place);
        return sb.toString();
    }

    /**
     * The last part of a dotted id: "model.nation.french" to "french".
     *
     * @param id The id.
     * @return The short form.
     */
    public static String shortId(String id) {
        if (id == null) return "";
        int i = id.lastIndexOf('.');
        return (i < 0) ? id : id.substring(i + 1);
    }

    @Override
    public String toString() {
        return label();
    }
}
