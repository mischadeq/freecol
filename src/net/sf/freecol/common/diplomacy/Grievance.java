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
 * A grievance (mod, step 6): no war out of nowhere.  A nation that
 * wants war first complains, then sets terms, then declares war.
 */
public final class Grievance {

    /** Turns a complaint stands before the ultimatum. */
    public static final int COMPLAINT_TURNS = 3;
    /** Turns to answer an ultimatum. */
    public static final int ULTIMATUM_TURNS = 3;
    /** Turns without a new grievance after the terms were met. */
    public static final int SETTLED_TURNS = 15;
    /** Turns an open grievance lasts once the reason for war is gone. */
    public static final int STALE_TURNS = 12;
    /** The least and most gold demanded. */
    public static final int MIN_DEMAND = 200, MAX_DEMAND = 2000;

    public enum Cause {
        SOLDIERS("your soldiers stand near our colonies"),
        HARM("you have harmed us"),
        CROWDING("your colonies crowd our land"),
        RIVALRY("you stand in the way of our plans"),
        /** A ship loan left unpaid after the ship was seized (mod). */
        DEBT("you owe us for a ship loan");

        public final String words;

        Cause(String words) {
            this.words = words;
        }
    }

    public enum Stage { COMPLAINT, ULTIMATUM, SETTLED, WAR }

    /** The nation it is against. */
    public final String against;
    public final Cause cause;
    public Stage stage;
    /** The turn the current stage began. */
    public int since;
    /** The gold demanded in the ultimatum. */
    public int demand;
    /** The turn the grievance began. */
    public final int opened;


    public Grievance(String against, Cause cause, int opened) {
        this.against = against;
        this.cause = cause;
        this.opened = opened;
        this.stage = Stage.COMPLAINT;
        this.since = opened;
    }

    /**
     * The gold to demand: a quarter of theirs, rounded to 50.
     *
     * @param theirGold Their gold (public in the Foreign Affairs report).
     * @return The demand.
     */
    public static int demandFor(int theirGold) {
        final int d = (int)Math.round(Math.max(0, theirGold) / 4.0 / 50.0) * 50;
        return Math.max(MIN_DEMAND, Math.min(MAX_DEMAND, d));
    }

    /**
     * Turns left in the current stage.
     *
     * @param now The turn.
     * @return The turns, 0 when it is over.
     */
    public int turnsLeft(int now) {
        final int len = (stage == Stage.COMPLAINT) ? COMPLAINT_TURNS
            : (stage == Stage.ULTIMATUM) ? ULTIMATUM_TURNS
            : (stage == Stage.SETTLED) ? SETTLED_TURNS : 0;
        return Math.max(0, since + len - now);
    }

    /** Settled lately: no new grievance yet. */
    public boolean settled(int now) {
        return stage == Stage.SETTLED && turnsLeft(now) > 0;
    }

    /** An open grievance, or a settled one, that no longer counts. */
    public boolean stale(int now) {
        return (stage == Stage.SETTLED && turnsLeft(now) == 0)
            || ((stage == Stage.COMPLAINT || stage == Stage.ULTIMATUM)
                && now - since >= STALE_TURNS);
    }

    public String serialize() {
        return against + ":" + cause.name() + ":" + stage.name() + ":"
            + since + ":" + demand + ":" + opened;
    }

    public static Grievance parse(String s) {
        final String[] f = s.split(":", -1);
        if (f.length != 6) return null;
        try {
            Grievance g = new Grievance(f[0], Cause.valueOf(f[1]),
                                        Integer.parseInt(f[5]));
            g.stage = Stage.valueOf(f[2]);
            g.since = Integer.parseInt(f[3]);
            g.demand = Integer.parseInt(f[4]);
            return g;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return against + " " + cause + " " + stage + " " + demand;
    }
}
