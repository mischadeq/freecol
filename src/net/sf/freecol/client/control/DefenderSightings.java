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

package net.sf.freecol.client.control;

import java.util.HashMap;
import java.util.Map;

import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Unit;


/**
 * The defenders the player saw at other nations' settlements (mod):
 * the unit that fought for a settlement, or came out of it to attack,
 * shown on the pre-combat screen as "last seen there".  Only what was
 * seen in battle: no peeking inside.  Kept while the game runs.
 */
public final class DefenderSightings {

    /** One sighting. */
    public static final class Seen {
        public final String typeId, roleId, label;
        public final int turn;

        Seen(String typeId, String roleId, String label, int turn) {
            this.typeId = typeId;
            this.roleId = roleId;
            this.label = label;
            this.turn = turn;
        }
    }

    private final Map<String, Seen> bySettlement = new HashMap<>();


    /**
     * Note a unit seen fighting for (or from) a settlement.
     *
     * @param settlement The {@code Settlement}.
     * @param unit The {@code Unit} seen.
     * @param label What to call it ("Russian Veteran Dragoon").
     * @param turn The turn.
     */
    public synchronized void note(Settlement settlement, Unit unit,
                                  String label, int turn) {
        if (settlement == null || unit == null || unit.getType() == null) return;
        this.bySettlement.put(settlement.getId(), new Seen(
            unit.getType().getId(),
            (unit.getRole() == null) ? null : unit.getRole().getId(),
            label, turn));
    }

    /**
     * The last unit seen at a settlement.
     *
     * @param settlement The {@code Settlement}.
     * @return The sighting, or null if none.
     */
    public synchronized Seen last(Settlement settlement) {
        return (settlement == null) ? null
            : this.bySettlement.get(settlement.getId());
    }
}
