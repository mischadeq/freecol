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

package net.sf.freecol.client.companion;

import java.util.HashMap;
import java.util.Map;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The log grouped by turn: which group a line goes in, short chips,
 * and the same warning coming back.
 */
public class LogDigestTest extends FreeColTestCase {

    public void testGroups() {
        assertEquals(LogDigest.ROUTE, LogDigest.group("load", null));
        assertEquals(LogDigest.ROUTE, LogDigest.group("unload", null));
        assertEquals(LogDigest.UNIT, LogDigest.group("arrive", null));
        assertEquals(LogDigest.WARN, LogDigest.group("msg-missing_goods",
            "model.building.notEnoughInput"));
        assertEquals(LogDigest.WARN, LogDigest.group("msg-warehouse_capacity",
            "model.colony.warehouseSoonFull"));
        // Offers are deals, whatever the message type.
        assertEquals(LogDigest.DEAL, LogDigest.group("msg-default",
            "offer.postedSell"));
        assertEquals(LogDigest.DEAL, LogDigest.group("msg-foreign_diplomacy",
            "offer.agreement"));
        assertEquals(LogDigest.NEWS, LogDigest.group("msg-sons_of_liberty",
            "model.player.soLIncrease"));
        assertEquals(LogDigest.NEWS, LogDigest.group("lost", null));
        assertEquals(LogDigest.NEWS, LogDigest.group(null, null));
    }

    public void testRepeatKeyIgnoresNumbers() {
        Map<String, String> a = new HashMap<>(), b = new HashMap<>();
        a.put("%colony%", "Nieuwegein");
        a.put("%number%", "6");
        b.put("%colony%", "Nieuwegein");
        b.put("%number%", "5");
        assertEquals(LogDigest.repeatKey("x", "building:1", a),
                     LogDigest.repeatKey("x", "building:1", b));
        b.put("%colony%", "Utrecht");
        assertFalse(LogDigest.repeatKey("x", "building:1", a)
            .equals(LogDigest.repeatKey("x", "building:1", b)));
        assertFalse(LogDigest.repeatKey("x", "building:1", a)
            .equals(LogDigest.repeatKey("x", "building:2", a)));
    }

    public void testMessageFacts() {
        Game game = getGame();
        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        ModelMessage m = new ModelMessage(ModelMessage.MessageType.MISSING_GOODS,
            "model.building.notEnoughInput", dutch)
            .addName("%building%", "Lumber mill")
            .addName("%colony%", "Nieuwegein")
            .addAmount("%number%", 6)
            .addName("%goodsType%", "Lumber");
        Map<String, String> f = CompanionState.messageFacts("msg-missing_goods", m, 1);
        assertEquals(LogDigest.WARN, f.get("group"));
        assertEquals("Nieuwegein Lumber mill: short 6 Lumber", f.get("chip"));
        // Next turn, 5 short: the same warning.
        ModelMessage m2 = new ModelMessage(ModelMessage.MessageType.MISSING_GOODS,
            "model.building.notEnoughInput", dutch)
            .addName("%building%", "Lumber mill")
            .addName("%colony%", "Nieuwegein")
            .addAmount("%number%", 5)
            .addName("%goodsType%", "Lumber");
        assertEquals(f.get("repeat"),
            CompanionState.messageFacts("msg-missing_goods", m2, 1).get("repeat"));
        // Repeated within the turn: the count goes on the chip.
        assertEquals("Nieuwegein Lumber mill: short 6 Lumber (×2)",
            CompanionState.messageFacts("msg-missing_goods", m, 2).get("chip"));
        // A message without a short form keeps its sentence.
        ModelMessage plain = new ModelMessage(ModelMessage.MessageType.DEFAULT,
            "model.player.dead.european", dutch);
        assertNull(CompanionState.messageFacts("msg-default", plain, 1).get("chip"));
    }
}
