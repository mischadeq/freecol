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

import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.ModelMessage.MessageType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Which turn report messages may move to the companion page.
 */
public class RoutineMessagesTest extends FreeColTestCase {

    private static boolean routine(MessageType type, Player p) {
        return InGameController.isRoutine(new ModelMessage(type, "x", p));
    }

    public void testRoutineAndImportant() {
        final Player p = getGame().getPlayerByNationId("model.nation.dutch");
        // Colony housekeeping the page shows better.
        assertTrue(routine(MessageType.MISSING_GOODS, p));
        assertTrue(routine(MessageType.WAREHOUSE_CAPACITY, p));
        assertTrue(routine(MessageType.SONS_OF_LIBERTY, p));
        assertTrue(routine(MessageType.GOVERNMENT_EFFICIENCY, p));
        assertTrue(routine(MessageType.MARKET_PRICES, p));
        // News that must stay in the game.
        assertFalse(routine(MessageType.WARNING, p));
        assertFalse(routine(MessageType.COMBAT_RESULT, p));
        assertFalse(routine(MessageType.DEMANDS, p));
        assertFalse(routine(MessageType.UNIT_LOST, p));
        assertFalse(routine(MessageType.BUILDING_COMPLETED, p));
        assertFalse(routine(MessageType.DEFAULT, p));
    }
}
