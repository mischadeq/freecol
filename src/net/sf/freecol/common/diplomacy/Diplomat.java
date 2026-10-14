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
import java.util.Collections;
import java.util.List;

import net.sf.freecol.common.model.Stance;


/**
 * The diplomat: from one nation's world view, list the moves that make
 * sense this turn, score each, and rank them.  It reads only; acting on
 * the choice is someone else's job.
 */
public final class Diplomat {

    /** A move is made only if it scores at least this. */
    public static final double THRESHOLD = 20;
    /** Weight of the opinion term. */
    public static final double OPINION_WEIGHT = 0.3;
    /** Opinion needed to propose an alliance. */
    public static final double ALLIANCE_OPINION = 20;
    /** Opinion of a nation at or below which paying others to fight it is considered. */
    public static final double INCITE_OPINION = -20;
    /** Points a move must bring by itself for the full goal bonus. */
    public static final double GOAL_SCALE = 10;
    /** Most soldiers armed in one move. */
    public static final int MAX_ARM = 10;

    private Diplomat() {}


    /**
     * Rank the moves for a nation.
     *
     * @param w Its {@code WorldView}.
     * @return The scored moves, best first ("do nothing" included).
     */
    public static List<ScoredAction> rank(WorldView w) {
        return rank(new Situation(w));
    }

    /**
     * Rank the moves, from an already worked out situation.
     *
     * @param s The {@code Situation}.
     * @return The scored moves, best first.
     */
    public static List<ScoredAction> rank(Situation s) {
        List<ScoredAction> ret = new ArrayList<>();
        for (Action a : candidates(s)) ret.add(score(a, s));
        Collections.sort(ret);
        return ret;
    }

    /**
     * The move the diplomat would make, or "do nothing".
     *
     * @param ranked The ranked moves.
     * @return The chosen move.
     */
    public static ScoredAction choose(List<ScoredAction> ranked) {
        for (ScoredAction sa : ranked) {
            if (sa.action.kind == Action.Kind.NOTHING) return sa;
            if (sa.total >= THRESHOLD) return sa;
            break;
        }
        for (ScoredAction sa : ranked) {
            if (sa.action.kind == Action.Kind.NOTHING) return sa;
        }
        return new ScoredAction(Action.nothing());
    }

    /**
     * Score one move.
     *
     * @param a The {@code Action}.
     * @param s The {@code Situation}.
     * @return The {@code ScoredAction}.
     */
    public static ScoredAction score(Action a, Situation s) {
        final WorldView w = s.w;
        ScoredAction sa = Effects.of(a, s);
        // A goal makes a good move better; it does not make a useless
        // move good: the bonus grows with what the move itself brings,
        // full from GOAL_SCALE points up.
        final double scale = Math.max(0, Math.min(1,
                sa.score(w.character).total / GOAL_SCALE));
        for (Goal g : s.goals) {
            if (g.servedBy(a)) {
                sa.goal = Goal.GOAL_BONUS * scale;
                sa.note = (sa.note.isEmpty()) ? g.toString()
                    : sa.note + "; " + g;
                break;
            }
        }
        if (w.character.favours(a.kind)) {
            sa.goal += Goal.AMBITION_BONUS * scale;
        }
        // Liking or hating a nation weighs on war, peace, alliances and
        // attacks; for trade it only changes the chance they accept
        // (see Effects.accept), it does not make a small deal big.
        if (a.target != null && a.kind.side != 0 && a.kind.political) {
            NationView x = w.others.get(a.target);
            if (x != null) sa.opinion = OPINION_WEIGHT * a.kind.side * x.opinion;
        }
        return sa.score(w.character);
    }

    /**
     * The moves worth considering this turn.
     *
     * @param s The {@code Situation}.
     * @return The candidate moves.
     */
    public static List<Action> candidates(Situation s) {
        final WorldView w = s.w;
        List<Action> ret = new ArrayList<>();
        ret.add(Action.nothing());
        final boolean european = w.me.european;
        for (NationView v : w.others.values()) {
            if (v.atWar()) {
                ret.add(Action.peace(v.id));
                Situation.Attack at = s.attacks.get(v.id);
                if (at != null) ret.add(Action.attack(v.id, at.place.name));
                continue;
            }
            ret.add(Action.war(v.id));
            if (!european || !v.european) continue;
            if (v.stance == Stance.PEACE && v.opinion >= ALLIANCE_OPINION) {
                ret.add(Action.alliance(v.id));
            }
            for (GoodsView g : w.goods.values()) {
                if (g.surplus()) {
                    int amount = Math.min(100, Math.max(g.overflow,
                        (g.boycotted) ? g.stock - 100 : 0));
                    if (amount > 0) {
                        ret.add(Action.sell(v.id, g.id, amount,
                            price(Effects.SELL_SHARE, g.sellPrice)));
                    }
                }
                if (g.short3() || (g.boycotted && g.net < 0)) {
                    ret.add(Action.buy(v.id, g.id, 100,
                        price(Effects.BUY_MARKUP, g.buyPrice)));
                }
                if (g.net >= 10 && (g.surplus() || g.boycotted)) {
                    ret.add(Action.agreement(v.id, g.id, Math.min(g.net, 50),
                        price(Effects.SELL_SHARE, g.sellPrice)));
                }
            }
        }
        if (european && w.me.gold >= Effects.INCITE_PRICE) {
            for (NationView z : w.others.values()) {
                if (!z.european || z.opinion > INCITE_OPINION) continue;
                for (NationView x : w.others.values()) {
                    if (x == z || !x.european || x.atWar()) continue;
                    if (w.stanceBetween(x.id, z.id) == Stance.WAR) continue;
                    ret.add(Action.incite(x.id, z.id, Effects.INCITE_PRICE));
                }
            }
        }
        if (european && w.armable > 0) {
            ret.add(Action.arm(Math.min(MAX_ARM, w.armable)));
        }
        return ret;
    }

    private static int price(double share, int price) {
        return Math.max(1, (int)Math.round(share * price));
    }
}
