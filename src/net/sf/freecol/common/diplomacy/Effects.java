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

import java.util.Locale;

import net.sf.freecol.common.model.Stance;


/**
 * The expected effect of each kind of move, in points over the next
 * {@link #HORIZON} turns: 100 gold is 1 point, a colony 40 + 3 per
 * colonist, an enemy soldier that can reach us -5.
 */
public final class Effects {

    /** Turns looked ahead. */
    public static final int HORIZON = 10;
    /** Points per enemy soldier that can reach us. */
    public static final double SOLDIER_POINTS = 5;
    /** Share of output lost to a war each turn. */
    public static final double WAR_COST = 0.01;
    /** Gold paid to incite a war. */
    public static final int INCITE_PRICE = 1000;
    /**
     * Trade counts for itself (contacts, goodwill, a market kept open)
     * at this share of the gold that changes hands; the gold itself is
     * already in the gold part.
     */
    public static final double TRADE_SHARE = 0.25;
    /** Offer at this share of our Europe price; buy at this markup. */
    public static final double SELL_SHARE = 0.8, BUY_MARKUP = 1.2;
    /** What goods about to run out are worth, times their price. */
    public static final double NEEDED_VALUE = 1.5;
    /** Treasure guessed per inhabitant of a destroyed native settlement. */
    public static final int PLUNDER_PER_NATIVE = 100;
    /** Rebels (percent) from which the King's army is a reason to arm. */
    public static final int KING_REBELS = 20;
    /** Turns a boycott is counted for (it lasts until arrears are paid). */
    public static final int BOYCOTT_TURNS = 20;
    /** Turns freed land is counted for (land stays). */
    public static final int LAND_HORIZON = 30;
    /** Price to stay out of a King's war: per enemy soldier, at least. */
    public static final int KING_PRICE_PER_SOLDIER = 100, KING_MIN_PRICE = 500;
    /** Muskets to arm one soldier. */
    public static final int MUSKETS_PER_SOLDIER = 50;

    private Effects() {}


    /**
     * Work out the expected effect of a move.
     *
     * @param a The {@code Action}.
     * @param s The {@code Situation}.
     * @return The move with its parts filled in (not yet totalled).
     */
    public static ScoredAction of(Action a, Situation s) {
        final WorldView w = s.w;
        final ScoredAction sa = new ScoredAction(a);
        final NationView x = (a.target == null) ? null : w.others.get(a.target);
        switch (a.kind) {
        case NOTHING:
            break;
        case DECLARE_WAR: {
            Situation.Attack at = s.attacks.get(x.id);
            if (at != null) {
                sa.land = at.expected()
                    + at.result.chance * at.place.freed;
                sa.gold += at.result.chance * plunder(at.place, x) / 100;
                sa.risk = (1 - at.result.chance) * at.result.lost
                    * SOLDIER_POINTS;
                sa.note = String.format(Locale.ROOT, "%s %.0f%%",
                    at.place.name, 100 * at.result.chance);
            } else {
                sa.note = "nothing in reach";
            }
            sa.gold += -WAR_COST * w.me.output * HORIZON / 100
                - lostTrade(x);
            sa.trade = -TRADE_SHARE * lostTrade(x);
            sa.security = -SOLDIER_POINTS * threatOf(x);
            sa.relations = -10 - 3 * alliesOf(w, x.id);
            sa.risk += counterRisk(s, x.id);
            break;
        }
        case ATTACK_COLONY: {
            Situation.Attack at = s.attacks.get(x.id);
            if (at != null) {
                sa.land = at.expected()
                    + at.result.chance * at.place.freed;
                sa.gold = at.result.chance * plunder(at.place, x) / 100;
                sa.risk = (1 - at.result.chance) * at.result.lost
                    * SOLDIER_POINTS;
                sa.note = String.format(Locale.ROOT, "%s %.0f%%",
                    at.place.name, 100 * at.result.chance);
            }
            sa.relations = -3;
            break;
        }
        case MAKE_PEACE: {
            Situation.Attack at = s.attacks.get(x.id);
            sa.security = SOLDIER_POINTS * threatOf(x);
            sa.gold = WAR_COST * w.me.output * HORIZON / 100;
            sa.land = (at == null) ? 0 : -at.expected();
            sa.relations = 5;
            break;
        }
        case ALLIANCE: {
            boolean shared = false;
            double drag = 0;
            for (NationView y : w.others.values()) {
                if (y == x) continue;
                boolean theirEnemy = w.stanceBetween(x.id, y.id) == Stance.WAR;
                if (theirEnemy && y.atWar()) shared = true;
                else if (theirEnemy) drag += y.army * y.near;
            }
            sa.security = ((shared) ? SOLDIER_POINTS / 2 * x.army : 0)
                - 0.3 * SOLDIER_POINTS * drag;
            sa.relations = 5;
            sa.risk = (1 - x.trust / 100) * 10;
            sa.note = (shared) ? "shared enemy" : "";
            break;
        }
        case INCITE: {
            final NationView z = w.others.get(a.against);
            sa.gold = -a.price / 100.0;
            sa.security = SOLDIER_POINTS / 2 * Math.min(x.army, z.army) * z.near;
            sa.relations = -0.3 * 5;
            sa.risk = a.price / 100.0 * (1 - x.trust / 100);
            break;
        }
        case SELL_GOODS: {
            final GoodsView g = w.goods.get(a.goods);
            final double p = accept(x);
            final double alt = (g.boycotted) ? 0
                : g.sellPrice * (1 - w.tax / 100.0);
            sa.gold = p * a.amount * (a.price - alt) / 100;
            sa.trade = TRADE_SHARE * p * a.amount * a.price / 100;
            sa.relations = 2 * p;
            sa.risk = 1 - p;
            break;
        }
        case BUY_GOODS: {
            final GoodsView g = w.goods.get(a.goods);
            final double p = accept(x);
            final double worth = ((g.short3()) ? NEEDED_VALUE : 1.0)
                * g.buyPrice;
            sa.gold = p * a.amount * Math.max(0, worth - a.price) / 100;
            sa.trade = TRADE_SHARE * p * a.amount * a.price / 100;
            sa.relations = 2 * p;
            sa.risk = 1 - p;
            break;
        }
        case TRADE_AGREEMENT: {
            final GoodsView g = w.goods.get(a.goods);
            final double p = accept(x);
            final double alt = (g.boycotted) ? 0
                : g.sellPrice * (1 - w.tax / 100.0);
            final double kept = p * x.trust / 100 * HORIZON * a.amount;
            sa.gold = kept * (a.price - alt) / 100;
            sa.trade = TRADE_SHARE * kept * a.price / 100;
            sa.relations = 5 * p;
            sa.risk = (1 - x.trust / 100) * 10;
            break;
        }
        case ARM: {
            final double perColonist = (w.me.colonists == 0) ? 0
                : w.me.output / w.me.colonists;
            sa.gold = -a.amount * (MUSKETS_PER_SOLDIER * w.musketPrice
                                   + perColonist * HORIZON) / 100;
            final boolean danger = w.exposure() > 0 || w.maxFear() > 40;
            final double king = kingThreat(w);
            sa.security = a.amount * SOLDIER_POINTS
                * Math.max((danger) ? 1 : 0.3, king);
            sa.note = (danger) ? "in danger"
                : (king > 0.3) ? "the King's army" : "";
            break;
        }
        case ARM_TRIBE: {
            final NationView rival = w.others.get(a.against);
            final GoodsView g = w.goods.get(a.goods);
            final double soldiers = a.amount / (double)MUSKETS_PER_SOLDIER;
            final double alt = (g == null || g.boycotted) ? 0
                : g.sellPrice * (1 - w.tax / 100.0);
            sa.gold = a.amount * (a.price - alt) / 100;
            sa.trade = TRADE_SHARE * a.amount * a.price / 100;
            // Braves armed next to a rival keep it busy: half what our
            // own soldiers would be worth (they fight when they choose),
            // more the more we fear that rival.
            sa.security = SOLDIER_POINTS / 2 * soldiers
                * (0.3 + 0.7 * rival.fear / 100);
            sa.relations = 2 - 0.3 * 5; // the tribe pleased; the rival may hear
            sa.risk = soldiers * SOLDIER_POINTS * x.near
                * ((x.opinion < 0) ? 1 : 0.2);
            sa.note = "next to " + Action.shortId(a.against);
            break;
        }
        default:
            break;
        }
        return sa;
    }

    /**
     * Gold taken with a place: a native settlement's treasure (a guess:
     * {@link #PLUNDER_PER_NATIVE} per inhabitant), or a colony's share
     * of its owner's gold (the game hands over gold in proportion to
     * the colony's size).
     */
    static double plunder(PlaceView p, NationView owner) {
        if (p.nativeSettlement) return PLUNDER_PER_NATIVE * p.size;
        if (owner.colonists <= 0) return 0;
        return Math.max(0, owner.gold) * (double)p.size / owner.colonists;
    }

    /**
     * How much the King's army is a reason to arm, 0..1: nothing below
     * {@link #KING_REBELS}% rebels, growing to full at 30 points more,
     * and only while his army is larger than mine.
     */
    static double kingThreat(WorldView w) {
        if (w.kingArmy <= w.me.army) return 0;
        return Math.max(0, Math.min(1, (w.rebels - KING_REBELS) / 30.0));
    }

    /**
     * The gold of trade lost with a tribe at war, over the horizon (my
     * trade in the last 20 turns, halved for 10 turns), in points.
     */
    static double lostTrade(NationView x) {
        return x.tradeWithMe * HORIZON / 20.0 / 100;
    }

    /**
     * What staying out of a war the King declared would cost: a sum to
     * the King, or a boycott of my best-selling goods.  Mischa's idea
     * (2026-10-14); not in the game yet, scored so the replay can show
     * when a nation would have paid.
     *
     * @param w The {@code WorldView}.
     * @param x The enemy the King declared war on.
     * @return The cost in gold, and a description.
     */
    public static Object[] kingsWarPrice(WorldView w, NationView x) {
        final int pay = (int)Math.max(KING_MIN_PRICE,
                                      Math.round(KING_PRICE_PER_SOLDIER * x.army));
        GoodsView best = null;
        double bestValue = 0;
        for (GoodsView g : w.goods.values()) {
            if (g.boycotted || g.net <= 0) continue;
            double v = g.net * g.sellPrice * (1 - w.tax / 100.0)
                * BOYCOTT_TURNS;
            if (v > bestValue) {
                bestValue = v;
                best = g;
            }
        }
        if (best != null && bestValue < pay) {
            return new Object[] { bestValue,
                "boycott of " + Action.shortId(best.id) };
        }
        return new Object[] { (double)pay, "pay " + pay + " gold" };
    }

    /** Chance a nation accepts a trade offer, from its opinion. */
    static double accept(NationView x) {
        return Math.max(0.05, Math.min(0.95, 0.5 + x.opinion / 200));
    }

    /**
     * The army of a nation that could reach us, in soldiers' worth;
     * a tribe raids and burns rather than conquers, so its braves
     * count for {@link Situation#NATIVE_RAID_SHARE}.
     */
    static double threatOf(NationView x) {
        return x.army * x.near
            * ((x.european) ? 1.0 : Situation.NATIVE_RAID_SHARE);
    }

    /** Nations allied with x. */
    private static int alliesOf(WorldView w, String x) {
        int n = 0;
        for (NationView y : w.others.values()) {
            if (!y.id.equals(x)
                && w.stanceBetween(x, y.id) == Stance.ALLIANCE) n++;
        }
        return n;
    }

    /** Chance x takes my weakest colony, times its value. */
    private static double counterRisk(Situation s, String x) {
        Double c = s.counter.get(x);
        PlaceView weakest = s.w.weakestColony();
        return (c == null || weakest == null) ? 0 : c * weakest.value();
    }
}
