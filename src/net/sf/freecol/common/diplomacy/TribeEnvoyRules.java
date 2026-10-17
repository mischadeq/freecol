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
 * How a tribe judges the player's envoy (step 8.3b, mod).  Survival
 * first: a tribe that is losing makes peace humbly; one that holds its
 * own asks gold; one in a position of strength asks more, and may set
 * conditions: give back the land taken, abandon a colony on it, pull
 * the soldiers out of its borders.
 *
 * Pure rules, no game objects.
 */
public final class TribeEnvoyRules {

    /** How the war stands, as the tribe sees it. */
    public enum Standing { LOSING, EVEN, STRONG }

    /** Conditions of peace (a bit set, kept in an offer's amount);
        {@link #ALLY}: only as allies (to fight for the player, 8.2). */
    public static final int LAND = 1, COLONY = 2, TROOPS = 4, ALLY = 8;

    /** Braves per soldier at or under which a tribe is losing. */
    public static final double LOSING_RATIO = 0.67;
    /** Braves per soldier at or over which a tribe is strong. */
    public static final double STRONG_RATIO = 1.5;
    /** Tension a tribe lets go of without being paid. */
    public static final int FREE_TENSION = 300;
    /** Opinion at or under which a strong tribe wants only revenge. */
    public static final double REVENGE_LINE = -90;

    /** Why a tribe says no. */
    public enum Reason { REVENGE, NOT_NOW, TRUST }

    /**
     * The gold a tribe asks to go to war for the player (8.2): the
     * game's old price for inciting against a European nation (dear:
     * 5000, 10000 when it likes the payer less than the enemy), the
     * tribes' own price against a tribe (cheaper against an old rival).
     *
     * @param european The enemy is a European nation.
     * @param rival The enemy is the tribe's old rival.
     * @param payerTension The tribe's tension towards the player.
     * @param enemyTension Its tension towards the enemy.
     * @return The price.
     */
    public static int warPrice(boolean european, boolean rival,
                               int payerTension, int enemyTension) {
        if (european) {
            int p = (payerTension > enemyTension) ? 10000 : 5000;
            p += 20 * (payerTension - enemyTension);
            return EnvoyRules.roundUp(Math.max(650, p));
        }
        int p = (rival) ? 800 : 2000;
        p += 3 * payerTension - 2 * enemyTension;
        return EnvoyRules.roundUp(Math.max(300, p));
    }

    /**
     * No: it does not fit.
     *
     * @return The {@code Answer}.
     */
    public static Answer notNow() {
        return new Answer(EnvoyRules.Verdict.REFUSE, Reason.NOT_NOW, 0, 0,
                          Standing.EVEN);
    }

    /**
     * Judge an envoy asking a tribe to fight for the player (8.2).  An
     * ally fights his wars anyway: yes.  Otherwise: no if it does not
     * like him, else its price, and only as his ally.
     *
     * @param opinion Its opinion of the player.
     * @param allied It is allied with the player already.
     * @param atWar It is at war with the enemy already.
     * @param price Its price ({@link #warPrice}).
     * @return The {@code Answer}.
     */
    public static Answer judgeWar(double opinion, boolean allied,
                                  boolean atWar, int price) {
        if (allied || atWar) {
            return new Answer(EnvoyRules.Verdict.ACCEPT, null, 0, 0,
                              Standing.EVEN);
        }
        if (opinion < 0) {
            return new Answer(EnvoyRules.Verdict.REFUSE, Reason.TRUST, 0, 0,
                              Standing.EVEN);
        }
        return new Answer(EnvoyRules.Verdict.COUNTER, null, price, ALLY,
                          Standing.EVEN);
    }

    /** What the tribe brings to the decision. */
    public static final class Input {
        public Offer.Kind kind;
        public int gold;
        /** The tribe's braves. */
        public int braves;
        /** The player's soldiers near the tribe's villages. */
        public int soldiers;
        /** The player destroyed one of its villages lately. */
        public boolean lostVillage;
        /** The tribe's tension towards the player. */
        public int tension;
        /** Its opinion of the player, -100..100. */
        public double opinion;
        /** The player holds land taken from it. */
        public boolean takenLand;
        /** A colony of the player stands on land taken from it. */
        public boolean colonyOnTakenLand;
        /** Soldiers of the player stand inside its borders. */
        public boolean troopsInside;
        /** The proposal fits the stance (peace only at war). */
        public boolean fits = true;
    }

    /** One answer. */
    public static final class Answer {
        public final EnvoyRules.Verdict verdict;
        public final Reason reason;
        public final int price;
        /** Conditions ({@link #LAND}, {@link #COLONY}, {@link #TROOPS},
            {@link #ALLY}). */
        public final int conditions;
        public final Standing standing;

        Answer(EnvoyRules.Verdict verdict, Reason reason, int price,
               int conditions, Standing standing) {
            this.verdict = verdict;
            this.reason = reason;
            this.price = price;
            this.conditions = conditions;
            this.standing = standing;
        }

        @Override
        public String toString() {
            return verdict + " " + standing
                + ((reason != null) ? " " + reason : "")
                + ((price > 0) ? " " + price + "g" : "")
                + ((conditions != 0) ? " c" + conditions : "");
        }
    }

    private TribeEnvoyRules() {}


    /**
     * How the war stands.
     *
     * @param braves The tribe's braves.
     * @param soldiers The player's soldiers near its villages.
     * @param lostVillage It lost a village to the player lately.
     * @return The {@code Standing}.
     */
    public static Standing standing(int braves, int soldiers,
                                    boolean lostVillage) {
        final double ratio = braves / (double)Math.max(1, soldiers);
        if (lostVillage || ratio <= LOSING_RATIO) return Standing.LOSING;
        return (ratio >= STRONG_RATIO) ? Standing.STRONG : Standing.EVEN;
    }

    /**
     * The gold a tribe asks for peace.
     *
     * @param tension Its tension towards the player.
     * @param st How the war stands.
     * @return The price (0 when losing).
     */
    public static int price(int tension, Standing st) {
        if (st == Standing.LOSING) return 0;
        final int base = EnvoyRules.roundUp(Math.max(0, tension - FREE_TENSION) / 2);
        return (st == Standing.STRONG) ? 2 * base : base;
    }

    /**
     * Judge an envoy to a tribe.
     *
     * @param in The {@code Input}.
     * @return The {@code Answer}.
     */
    public static Answer judge(Input in) {
        final Standing st = standing(in.braves, in.soldiers, in.lostVillage);
        if (in.kind == Offer.Kind.ENVOY_GIFT) {
            return new Answer(EnvoyRules.Verdict.ACCEPT, null, 0, 0, st);
        }
        if (!in.fits) {
            return new Answer(EnvoyRules.Verdict.REFUSE, Reason.NOT_NOW, 0, 0, st);
        }
        // Losing: survival first, peace at once.
        if (st == Standing.LOSING) {
            return new Answer(EnvoyRules.Verdict.ACCEPT, null, 0, 0, st);
        }
        if (st == Standing.STRONG && in.opinion <= REVENGE_LINE) {
            return new Answer(EnvoyRules.Verdict.REFUSE, Reason.REVENGE, 0, 0, st);
        }
        int conditions = 0;
        if (st == Standing.STRONG && in.opinion < 0) {
            if (in.takenLand) conditions |= LAND;
            if (in.colonyOnTakenLand) conditions |= COLONY;
            if (in.troopsInside) conditions |= TROOPS;
        }
        final int price = price(in.tension, st);
        if (conditions == 0 && in.gold >= price) {
            return new Answer(EnvoyRules.Verdict.ACCEPT, null, 0, 0, st);
        }
        return new Answer(EnvoyRules.Verdict.COUNTER, null, price, conditions, st);
    }
}
