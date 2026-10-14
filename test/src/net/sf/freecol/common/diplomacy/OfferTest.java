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
 * Offers: who sees them when, deadlines, late deliveries, saving.
 */
public class OfferTest extends TestCase {

    private static final String SPANISH = "model.nation.spanish",
        DUTCH = "model.nation.dutch", FRENCH = "model.nation.french";


    private static Offer want(int posted, int deadline) {
        Offer o = new Offer("offer:" + SPANISH + ":" + posted, Offer.Kind.WANT,
            SPANISH, "model.goods.muskets", 100, 600, "colony:1", "Havana",
            posted, deadline);
        o.audience.add(FRENCH);
        o.audience.add(DUTCH);
        return o;
    }

    public void testDeadline() {
        assertEquals(Offer.MIN_DEADLINE, Offer.deadlineFor(0));
        assertEquals(10, Offer.deadlineFor(3));
        assertEquals(Offer.MAX_DEADLINE, Offer.deadlineFor(20));
    }

    public void testMostLikedSeesItFirst() {
        Offer o = want(100, 8);
        assertTrue(o.visibleTo(FRENCH, 100));
        assertFalse(o.visibleTo(DUTCH, 100));
        assertFalse(o.visibleTo(DUTCH, 100 + Offer.FIRST_LOOK - 1));
        assertTrue(o.visibleTo(DUTCH, 100 + Offer.FIRST_LOOK));
        assertFalse("not in the audience", o.visibleTo("model.nation.english", 120));
        o.state = Offer.State.TAKEN;
        assertFalse("taken", o.visibleTo(FRENCH, 101));
    }

    public void testLateCostsGoldVeryLateEndsTheDeal() {
        Offer o = want(100, 8);
        o.state = Offer.State.TAKEN;
        o.takenTurn = 102;
        assertEquals(8, o.turnsLeft(102));
        assertEquals(600, o.priceAt(110)); // due
        assertEquals(450, o.priceAt(111)); // a little late: 25% off
        assertEquals(450, o.priceAt(114)); // up to half the deadline over
        assertEquals(-1, o.priceAt(115));
        assertFalse(o.tooLate(114));
        assertTrue(o.tooLate(115));
    }

    public void testStale() {
        Offer o = want(100, 8);
        assertFalse(o.stale(100 + Offer.OPEN_TURNS - 1));
        assertTrue(o.stale(100 + Offer.OPEN_TURNS));
        Offer p = new Offer("p", Offer.Kind.PEACE, SPANISH, null, 0, 300,
                            null, "", 100, 0);
        p.audience.add(DUTCH);
        assertTrue("peace at once", p.visibleTo(DUTCH, 100));
        assertTrue(p.stale(100 + Offer.PEACE_TURNS));
    }

    public void testSerializeAndParse() {
        Offer o = want(100, 8);
        o.state = Offer.State.TAKEN;
        o.takenBy = DUTCH;
        o.takenTurn = 103;
        o.told.add(DUTCH);
        Offer p = Offer.parse(o.serialize());
        assertEquals(o.serialize(), p.serialize());
        assertEquals("Havana", p.placeName);
        assertEquals(DUTCH, p.takenBy);
        Offer q = Offer.parse(new Offer("p", Offer.Kind.PEACE, SPANISH, null,
            0, 0, null, "", 1, 0).serialize());
        assertNull(q.goods);
        assertNull(q.place);
        assertNull(Offer.parse("junk"));
    }
}
