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

import junit.framework.TestCase;


/**
 * Price agreements: the price a buyer takes, its counter, room.
 */
public class PriceAgreementTest extends TestCase {

    public void testPrices() {
        assertEquals(9, PriceAgreement.net(10, 10));
        assertEquals(0, PriceAgreement.net(0, 10));
        // Up to 85% of its net price, never nothing.
        assertTrue(PriceAgreement.accepts(8, 10));
        assertFalse(PriceAgreement.accepts(9, 10));
        assertFalse(PriceAgreement.accepts(0, 10));
        assertEquals(8, PriceAgreement.counter(10));
        assertTrue("its counter it accepts",
                   PriceAgreement.accepts(PriceAgreement.counter(12), 12));
    }

    public void testRoomAndTurns() {
        assertEquals(50, PriceAgreement.room(100, 50, 10000, 2));
        assertEquals(PriceAgreement.MAX_PER_VISIT, PriceAgreement.room(500, 0, 10000, 2));
        assertEquals(10, PriceAgreement.room(500, 0, 50, 5));
        assertEquals(0, PriceAgreement.room(100, 120, 10000, 2));
        assertEquals(PriceAgreement.MIN_TURNS, PriceAgreement.turns(1));
        assertEquals(12, PriceAgreement.turns(12));
    }

    public void testDeliveredIsSaved() {
        Offer o = new Offer("agree:1", Offer.Kind.AGREEMENT, "model.nation.dutch",
            "model.goods.coats", 0, 7, null, "", 10, 20);
        o.delivered = 140;
        o.state = Offer.State.TAKEN;
        assertEquals(140, Offer.parse(o.serialize()).delivered);
        // Older offers (17 fields) still read.
        final String old = o.serialize();
        assertNotNull(Offer.parse(old.substring(0, old.lastIndexOf('|'))));
    }
}
