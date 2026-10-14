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
import java.util.Map;


/**
 * Short-term goals: what a nation is after right now.  Each turn the
 * triggers are checked; a goal gives +30 to the moves that serve it.
 */
public final class Goal {

    /** Bonus for a move that serves a short-term goal. */
    public static final double GOAL_BONUS = 30;
    /** Bonus for a move the long-term ambition favours. */
    public static final double AMBITION_BONUS = 15;

    /** Opinion at or below which an old grudge becomes a goal. */
    public static final double REVENGE_MEMORY = -40;

    public enum Type {
        SELL_SURPLUS, GET_GOODS, TAKE_COLONY, DEFEND, CONTAIN_LEADER,
        MAKE_PEACE, REVENGE
    }

    public final Type type;
    /** The nation or goods it is about, or null. */
    public final String target;


    public Goal(Type type, String target) {
        this.type = type;
        this.target = target;
    }

    /**
     * Does a move serve this goal?
     *
     * @param a The {@code Action}.
     * @return True if it does.
     */
    public boolean servedBy(Action a) {
        switch (type) {
        case SELL_SURPLUS:
            return (a.kind == Action.Kind.SELL_GOODS
                    || a.kind == Action.Kind.TRADE_AGREEMENT)
                && target.equals(a.goods);
        case GET_GOODS:
            return a.kind == Action.Kind.BUY_GOODS && target.equals(a.goods);
        case TAKE_COLONY:
            return a.kind == Action.Kind.ATTACK_COLONY
                && target.equals(a.target);
        case DEFEND:
            return a.kind == Action.Kind.ARM
                || a.kind == Action.Kind.ALLIANCE
                || (a.kind == Action.Kind.MAKE_PEACE
                    && target.equals(a.target));
        case CONTAIN_LEADER:
            return (a.kind == Action.Kind.ALLIANCE && !target.equals(a.target))
                || (a.kind == Action.Kind.INCITE && target.equals(a.against))
                || (a.kind == Action.Kind.DECLARE_WAR && target.equals(a.target));
        case MAKE_PEACE:
            return a.kind == Action.Kind.MAKE_PEACE && target.equals(a.target);
        case REVENGE:
            return ((a.kind == Action.Kind.DECLARE_WAR
                     || a.kind == Action.Kind.ATTACK_COLONY)
                    && target.equals(a.target))
                || (a.kind == Action.Kind.INCITE && target.equals(a.against));
        default:
            return false;
        }
    }

    /**
     * The goals that are active now.
     *
     * @param w The {@code WorldView}.
     * @param takeChance Nation id to the best chance of taking one of
     *     its places (only nations at war matter).
     * @return The active goals.
     */
    public static List<Goal> active(WorldView w,
                                    Map<String, Double> takeChance) {
        List<Goal> ret = new ArrayList<>();
        for (GoodsView g : w.goods.values()) {
            if (g.surplus()) ret.add(new Goal(Type.SELL_SURPLUS, g.id));
            if (isWarGood(g.id) || "model.goods.tools".equals(g.id)) {
                if (g.short3()) ret.add(new Goal(Type.GET_GOODS, g.id));
            }
        }
        final double army = w.me.army;
        for (NationView v : w.others.values()) {
            if (!v.atWar()) continue;
            Double p = takeChance.get(v.id);
            if (p != null && p >= 0.5) ret.add(new Goal(Type.TAKE_COLONY, v.id));
            if (w.enemiesAtGates.contains(v.id)) {
                ret.add(new Goal(Type.DEFEND, v.id));
            }
            if (w.exposure() > army || warTurns(w, v.id) >= 20) {
                ret.add(new Goal(Type.MAKE_PEACE, v.id));
            }
        }
        String leader = Threat.leaderToContain(w);
        if (leader != null) ret.add(new Goal(Type.CONTAIN_LEADER, leader));
        for (NationView v : w.others.values()) {
            double grudge = 0;
            for (Memory m : w.memories) {
                if (v.id.equals(m.about) && w.turn - m.turn <= 30) {
                    grudge += m.value(w.turn);
                }
            }
            if (grudge <= REVENGE_MEMORY) ret.add(new Goal(Type.REVENGE, v.id));
        }
        return ret;
    }

    private static boolean isWarGood(String id) {
        return "model.goods.muskets".equals(id)
            || "model.goods.horses".equals(id);
    }

    /** Turns since the last war memory about a nation (0 if none). */
    private static int warTurns(WorldView w, String id) {
        int last = -1;
        for (Memory m : w.memories) {
            if (m.kind == Memory.Kind.WAR && id.equals(m.about)) {
                last = Math.max(last, m.turn);
            }
        }
        return (last < 0) ? 0 : w.turn - last;
    }

    @Override
    public String toString() {
        return type.name().toLowerCase()
            + ((target == null) ? "" : " " + Action.shortId(target));
    }
}
