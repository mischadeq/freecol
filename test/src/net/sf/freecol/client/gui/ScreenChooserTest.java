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

package net.sf.freecol.client.gui;

import java.awt.Insets;
import java.awt.Rectangle;
import java.util.List;

import junit.framework.TestCase;


/**
 * Picking the monitor with a vertical monitor left of a wide one
 * (Mischa's set-up).
 */
public class ScreenChooserTest extends TestCase {

    /** Vertical 1080x1920 primary at 0,0; wide 2560x1440 to its right. */
    private static final Rectangle VERTICAL = new Rectangle(0, 0, 1080, 1920);
    private static final Rectangle WIDE = new Rectangle(1080, 0, 2560, 1440);
    private static final List<Rectangle> SCREENS = List.of(VERTICAL, WIDE);

    public void testWindowOnWideScreen() {
        assertEquals(1, ScreenChooser.bestScreen(
            new Rectangle(1200, 100, 1000, 800), SCREENS));
    }

    public void testWindowStraddlingPicksLargerPart() {
        // 1000 wide, 200 on the vertical screen, 800 on the wide one
        assertEquals(1, ScreenChooser.bestScreen(
            new Rectangle(880, 100, 1000, 800), SCREENS));
        // 800 on the vertical screen, 200 on the wide one
        assertEquals(0, ScreenChooser.bestScreen(
            new Rectangle(280, 100, 1000, 800), SCREENS));
    }

    public void testOldSizeAfterWinArrowStillPicksWideScreen() {
        // The bug: borderless window still 1080 wide, moved onto the
        // wide screen.  It must be seen as on the wide screen.
        assertEquals(1, ScreenChooser.bestScreen(
            new Rectangle(1080, 0, 1080, 1872), SCREENS));
    }

    public void testWindowOffAllScreensPicksNearest() {
        assertEquals(1, ScreenChooser.bestScreen(
            new Rectangle(5000, 200, 300, 300), SCREENS));
    }

    public void testWorkAreaLeavesTaskbarFree() {
        Rectangle area = ScreenChooser.workArea(WIDE, new Insets(0, 0, 48, 0));
        assertEquals(new Rectangle(1080, 0, 2560, 1392), area);
        assertEquals(WIDE, ScreenChooser.workArea(WIDE, null));
    }

    public void testPlaceOnCentresAndShrinks() {
        Rectangle area = new Rectangle(1080, 0, 2560, 1392);
        // A size at 0,0 is not inside the wide screen: centre it there
        Rectangle placed = ScreenChooser.placeOn(
            new Rectangle(0, 0, 1000, 800), area);
        assertEquals(new Rectangle(1080 + 780, 296, 1000, 800), placed);
        // Too big: shrink to fit
        placed = ScreenChooser.placeOn(new Rectangle(0, 0, 4000, 3000), area);
        assertEquals(area, placed);
        // Already inside: keep
        Rectangle inside = new Rectangle(1200, 50, 500, 500);
        assertEquals(inside, ScreenChooser.placeOn(inside, area));
    }
}
