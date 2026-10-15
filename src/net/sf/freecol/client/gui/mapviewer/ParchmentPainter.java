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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Tile;


/**
 * Draws the unexplored part of the map as an old chart: aged paper
 * instead of the flat blue, with the edge of the explored map fading
 * from colour into a sepia ink sketch and then into the paper, the way
 * a hand-drawn map trails off.  Nothing that has not been seen is
 * shown.
 *
 * The parchment is one seamless texture anchored to the map, so
 * neighbouring tiles join up and no tile pattern repeats visibly.
 */
public final class ParchmentPainter {

    /** Size of the tileable texture. */
    private static final int T = 512;

    /** The parchment, generated once. */
    private static BufferedImage texture = null;

    /** Ink for the coastline. */
    private static final Color INK = new Color(92, 62, 30);

    private ParchmentPainter() {}


    // The texture

    /**
     * Smooth periodic value noise.
     *
     * @param grid Random values on a period x period grid.
     * @param x The x position, 0..1 over the texture.
     * @param y The y position.
     * @return A value 0..1.
     */
    private static double noise(double[][] grid, double x, double y) {
        final int n = grid.length;
        final double gx = x * n, gy = y * n;
        final int x0 = (int)Math.floor(gx), y0 = (int)Math.floor(gy);
        final double fx = gx - x0, fy = gy - y0;
        final double sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        final int xa = Math.floorMod(x0, n), xb = Math.floorMod(x0 + 1, n);
        final int ya = Math.floorMod(y0, n), yb = Math.floorMod(y0 + 1, n);
        final double a = grid[ya][xa] + (grid[ya][xb] - grid[ya][xa]) * sx;
        final double b = grid[yb][xa] + (grid[yb][xb] - grid[yb][xa]) * sx;
        return a + (b - a) * sy;
    }

    private static double[][] grid(Random r, int n) {
        double[][] g = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) g[i][j] = r.nextDouble();
        }
        return g;
    }

    /**
     * The parchment texture: warm paper with soft blotches, fine grain
     * and a few faint fibres.  Seamless in both directions.
     *
     * @return The texture.
     */
    public static synchronized BufferedImage getTexture() {
        if (texture != null) return texture;
        final Random r = new Random(1630); // Same paper every time
        final double[][] stains = grid(r, 3), clouds = grid(r, 8),
            mottle = grid(r, 32), grain = grid(r, 128);
        BufferedImage img = new BufferedImage(T, T, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                final double u = x / (double)T, v = y / (double)T;
                final double s = noise(stains, u, v);
                final double c = noise(clouds, u, v);
                final double m = noise(mottle, u, v);
                final double g = noise(grain, u, v);
                // 0 = light paper, 1 = dark aged paper
                double dark = 0.55 * Math.pow(s, 2.2) + 0.25 * c
                    + 0.12 * m + 0.08 * g;
                dark = Math.max(0, Math.min(1, (dark - 0.18) * 1.35));
                // Aged paper: muted and darker than fresh parchment, so
                // it does not outshine the map.
                int red = (int)(176 - 52 * dark);
                int green = (int)(158 - 52 * dark);
                int blue = (int)(118 - 48 * dark);
                img.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        // A few faint fibres
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(0.7f));
        for (int i = 0; i < 70; i++) {
            final double x = r.nextDouble() * T, y = r.nextDouble() * T;
            final double a = r.nextDouble() * Math.PI, len = 6 + r.nextDouble() * 18;
            g2.setColor(new Color(70, 50, 28, 12 + r.nextInt(18)));
            for (int dx = -T; dx <= T; dx += T) {
                for (int dy = -T; dy <= T; dy += T) {
                    g2.draw(new Line2D.Double(x + dx, y + dy,
                        x + dx + Math.cos(a) * len, y + dy + Math.sin(a) * len));
                }
            }
        }
        g2.dispose();
        texture = img;
        return texture;
    }


    // Geometry

    /** Pixel x of a tile's image on the whole map. */
    static int worldX(int tx, int ty, int w) {
        return tx * w + (((ty & 1) != 0) ? w / 2 : 0);
    }

    /** Pixel y of a tile's image on the whole map. */
    static int worldY(int ty, int h) {
        return ty * h / 2;
    }

    private static Polygon diamond(int w, int h) {
        return new Polygon(new int[] { w / 2, w, w / 2, 0 },
                           new int[] { 0, h / 2, h, h / 2 }, 4);
    }

    private static TexturePaint paint(int tx, int ty, int w, int h) {
        return new TexturePaint(getTexture(), new Rectangle2D.Double(
            -worldX(tx, ty, w), -worldY(ty, h), T, T));
    }


    // Drawing

    /**
     * Draw an unexplored tile as parchment.
     *
     * @param g2d The {@code Graphics2D} (origin at the tile image).
     * @param tile The {@code Tile}.
     * @param w The tile width.
     * @param h The tile height.
     */
    public static void paintTile(Graphics2D g2d, Tile tile, int w, int h) {
        g2d.setPaint(paint(tile.getX(), tile.getY(), w, h));
        // One pixel larger, so the diamonds close without seams.
        g2d.fill(new Polygon(new int[] { w / 2, w + 1, w / 2, -1 },
                             new int[] { -1, h / 2, h + 1, h / 2 }, 4));
    }

    /**
     * Draw the fade from an explored tile into the parchment of its
     * unexplored neighbour, using the shape of the game's own fade.
     *
     * @param g2d The {@code Graphics2D} (origin at the tile image).
     * @param tile The explored {@code Tile} being drawn.
     * @param mask The game's fade image for that side (only its
     *     transparency is used).
     * @param w The tile width.
     * @param h The tile height.
     */
    public static void paintBorder(Graphics2D g2d, Tile tile,
                                   BufferedImage mask, int w, int h) {
        if (mask == null) return;
        BufferedImage img = new BufferedImage(mask.getWidth(), mask.getHeight(),
                                              BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(paint(tile.getX(), tile.getY(), w, h));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setComposite(AlphaComposite.DstIn);
        g.drawImage(mask, 0, 0, null);
        g.dispose();
        g2d.drawImage(img, 0, 0, null);
    }

    // The fade from colour to sketch to paper

    /** How far into the unknown a tile is, for the fade: 1 unexplored. */
    static float frontier(Tile tile) {
        if (tile == null) return 0f;
        if (!tile.isExplored()) return 1f;
        for (Tile t : tile.getSurroundingTiles(1, 1)) {
            if (!t.isExplored()) return 0.5f;
        }
        for (Tile t : tile.getSurroundingTiles(2, 2)) {
            if (!t.isExplored()) return 0.25f;
        }
        for (Tile t : tile.getSurroundingTiles(3, 3)) {
            if (!t.isExplored()) return 0.08f;
        }
        return 0f;
    }

    /** Is an unexplored tile next to the explored map (where pictures of
        explored tiles can stick out over it)? */
    static boolean edgeOfUnknown(Tile tile) {
        if (tile.isExplored()) return false;
        for (Tile t : tile.getSurroundingTiles(1, 2)) {
            if (t.isExplored()) return true;
        }
        return false;
    }

    /** A corner value: the strongest pull of the tiles that share it. */
    private static float corner(Tile tile, Direction... dirs) {
        float v = frontier(tile);
        for (Direction d : dirs) {
            v = Math.max(v, frontier(tile.getNeighbourOrNull(d)));
        }
        return v;
    }

    /**
     * Fade an explored tile near the unknown, in place on the image the
     * map is drawn into: colour becomes a sepia ink drawing, and nearer
     * the unknown the drawing fades into the paper.  The amount is
     * blended smoothly over the tile from its four corners, so there
     * are no steps between tiles.
     *
     * @param img The image the map is drawn into.
     * @param ox The tile image's x position in that image.
     * @param oy The tile image's y position in that image.
     * @param tile The explored {@code Tile}.
     * @param w The tile width.
     * @param h The tile height.
     * @param clip The area being redrawn, in image coordinates.
     */
    public static void fadeTile(BufferedImage img, int ox, int oy, Tile tile,
                                int w, int h, java.awt.Rectangle clip) {
        if (!tile.isExplored() && !edgeOfUnknown(tile)) return;
        // Corners: left, top, right, bottom of the diamond.
        final float fL = corner(tile, Direction.W, Direction.NW, Direction.SW);
        final float fT = corner(tile, Direction.N, Direction.NW, Direction.NE);
        final float fR = corner(tile, Direction.E, Direction.NE, Direction.SE);
        final float fB = corner(tile, Direction.S, Direction.SE, Direction.SW);
        if (fL <= 0 && fT <= 0 && fR <= 0 && fB <= 0) return;
        final boolean water = !tile.isLand();
        final BufferedImage tex = getTexture();
        final int wx = worldX(tile.getX(), tile.getY(), w);
        final int wy = worldY(tile.getY(), h);
        final int x0 = Math.max(ox, clip.x), x1 = Math.min(ox + w, clip.x + clip.width);
        final int y0 = Math.max(oy, clip.y), y1 = Math.min(oy + h, clip.y + clip.height);
        if (x1 > img.getWidth() || y1 > img.getHeight()) return;
        for (int y = y0; y < y1; y++) {
            final double q = (y - oy + 0.5) / h;
            for (int x = x0; x < x1; x++) {
                final double p = (x - ox + 0.5) / w;
                // Diamond coordinates: a from the NW edge to the SE edge,
                // b from the SW edge to the NE edge.
                final double a = p + q - 0.5, b = p - q + 0.5;
                if (a < 0 || a > 1 || b < 0 || b > 1) continue;
                final double f = (1 - a) * (1 - b) * fL + (1 - a) * b * fT
                    + a * b * fR + a * (1 - b) * fB;
                if (f <= 0.01) continue;
                final int argb = img.getRGB(x, y);
                final int alpha = argb >>> 24;
                // The paper at this spot on the map.
                final int paper = tex.getRGB(Math.floorMod(wx + x - ox, T),
                                             Math.floorMod(wy + y - oy, T));
                final int pr = (paper >> 16) & 0xff, pg = (paper >> 8) & 0xff,
                    pb = paper & 0xff;
                // The sea near the unknown: a flat chart blue (no waves,
                // no high-seas edges poking into the mist), then paper.
                final double cover = Math.max(smooth(0.3, 1.0, f),
                                              0.55 * smooth(0.0, 0.6, f));
                final double flat = smooth(0.0, 0.25, f);
                final double seaA = Math.max(cover, flat);
                final double sr0 = (pr * cover + SEA_R * (seaA - cover)) / seaA;
                final double sg0 = (pg * cover + SEA_G * (seaA - cover)) / seaA;
                final double sb0 = (pb * cover + SEA_B * (seaA - cover)) / seaA;
                if (alpha < 255) {
                    // Nothing (or little) drawn here: the animated sea
                    // shows through from below.  Lay the chart sea and
                    // the paper over it, thicker towards the unknown.
                    final int pa = (int)Math.round(255 * seaA);
                    if (pa > alpha) {
                        img.setRGB(x, y, (pa << 24) | ((int)sr0 << 16)
                                   | ((int)sg0 << 8) | (int)sb0);
                    }
                    continue;
                }
                if (water) {
                    // Drawn straight onto the sea (the scrolling painter):
                    // the same chart sea and paper, not brown ink.
                    final int r0 = (argb >> 16) & 0xff, g0 = (argb >> 8) & 0xff,
                        b0 = argb & 0xff;
                    img.setRGB(x, y, (0xff << 24)
                        | ((int)Math.round(r0 + (sr0 - r0) * seaA) << 16)
                        | ((int)Math.round(g0 + (sg0 - g0) * seaA) << 8)
                        | (int)Math.round(b0 + (sb0 - b0) * seaA));
                    continue;
                }
                final int r0 = (argb >> 16) & 0xff, g0 = (argb >> 8) & 0xff,
                    b0 = argb & 0xff;
                // Sepia ink drawing: dark parts become brown ink, light
                // parts the paper.
                final double lum = (0.30 * r0 + 0.59 * g0 + 0.11 * b0) / 255.0;
                // Contrast is stretched so shapes (trees, hills, rivers)
                // stay readable as ink strokes on light paper.
                final double ink = 0.85 * Math.pow(Math.max(0, Math.min(1,
                    (0.62 - lum) / 0.5)), 1.4);
                final double sr = pr * (1 - ink) + 62 * ink;
                final double sg = pg * (1 - ink) + 42 * ink;
                final double sb = pb * (1 - ink) + 22 * ink;
                final double toSketch = smooth(0.0, 0.55, f);
                final double toPaper = smooth(0.45, 1.0, f);
                double r = r0 + (sr - r0) * toSketch;
                double g = g0 + (sg - g0) * toSketch;
                double bl = b0 + (sb - b0) * toSketch;
                r += (pr - r) * toPaper;
                g += (pg - g) * toPaper;
                bl += (pb - bl) * toPaper;
                img.setRGB(x, y, (alpha << 24) | ((int)r << 16)
                           | ((int)g << 8) | (int)bl);
            }
        }
    }

    /** The flat sea of the chart, near the unknown. */
    private static final int SEA_R = 30, SEA_G = 72, SEA_B = 96;

    private static double smooth(double e0, double e1, double x) {
        final double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static Color withAlpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                         Math.max(0, Math.min(255, Math.round(255 * a))));
    }
}
