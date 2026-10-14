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

import java.util.Collections;
import java.util.List;


/**
 * A nation's outlook for one turn: its world view, the situation and
 * the diplomat's ranking of every move.  Built once a turn and shared
 * by everything that acts on the diplomat's word.
 */
public final class Outlook {

    public final WorldView view;
    public final Situation situation;
    /** Every move, best first. */
    public final List<ScoredAction> ranked;


    public Outlook(WorldView view) {
        this.view = view;
        this.situation = new Situation(view);
        this.ranked = Collections.unmodifiableList(Diplomat.rank(situation));
    }
}
