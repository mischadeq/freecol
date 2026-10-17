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


/**
 * An offer a computer nation makes: peace terms (by messenger, to one
 * nation), or a public notice "wanted" / "for sale" on the notice
 * board, shown first to the nation it likes most, then to every nation
 * it does not dislike.  Whoever accepts brings the goods (or fetches
 * them) by ship before the deadline.
 */
public final class Offer {

    /** Turns before the others in the audience see a notice. */
    public static final int FIRST_LOOK = 3;
    /** Turns a notice stays up if nobody takes it. */
    public static final int OPEN_TURNS = 10;
    /** Turns peace terms stay on the table. */
    public static final int PEACE_TURNS = 5;
    /** Shortest and longest delivery time. */
    public static final int MIN_DEADLINE = 6, MAX_DEADLINE = 15;
    /** A late delivery is still taken up to this share of the deadline over. */
    public static final double LATE_SHARE = 0.5;
    /** ... at this share of the price. */
    public static final double LATE_PRICE = 0.75;

    public enum Kind {
        /** Peace, with gold from us if any. */
        PEACE,
        /** We want goods, and pay. */
        WANT,
        /** We sell goods. */
        SELL,
        /** Our ship brings goods to sell to one nation's colony. */
        SHIP_SELL,
        /** Our ship comes with gold to buy goods at one nation's colony. */
        SHIP_BUY,
        /** We were attacked: our ally is asked to join the war. */
        CALL_TO_ARMS,
        /** We invite a nation into an alliance (a defensive pact). */
        ALLIANCE,
        /** Join us in a war: gold now, and maybe a colony we take. */
        JOINT_WAR,
        /** Join our coalition against a nation that grows too strong. */
        COALITION,
        /** We, the target of a coalition, pay a member to leave it
            ({@code against} is the coalition's founder). */
        BUYOUT,
        /** A native settlement asks to break off from its tribe and
            join us ({@code place} is the settlement). */
        SETTLEMENT_JOINS,
        /** Our terms before war: pay {@code gold} (placeName holds the
            grievance in words). */
        ULTIMATUM,
        /** Our breakaway settlement ({@code place}) is raided by its
            tribe ({@code from}): defend it, or it goes back. */
        DEFEND_SETTLEMENT,
        /** A price agreement (step 4): we ({@code from}) buy {@code goods}
            at {@code gold} each, as many as the other brings by ship to
            our coastal colonies, for {@code deadline} turns. */
        AGREEMENT,
        /** A ship came under an agreement: how many to sell here
            ({@code amount} is the most we take, at {@code place}). */
        SALE,
        /** The player's envoy (step 8): peace, with {@code gold} from
            him if any. In the target's book, {@code from} the player. */
        ENVOY_PEACE,
        /** The player's envoy: an alliance (a defensive pact). */
        ENVOY_ALLIANCE,
        /** The player's envoy: a gift of {@code gold}. */
        ENVOY_GIFT,
        /** The player's envoy: join me in my war against {@code against}
            (step 8.2; a tribe too). */
        ENVOY_WAR,
        /** The player's envoy: leave the coalition against me (8.2). */
        ENVOY_LEAVE,
        /** Our answer to an envoy: we agree for {@code gold} in all;
            {@code goods} holds the envoy's kind. */
        TERMS;

        /** One of the player's envoys (answered on our next turn). */
        public boolean envoy() {
            return this == ENVOY_PEACE || this == ENVOY_ALLIANCE
                || this == ENVOY_GIFT || this == ENVOY_WAR
                || this == ENVOY_LEAVE;
        }

        /** Answered at once, by one nation (not a public notice). */
        public boolean direct() {
            return this == PEACE || this == CALL_TO_ARMS || this == ALLIANCE
                || this == JOINT_WAR || this == COALITION || this == BUYOUT
                || this == SETTLEMENT_JOINS || this == ULTIMATUM
                || this == DEFEND_SETTLEMENT || this == AGREEMENT
                || this == SALE || this == TERMS;
        }

        /** Answered in the game's trade screen when the ship arrives. */
        public boolean byShip() {
            return this == SHIP_SELL || this == SHIP_BUY;
        }
    }

    public enum State {
        OPEN, TAKEN, DONE, LATE, FAILED, DECLINED, EXPIRED, CANCELLED;

        public boolean over() {
            return this != OPEN && this != TAKEN;
        }
    }

    public final String id;
    public final Kind kind;
    /** The nation offering. */
    public final String from;
    /** Goods id, amount and gold (the whole price; for PEACE what we pay). */
    public final String goods;
    public final int amount, gold;
    /** The colony to deliver to or fetch from (id), and its name. */
    public final String place, placeName;
    public final int posted;
    /** Turns to deliver once taken. */
    public final int deadline;
    /** The nation a war offer is against (CALL_TO_ARMS, JOINT_WAR,
        COALITION), or null. */
    public String against;
    /** Who may see it, liked most first (for PEACE only the enemy). */
    public final List<String> audience = new ArrayList<>();

    public State state = State.OPEN;
    /** Who took it, and when. */
    public String takenBy;
    public int takenTurn = -1;
    /** The gold actually paid (late deliveries pay less). */
    public int paid;
    /** Audience members told so far (by a popup). */
    public final List<String> told = new ArrayList<>();
    /** Goods delivered so far (agreements, deliveries in parts). */
    public int delivered;


    public Offer(String id, Kind kind, String from, String goods, int amount,
                 int gold, String place, String placeName, int posted,
                 int deadline) {
        this.id = id;
        this.kind = kind;
        this.from = from;
        this.goods = goods;
        this.amount = amount;
        this.gold = gold;
        this.place = place;
        this.placeName = (placeName == null) ? "" : placeName;
        this.posted = posted;
        this.deadline = deadline;
    }

    /**
     * Delivery time for a sailing time.
     *
     * @param sailTurns Turns a ship needs to get there.
     * @return Twice the sailing time plus a few turns to get ready,
     *     between {@link #MIN_DEADLINE} and {@link #MAX_DEADLINE}.
     */
    public static int deadlineFor(int sailTurns) {
        return Math.max(MIN_DEADLINE, Math.min(MAX_DEADLINE,
                                               2 * Math.max(0, sailTurns) + 4));
    }

    /**
     * Can a nation see (and take) this offer now?
     *
     * @param nation The nation id.
     * @param now The turn.
     * @return True if so.
     */
    public boolean visibleTo(String nation, int now) {
        if (state != State.OPEN || kind.byShip()) return false;
        final int i = audience.indexOf(nation);
        if (i < 0) return false;
        return i == 0 || kind.direct() || now - posted >= FIRST_LOOK;
    }

    /** Turns until it is due, once taken (negative when late). */
    public int turnsLeft(int now) {
        return (takenTurn < 0) ? deadline : takenTurn + deadline - now;
    }

    /**
     * What a delivery now is worth.
     *
     * @param now The turn.
     * @return The full price on time, less when a little late, -1 when
     *     too late (the deal is off).
     */
    public int priceAt(int now) {
        final int over = -turnsLeft(now);
        if (over <= 0) return gold;
        if (over <= Math.ceil(LATE_SHARE * deadline)) {
            return (int)Math.round(LATE_PRICE * gold);
        }
        return -1;
    }

    /** Too late to deliver? */
    public boolean tooLate(int now) {
        return state == State.TAKEN && priceAt(now) < 0;
    }

    /** Nobody took it in time (or peace terms ran out). */
    public boolean stale(int now) {
        return state == State.OPEN && !kind.byShip()
            && now - posted >= ((kind.direct()) ? PEACE_TURNS : OPEN_TURNS);
    }


    // Serialization: fields separated by '|', lists by ','.

    public String serialize() {
        return String.join("|", id, kind.name(), from, nz(goods),
            String.valueOf(amount), String.valueOf(gold), nz(place),
            placeName.replace('|', ' ').replace(';', ' '),
            String.valueOf(posted), String.valueOf(deadline),
            String.join(",", audience), state.name(), nz(takenBy),
            String.valueOf(takenTurn), String.valueOf(paid),
            String.join(",", told), nz(against), String.valueOf(delivered));
    }

    private static String nz(String s) {
        return (s == null) ? "" : s;
    }

    private static String nul(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    /**
     * Read an offer back.
     *
     * @param s The line from {@link #serialize}.
     * @return The {@code Offer}, or null if it can not be read.
     */
    public static Offer parse(String s) {
        final String[] f = s.split("\\|", -1);
        if (f.length < 16 || f.length > 18) return null;
        try {
            Offer o = new Offer(f[0], Kind.valueOf(f[1]), f[2], nul(f[3]),
                Integer.parseInt(f[4]), Integer.parseInt(f[5]), nul(f[6]),
                f[7], Integer.parseInt(f[8]), Integer.parseInt(f[9]));
            if (!f[10].isEmpty()) {
                for (String a : f[10].split(",")) o.audience.add(a);
            }
            o.state = State.valueOf(f[11]);
            o.takenBy = nul(f[12]);
            o.takenTurn = Integer.parseInt(f[13]);
            o.paid = Integer.parseInt(f[14]);
            if (!f[15].isEmpty()) {
                for (String a : f[15].split(",")) o.told.add(a);
            }
            if (f.length > 16) o.against = nul(f[16]);
            if (f.length > 17) o.delivered = Integer.parseInt(f[17]);
            return o;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return id + " " + kind + " " + amount + " " + Action.shortId(goods)
            + " " + gold + "g " + state;
    }
}
