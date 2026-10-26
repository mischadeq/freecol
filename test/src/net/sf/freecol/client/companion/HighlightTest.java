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

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;


/**
 * The important words of a log line: names and trouble bold, good news
 * green, bad news red. With lines from Mischa's game, turn 272.
 */
public class HighlightTest extends TestCase {

    /** The flags on the first occurrence of a word, 0 if plain. */
    private static int flags(String text, List<String> names, String word) {
        final int at = text.indexOf(word);
        assertTrue(word, at >= 0);
        int f = 0;
        for (int[] r : CompanionHighlight.runs(text, names)) {
            if (r[0] <= at && at + word.length() <= r[1]) f = r[2];
        }
        return f;
    }

    public void testPeaceGreenTribeBold() {
        String t = "Your Excellency, the Tupi are at peace with us!";
        List<String> n = Arrays.asList("Tupi");
        assertEquals(CompanionHighlight.BOLD, flags(t, n, "Tupi"));
        assertEquals(CompanionHighlight.GOOD, flags(t, n, "at peace"));
        assertEquals(0, flags(t, n, "Excellency"));
    }

    public void testConcernBold() {
        String t = "The Arawak chief at Xaymaca greets his friends the Dutch."
            + " We are pleased to see growing cooperation between us, but"
            + " we are concerned at the encroachments of your settlers upon"
            + " our lands.";
        List<String> n = Arrays.asList("Arawak", "Xaymaca", "Dutch");
        assertEquals(CompanionHighlight.BOLD, flags(t, n, "Xaymaca"));
        assertEquals(CompanionHighlight.BOLD, flags(t, n, "concerned"));
        assertEquals(CompanionHighlight.BOLD, flags(t, n, "our lands"));
        assertEquals(CompanionHighlight.GOOD, flags(t, n, "cooperation"));
    }

    public void testWarehouse() {
        String t = "Your warehouse in Nijmegen will exceed its capacity of"
            + " Muskets during the next turn. 16 units of Muskets will be"
            + " wasted.";
        List<String> n = Arrays.asList("Nijmegen", "Muskets", "16");
        assertEquals(CompanionHighlight.BOLD, flags(t, n, "Nijmegen"));
        assertEquals(CompanionHighlight.BOLD, flags(t, n, "exceed its capacity"));
        assertEquals(CompanionHighlight.BOLD, flags(t, n, "Muskets"));
        assertEquals(CompanionHighlight.BOLD, flags(t, n, "wasted"));
        assertEquals("numbers stay plain", 0, flags(t, n, "16"));
        // "war" inside "warehouse" is not war.
        assertEquals(0, flags(t, n, "warehouse"));
    }

    public void testWarRedAndArrival() {
        String t = "The French have declared war on you!";
        assertEquals(CompanionHighlight.BAD,
                     flags(t, Arrays.asList(), "declared war"));
        String a = "Our ship has arrived in Amsterdam, Your Excellency.";
        List<String> n = Arrays.asList("Amsterdam");
        assertEquals(CompanionHighlight.GOOD, flags(a, n, "arrived"));
        assertEquals(CompanionHighlight.BOLD, flags(a, n, "Amsterdam"));
    }

    public void testRunsDoNotOverlap() {
        String t = "Cuper's Cove has a shortage of 27 Furs.";
        List<int[]> runs = CompanionHighlight.runs(t,
            Arrays.asList("Cuper's Cove", "Furs", "27"));
        int last = 0;
        for (int[] r : runs) {
            assertTrue(r[0] >= last && r[1] > r[0]);
            last = r[1];
        }
        assertEquals(CompanionHighlight.BOLD,
                     flags(t, Arrays.asList("Cuper's Cove"), "Cuper's Cove"));
        assertTrue(CompanionHighlight.runs("", null).isEmpty());
    }
}
