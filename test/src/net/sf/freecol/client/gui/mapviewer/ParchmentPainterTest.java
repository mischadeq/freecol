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

package net.sf.freecol.client.gui.mapviewer;

import java.awt.image.BufferedImage;

import junit.framework.TestCase;


/**
 * The old-chart paper: seamless, and anchored to the map so that
 * neighbouring tiles join up.
 */
public class ParchmentPainterTest extends TestCase {

    private static int diff(int a, int b) {
        return Math.abs(((a >> 16) & 0xff) - ((b >> 16) & 0xff))
            + Math.abs(((a >> 8) & 0xff) - ((b >> 8) & 0xff))
            + Math.abs((a & 0xff) - (b & 0xff));
    }

    /** The texture wraps without a visible seam. */
    public void testTextureIsSeamless() {
        BufferedImage t = ParchmentPainter.getTexture();
        int n = t.getWidth();
        long seam = 0, inside = 0;
        for (int i = 0; i < n; i++) {
            seam += diff(t.getRGB(n - 1, i), t.getRGB(0, i))
                + diff(t.getRGB(i, n - 1), t.getRGB(i, 0));
            inside += diff(t.getRGB(n / 2, i), t.getRGB(n / 2 + 1, i))
                + diff(t.getRGB(i, n / 2), t.getRGB(i, n / 2 + 1));
        }
        // Across the wrap the paper changes no more than between any
        // two neighbouring pixels inside it (allowing for the fibres).
        assertTrue("seam " + seam + " inside " + inside, seam < inside * 1.5);
    }

    /** Odd rows sit half a tile to the right, rows are half a tile apart. */
    public void testWorldPositions() {
        assertEquals(0, ParchmentPainter.worldX(0, 0, 128));
        assertEquals(64, ParchmentPainter.worldX(0, 1, 128));
        assertEquals(128 * 3 + 64, ParchmentPainter.worldX(3, 5, 128));
        assertEquals(32 * 5, ParchmentPainter.worldY(5, 64));
    }

    /** The paper is muted: darker than fresh parchment. */
    public void testPaperIsMuted() {
        BufferedImage t = ParchmentPainter.getTexture();
        long sum = 0;
        for (int y = 0; y < t.getHeight(); y += 7) {
            for (int x = 0; x < t.getWidth(); x += 7) {
                int c = t.getRGB(x, y);
                sum += ((c >> 16) & 0xff) + ((c >> 8) & 0xff) + (c & 0xff);
            }
        }
        double avg = sum / 3.0 / ((t.getHeight() / 7 + 1) * (t.getWidth() / 7 + 1));
        assertTrue("average brightness " + avg, avg < 170);
    }
}
