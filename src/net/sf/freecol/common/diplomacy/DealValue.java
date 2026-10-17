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

import java.util.List;

import net.sf.freecol.common.model.Stance;


/**
 * What the items of a deal someone offers us are worth, in gold, with
 * the same values the diplomat uses for its own offers: goods at what
 * they would fetch or cost in our Europe market (more when we are short
 * of them, nothing for boycotted goods we give away), peace and
 * alliances at the diplomat's score (a point is 100 gold).
 */
public final class DealValue {

    /** Gold per point of the diplomat's score. */
    public static final int GOLD_PER_POINT = 100;
    /** No view: let the old rule decide. */
    public static final int NO_OPINION = Integer.MIN_VALUE;

    private DealValue() {}


    /**
     * Goods in a deal.
     *
     * @param w Our {@code WorldView}.
     * @param goodsId The goods.
     * @param amount How many.
     * @param weGet True if we get them, false if we give them.
     * @return Their worth to us (negative when we give), or
     *     {@link #NO_OPINION} for goods we know nothing of.
     */
    public static int goods(WorldView w, String goodsId, int amount,
                            boolean weGet) {
        final GoodsView g = w.goods.get(goodsId);
        if (g == null) return NO_OPINION;
        if (weGet) {
            final double worth = ((g.short3()) ? Effects.NEEDED_VALUE : 1.0)
                * g.buyPrice;
            return (int)Math.round(amount * worth);
        }
        final double alt = (g.boycotted) ? 0
            : g.sellPrice * (1 - w.tax / 100.0);
        return -(int)Math.round(amount * alt);
    }

    /**
     * A change of stance between us and the other side.
     *
     * @param ranked Our diplomat's moves.
     * @param other The other nation.
     * @param stance The stance offered.
     * @param current Our stance towards it now.
     * @return Its worth in gold, {@link #NO_OPINION} if the diplomat has
     *     none, or {@link #NEVER} for an alliance the diplomat would
     *     never make (not a candidate: the other is too little liked).
     */
    public static int stance(List<ScoredAction> ranked, String other,
                             Stance stance, Stance current) {
        final Action.Kind kind;
        switch (stance) {
        case PEACE: case CEASE_FIRE:
            if (current != Stance.WAR && current != Stance.CEASE_FIRE) {
                return NO_OPINION;
            }
            kind = Action.Kind.MAKE_PEACE;
            break;
        case ALLIANCE:
            if (current != Stance.PEACE) return NO_OPINION;
            kind = Action.Kind.ALLIANCE;
            break;
        default:
            return NO_OPINION;
        }
        for (ScoredAction sa : ranked) {
            if (sa.action.kind == kind && other.equals(sa.action.target)) {
                return (int)Math.round(sa.total * GOLD_PER_POINT);
            }
        }
        return (kind == Action.Kind.ALLIANCE) ? NEVER : NO_OPINION;
    }

    /**
     * What declaring war on a nation is worth to the diplomat (8.2: the
     * player asks it to join his war), in gold.
     *
     * @param ranked The diplomat's moves.
     * @param enemy The nation to fight.
     * @return The worth, or {@link #NO_OPINION} if not a candidate.
     */
    public static int war(List<ScoredAction> ranked, String enemy) {
        for (ScoredAction sa : ranked) {
            if (sa.action.kind == Action.Kind.DECLARE_WAR
                && enemy.equals(sa.action.target)) {
                return (int)Math.round(sa.total * GOLD_PER_POINT);
            }
        }
        return NO_OPINION;
    }

    /** An alliance we would never make. */
    public static final int NEVER = Integer.MIN_VALUE + 1;
}
