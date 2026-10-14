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

import junit.framework.TestCase;
import net.sf.freecol.common.model.Stance;


/**
 * The player's offers are judged with the diplomat's own values.
 */
public class DealValueTest extends TestCase {

    private static final String MUSKETS = "model.goods.muskets",
        FURS = "model.goods.furs", ENGLISH = "model.nation.english";

    private static WorldView world() {
        WorldView w = new WorldView(100, new NationView("model.nation.dutch",
            true, true), NationCharacter.SETTLER);
        w.tax = 20;
        // Muskets: running out in 2 turns.  Furs: boycotted.
        w.goods.put(MUSKETS, new GoodsView(MUSKETS, 50, -30, 0, 2, 8, 10, false));
        w.goods.put(FURS, new GoodsView(FURS, 300, 10, 0, Integer.MAX_VALUE,
                                        5, 7, true));
        return w;
    }

    public void testGoods() {
        WorldView w = world();
        // Getting goods we are short of: worth more than their price.
        assertEquals(1500, DealValue.goods(w, MUSKETS, 100, true));
        // Giving them: what they would fetch in Europe after tax.
        assertEquals(-640, DealValue.goods(w, MUSKETS, 100, false));
        // Boycotted goods cost us nothing to give.
        assertEquals(0, DealValue.goods(w, FURS, 100, false));
        assertEquals(DealValue.NO_OPINION,
                     DealValue.goods(w, "model.goods.silver", 100, true));
    }

    public void testStance() {
        List<ScoredAction> ranked = new ArrayList<>();
        ScoredAction peace = new ScoredAction(Action.peace(ENGLISH));
        peace.total = 12;
        ranked.add(peace);
        assertEquals(1200, DealValue.stance(ranked, ENGLISH, Stance.PEACE, Stance.WAR));
        assertEquals(1200, DealValue.stance(ranked, ENGLISH, Stance.CEASE_FIRE, Stance.WAR));
        assertEquals(DealValue.NO_OPINION,
                     DealValue.stance(ranked, ENGLISH, Stance.PEACE, Stance.PEACE));
        // An alliance with a nation it does not like enough: never.
        assertEquals(DealValue.NEVER,
                     DealValue.stance(ranked, ENGLISH, Stance.ALLIANCE, Stance.PEACE));
        ScoredAction ally = new ScoredAction(Action.alliance(ENGLISH));
        ally.total = -3;
        ranked.add(ally);
        assertEquals(-300, DealValue.stance(ranked, ENGLISH, Stance.ALLIANCE, Stance.PEACE));
    }
}
