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


/** The where-are-they guesses, without a game. */
public class BearingsTest extends TestCase {

    private static final String FRENCH = "model.nation.french",
        ENGLISH = "model.nation.english";


    public void testPointsTheRightWay() {
        Bearings b = new Bearings();
        // Three French sightings north of us (smaller y), one south.
        b.add(new Bearings.Sighting(FRENCH, 20, 10, 5, Bearings.Kind.LAND));
        b.add(new Bearings.Sighting(FRENCH, 22, 8, 5, Bearings.Kind.LAND));
        b.add(new Bearings.Sighting(FRENCH, 18, 12, 5, Bearings.Kind.COLONY));
        b.add(new Bearings.Sighting(FRENCH, 20, 70, 5, Bearings.Kind.SHIP));
        b.add(new Bearings.Sighting(ENGLISH, 20, 70, 5, Bearings.Kind.LAND));
        Bearings.Bearing f = b.bearing(FRENCH, 20, 40, 5);
        assertEquals("north", f.compass());
        assertEquals(4, f.sightings);
        assertTrue(f.confidence > 0.3 && f.confidence < 1);
        assertEquals("south", b.bearing(ENGLISH, 20, 40, 5).compass());
        assertNull(b.bearing("model.nation.spanish", 20, 40, 5));
        assertEquals(2, b.all(20, 40, 5).size());
        // Map rows are half a tile high: 10 rows up is 5 tiles.
        Bearings c = new Bearings();
        c.add(new Bearings.Sighting(FRENCH, 30, 20, 1, Bearings.Kind.COLONY));
        assertEquals(10.0, c.bearing(FRENCH, 20, 20, 1).distance, 1e-9);
        assertEquals(5.0, c.bearing(FRENCH, 30, 30, 1).distance, 1e-9);
    }

    public void testOldSightingsFadeAndConfidenceGrows() {
        Bearings b = new Bearings();
        b.add(new Bearings.Sighting(FRENCH, 40, 40, 1, Bearings.Kind.SHIP));
        final double one = b.bearing(FRENCH, 20, 40, 1).confidence;
        final double later = b.bearing(FRENCH, 20, 40, 15).confidence;
        assertTrue(later < one);
        assertNull(b.bearing(FRENCH, 20, 40, 1 + Bearings.FADE));
        for (int i = 0; i < 6; i++) {
            b.add(new Bearings.Sighting(FRENCH, 40, 40, 1, Bearings.Kind.SHIP));
        }
        assertEquals(1.0, b.bearing(FRENCH, 20, 40, 1).confidence, 1e-9);
        // Sightings that disagree lower the confidence.
        b.add(new Bearings.Sighting(FRENCH, 0, 40, 1, Bearings.Kind.COLONY));
        b.add(new Bearings.Sighting(FRENCH, 0, 40, 1, Bearings.Kind.COLONY));
        assertTrue(b.bearing(FRENCH, 20, 40, 1).confidence < 0.3);
    }

    public void testVisitorsAtOurDoorCountLittle() {
        Bearings b = new Bearings();
        b.add(new Bearings.Sighting(FRENCH, 22, 40, 1, Bearings.Kind.LAND));
        Bearings far = new Bearings();
        far.add(new Bearings.Sighting(FRENCH, 32, 40, 1, Bearings.Kind.LAND));
        assertTrue(b.bearing(FRENCH, 20, 40, 1).confidence
                   < far.bearing(FRENCH, 20, 40, 1).confidence / 5);
        // On top of us: no direction at all.
        Bearings on = new Bearings();
        on.add(new Bearings.Sighting(FRENCH, 20, 40, 1, Bearings.Kind.LAND));
        assertNull(on.bearing(FRENCH, 20, 40, 1));
    }

    public void testCourses() {
        // A ship sailing west, out of home: home is behind it, east.
        Bearings b = new Bearings();
        b.addCourse(FRENCH, 30, 40, 29, 40, 1, true);
        Bearings.Bearing g = b.bearing(FRENCH, 20, 40, 1);
        assertEquals("east", g.compass());
        assertEquals(30 + Bearings.COURSE_LENGTH - 20, g.distance, 1e-9);
        // The same ship going home: home is ahead of it, west.
        b = new Bearings();
        b.addCourse(FRENCH, 30, 40, 29, 40, 1, false);
        assertEquals("west", b.bearing(FRENCH, 40, 40, 1).compass());
        // Kept on the map.
        b = new Bearings();
        b.addCourse(FRENCH, 3, 5, 2, 4, 1, false, 40, 100);
        assertEquals("0,0,1,COURSE", b.serialize().trim()
                     .substring(FRENCH.length() + 1));
        // Not moving: nothing.
        b = new Bearings();
        b.addCourse(FRENCH, 3, 5, 3, 5, 1, true);
        assertEquals(0, b.size());
    }

    public void testCompass() {
        assertEquals("east", Bearings.compass(1, 0));
        assertEquals("south", Bearings.compass(0, 1));
        assertEquals("north", Bearings.compass(0, -1));
        assertEquals("north-west", Bearings.compass(-1, -1));
        assertEquals("south-east", Bearings.compass(1, 1));
        assertEquals("west", Bearings.compass(-1, 0.1));
    }

    public void testSerializeRoundTrip() {
        Bearings b = new Bearings();
        b.add(new Bearings.Sighting(FRENCH, 1, 2, 3, Bearings.Kind.COLONY));
        b.add(new Bearings.Sighting(ENGLISH, 4, 5, 6, Bearings.Kind.COURSE));
        Bearings c = Bearings.parse(b.serialize() + "junk\nx,1,2,3,NOPE\n");
        assertEquals(b.serialize(), c.serialize());
        assertEquals(2, c.size());
        assertEquals(0, Bearings.parse(null).size());
        // In one line, for a save file attribute.
        assertFalse(b.serialize(';').contains("\n"));
        assertEquals(b.serialize(), Bearings.parse(b.serialize(';')).serialize());
        // Forgetting a replayed turn.
        c.forget(6);
        assertEquals(1, c.size());
    }

    public void testKeepsOnlyTheNewest() {
        Bearings b = new Bearings();
        for (int i = 0; i < Bearings.KEEP + 10; i++) {
            b.add(new Bearings.Sighting(FRENCH, i, 0, i, Bearings.Kind.LAND));
        }
        assertEquals(Bearings.KEEP, b.size());
        assertTrue(b.serialize().startsWith(FRENCH + ",10,"));
    }
}
