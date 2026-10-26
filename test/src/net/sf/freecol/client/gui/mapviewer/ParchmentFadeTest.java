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

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The old-chart fade on a tile at the edge of the screen (mod): the
 * game froze when a tile half off the image was faded.
 */
public class ParchmentFadeTest extends FreeColTestCase {

    private static final int W = 128, H = 64;

    /** An explored tile with unexplored land right next to it. */
    private Tile frontierTile() {
        Game game = getStandardGame();
        Map map = getTestMap(true);
        game.setMap(map);
        Tile tile = map.getTile(5, 5);
        // Unexplored neighbour: a tile without a type.
        tile.getNeighbourOrNull(net.sf.freecol.common.model.Direction.E)
            .setType(null);
        assertTrue(ParchmentPainter.frontier(tile) > 0f);
        return tile;
    }

    private static BufferedImage image() {
        BufferedImage img = new BufferedImage(300, 200,
                                              BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.setRGB(x, y, 0xff406020);
            }
        }
        return img;
    }

    /** A tile hanging off the left and top edges, with a redrawn area
        reaching past the image, is faded only where it is on the image. */
    public void testTileOffTheTopLeft() {
        final Tile tile = frontierTile();
        final BufferedImage img = image();
        ParchmentPainter.fadeTile(img, -W / 2, -H / 2, tile, W, H,
                                  new Rectangle(-100, -100, 600, 500));
    }

    /** The same off the right and bottom edges. */
    public void testTileOffTheBottomRight() {
        final Tile tile = frontierTile();
        final BufferedImage img = image();
        ParchmentPainter.fadeTile(img, img.getWidth() - W / 2,
                                  img.getHeight() - H / 2, tile, W, H,
                                  new Rectangle(0, 0, 1000, 1000));
    }

    /** A tile wholly inside the image is still faded. */
    public void testTileInsideIsFaded() {
        final Tile tile = frontierTile();
        final BufferedImage img = image();
        ParchmentPainter.fadeTile(img, 50, 50, tile, W, H,
                                  new Rectangle(0, 0, 300, 200));
        boolean changed = false;
        for (int y = 50; y < 50 + H && !changed; y++) {
            for (int x = 50; x < 50 + W; x++) {
                if (img.getRGB(x, y) != 0xff406020) {
                    changed = true;
                    break;
                }
            }
        }
        assertTrue("the tile next to the unknown is faded", changed);
    }
}
