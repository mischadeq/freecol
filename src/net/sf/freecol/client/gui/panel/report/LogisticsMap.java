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
package net.sf.freecol.client.gui.panel.report;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * A small map for the logistics report.  It shows the area the trade
 * routes cover, each route as a coloured line through its stops with
 * arrows for the direction of travel, and each carrier at its real
 * position with the path it will take to its next stop and the number
 * of turns it needs to get there.
 */
public final class LogisticsMap extends JComponent {

    /** Colours for the routes, chosen to stand out on the map. */
    private static final Color[] ROUTE_COLORS = {
        new Color(0xD7263D), new Color(0x1B6FE0), new Color(0xE07A00),
        new Color(0x7B2CBF), new Color(0x0E8A3E), new Color(0x111111),
        new Color(0xC2187A), new Color(0x00838F)
    };

    /** Height of the map in the report. */
    private static final int HEIGHT = 320;

    /** Tiles of margin around the area of interest. */
    private static final int MARGIN = 2;

    /** One carrier on the map. */
    public static final class Carrier {
        final Unit unit;
        final List<Tile> path;
        final int turns;
        final String nextStop;
        final GoodsType cargo;

        public Carrier(Unit unit, List<Tile> path, int turns,
                       String nextStop, GoodsType cargo) {
            this.unit = unit;
            this.path = path;
            this.turns = turns;
            this.nextStop = nextStop;
            this.cargo = cargo;
        }
    }

    /** One trade route on the map. */
    public static final class Route {
        final String name;
        final Color color;
        final List<Tile> stops = new ArrayList<>();
        final List<Carrier> carriers = new ArrayList<>();

        public Route(String name, Color color) {
            this.name = name;
            this.color = color;
        }

        public void addStop(Tile tile) {
            if (tile != null) this.stops.add(tile);
        }

        public void addCarrier(Carrier carrier) {
            if (carrier.unit.getTile() != null) this.carriers.add(carrier);
        }
    }


    private final ImageLibrary lib;
    private final Map map;
    private final Player player;
    private final List<Route> routes;
    private final Consumer<Tile> onClick;

    /** The tile area shown. */
    private int minX, minY, maxX, maxY;

    /** Has the area been fitted to the shape of the component? */
    private boolean fitted = false;

    /** Layout of the current paint: tile width and offset. */
    private int tileW = 8, offX = 0, offY = 0;


    /**
     * Get the colour of the n-th route.
     *
     * @param n The index of the route.
     * @return The {@code Color} for it.
     */
    public static Color routeColor(int n) {
        return ROUTE_COLORS[n % ROUTE_COLORS.length];
    }

    /**
     * Make a small coloured square, to show the colour of a route
     * next to its name.
     *
     * @param color The {@code Color}.
     * @return An {@code Icon}.
     */
    public static Icon swatch(final Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(color);
                g.fillRect(x, y + 2, 14, 10);
                g.setColor(Color.BLACK);
                g.drawRect(x, y + 2, 14, 10);
            }
            @Override
            public int getIconWidth() { return 16; }
            @Override
            public int getIconHeight() { return 14; }
        };
    }

    /**
     * Create the map.
     *
     * @param lib The {@code ImageLibrary} for goods icons.
     * @param map The game {@code Map}.
     * @param player The {@code Player} whose routes these are.
     * @param routes The routes to draw.
     * @param onClick What to do with a tile that is clicked.
     */
    public LogisticsMap(ImageLibrary lib, Map map, Player player,
                        List<Route> routes, Consumer<Tile> onClick) {
        this.lib = lib;
        this.map = map;
        this.player = player;
        this.routes = routes;
        this.onClick = onClick;
        computeBounds();
        setPreferredSize(new Dimension(900, HEIGHT));
        setMinimumSize(new Dimension(300, HEIGHT));
        setToolTipText(""); // Enables getToolTipText(MouseEvent)
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Tile t = tileAt(e.getPoint());
                if (t != null && onClick != null) onClick.accept(t);
            }
        });
    }

    /** Work out which part of the map to show. */
    private void computeBounds() {
        List<Tile> tiles = new ArrayList<>();
        for (Route r : this.routes) {
            tiles.addAll(r.stops);
            for (Carrier c : r.carriers) {
                tiles.add(c.unit.getTile());
                tiles.addAll(c.path);
            }
        }
        if (tiles.isEmpty()) { // No routes yet: show the colonies.
            for (Colony c : this.player.getColonyList()) tiles.add(c.getTile());
        }
        if (tiles.isEmpty()) {
            minX = minY = 0;
            maxX = this.map.getWidth() - 1;
            maxY = this.map.getHeight() - 1;
            return;
        }
        minX = minY = Integer.MAX_VALUE;
        maxX = maxY = Integer.MIN_VALUE;
        for (Tile t : tiles) {
            minX = Math.min(minX, t.getX());
            maxX = Math.max(maxX, t.getX());
            minY = Math.min(minY, t.getY());
            maxY = Math.max(maxY, t.getY());
        }
        // Rows alternate, so the vertical margin is doubled.
        minX = Math.max(0, minX - MARGIN);
        maxX = Math.min(this.map.getWidth() - 1, maxX + MARGIN);
        minY = Math.max(0, minY - 2 * MARGIN);
        maxY = Math.min(this.map.getHeight() - 1, maxY + 2 * MARGIN);
    }

    /**
     * Widen or heighten the area shown so that it has the shape of
     * the component, instead of leaving empty bands at the sides.
     */
    private void fillAspect() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        final int mapW = this.map.getWidth(), mapH = this.map.getHeight();
        final int cols = maxX - minX + 1, rows = maxY - minY + 1;
        // A tile is w wide and each row adds w/4 to the height.
        final double wantCols
            = (rows + 1) * getWidth() / (4.0 * getHeight()) - 0.5;
        if (wantCols > cols) {
            int extra = (int)Math.ceil(wantCols) - cols;
            minX = Math.max(0, minX - extra / 2);
            maxX = Math.min(mapW - 1, maxX + (extra - extra / 2));
        } else {
            double wantRows = 4.0 * getHeight() * (cols + 0.5) / getWidth()
                - 1;
            if (wantRows > rows) {
                int extra = (int)Math.ceil(wantRows) - rows;
                minY = Math.max(0, minY - extra / 2);
                maxY = Math.min(mapH - 1, maxY + (extra - extra / 2));
            }
        }
    }

    /** Fit the area into the component. */
    private void layoutTiles() {
        if (!this.fitted && getWidth() > 0) {
            fillAspect();
            this.fitted = true;
        }
        final int cols = maxX - minX + 1, rows = maxY - minY + 1;
        final double byWidth = getWidth() / (cols + 0.5);
        final double byHeight = 4.0 * getHeight() / (rows + 1);
        tileW = (int)Math.max(4, Math.min(64, Math.min(byWidth, byHeight)));
        tileW -= tileW % 2;
        final int usedW = (int)((cols + 0.5) * tileW);
        final int usedH = (rows + 1) * tileW / 4;
        offX = Math.max(0, (getWidth() - usedW) / 2);
        offY = Math.max(0, (getHeight() - usedH) / 2);
    }

    /** The top left corner of the box around a tile's diamond. */
    private Point corner(int x, int y) {
        return new Point(offX + (x - minX) * tileW + (y & 1) * tileW / 2,
                         offY + (y - minY) * tileW / 4);
    }

    private Point center(Tile t) {
        Point p = corner(t.getX(), t.getY());
        p.translate(tileW / 2, tileW / 4);
        return p;
    }

    private Tile tileAt(Point p) {
        Tile best = null;
        double bestD = Double.MAX_VALUE;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                Tile t = this.map.getTile(x, y);
                if (t == null) continue;
                double d = center(t).distance(p);
                if (d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
        }
        return (bestD <= tileW) ? best : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getToolTipText(MouseEvent e) {
        layoutTiles();
        final double radius = Math.max(8, tileW / 2);
        for (Route r : this.routes) {
            for (Carrier c : r.carriers) {
                if (center(c.unit.getTile()).distance(e.getPoint()) <= radius) {
                    String s = Messages.message(c.unit
                        .getLabel(Unit.UnitLabelType.PLAIN)) + ", " + r.name;
                    if (c.nextStop != null && c.turns > 0) {
                        s += ": " + c.nextStop + " "
                            + OverviewReportPanel.turns(
                                "report.unitOverview.turns", c.turns);
                    }
                    return s;
                }
            }
        }
        Tile t = tileAt(e.getPoint());
        if (t != null && t.hasSettlement()) {
            return t.getSettlement().getName();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        final Graphics2D g = (Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        layoutTiles();
        g.setColor(ImageLibrary.getMinimapBackgroundColor());
        g.fillRect(0, 0, getWidth(), getHeight());

        // The terrain.
        final int w = tileW, h = tileW / 2;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                Tile t = this.map.getTile(x, y);
                if (t == null) continue;
                Point p = corner(x, y);
                GeneralPath d = new GeneralPath();
                d.moveTo(p.x + w / 2f, p.y);
                d.lineTo(p.x + w, p.y + h / 2f);
                d.lineTo(p.x + w / 2f, p.y + h);
                d.lineTo(p.x, p.y + h / 2f);
                d.closePath();
                g.setColor((t.isExplored())
                    ? ImageLibrary.getMinimapEconomicColor(t.getType())
                    : Color.DARK_GRAY);
                g.fill(d);
            }
        }

        // Settlements: ours as named squares, others as dots.
        final Font small = getFont().deriveFont(Font.PLAIN, 11f);
        g.setFont(small);
        final FontMetrics fm = g.getFontMetrics();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                Tile t = this.map.getTile(x, y);
                if (t == null || !t.hasSettlement()) continue;
                Settlement s = t.getSettlement();
                Point c = center(t);
                int r = Math.max(4, w / 3);
                g.setColor((s.getOwner() == null) ? Color.GRAY
                    : s.getOwner().getNationColor());
                g.fillRect(c.x - r, c.y - r, 2 * r, 2 * r);
                g.setColor(Color.BLACK);
                g.drawRect(c.x - r, c.y - r, 2 * r, 2 * r);
                if (this.player.owns(s)) {
                    String name = s.getName();
                    int tx = c.x - fm.stringWidth(name) / 2;
                    int ty = c.y + r + fm.getAscent() + 1;
                    g.setColor(new Color(255, 255, 255, 190));
                    g.fillRect(tx - 2, ty - fm.getAscent(),
                               fm.stringWidth(name) + 4, fm.getHeight());
                    g.setColor(Color.BLACK);
                    g.drawString(name, tx, ty);
                }
            }
        }

        // The routes: dashed lines from stop to stop, with arrows.
        final Stroke dashed = new BasicStroke(2.5f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND, 1f, new float[] { 8f, 6f }, 0f);
        for (Route r : this.routes) {
            final int n = r.stops.size();
            if (n < 2) continue;
            g.setColor(r.color);
            for (int i = 0; i < n; i++) {
                Point a = center(r.stops.get(i));
                Point b = center(r.stops.get((i + 1) % n));
                if (a.equals(b)) continue;
                g.setStroke(dashed);
                g.drawLine(a.x, a.y, b.x, b.y);
                arrow(g, a, b, r.color);
                if (n == 2) break; // There and back on the same line.
            }
        }

        // The carriers: their path to the next stop, and a marker.
        final Stroke solid = new BasicStroke(3f, BasicStroke.CAP_ROUND,
                                             BasicStroke.JOIN_ROUND);
        for (Route r : this.routes) {
            for (Carrier c : r.carriers) {
                g.setStroke(solid);
                g.setColor(r.color);
                Point last = center(c.unit.getTile());
                for (Tile t : c.path) {
                    Point p = center(t);
                    g.drawLine(last.x, last.y, p.x, p.y);
                    last = p;
                }
            }
        }
        final Font bold = small.deriveFont(Font.BOLD);
        g.setFont(bold);
        final FontMetrics bm = g.getFontMetrics();
        for (Route r : this.routes) {
            for (Carrier c : r.carriers) {
                Point p = center(c.unit.getTile());
                int d = Math.max(10, w / 2);
                g.setStroke(new BasicStroke(2.5f));
                g.setColor(Color.WHITE);
                g.fillOval(p.x - d / 2, p.y - d / 2, d, d);
                g.setColor(r.color);
                g.drawOval(p.x - d / 2, p.y - d / 2, d, d);
                int tx = p.x + d / 2 + 2;
                if (c.cargo != null) {
                    BufferedImage img = lib.getSmallerGoodsTypeImage(c.cargo);
                    g.drawImage(img, tx, p.y - img.getHeight() / 2, null);
                    tx += img.getWidth() + 1;
                }
                if (c.turns > 0) {
                    String s = Integer.toString(c.turns);
                    g.setColor(new Color(255, 255, 255, 210));
                    g.fillRoundRect(tx - 1, p.y - bm.getAscent() / 2 - 2,
                                    bm.stringWidth(s) + 4, bm.getAscent() + 3,
                                    4, 4);
                    g.setColor(r.color);
                    g.drawString(s, tx + 1, p.y + bm.getAscent() / 2);
                }
            }
        }

        g.setStroke(new BasicStroke(1f));
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        g.dispose();
    }

    /** Draw an arrowhead halfway along a line, pointing from a to b. */
    private static void arrow(Graphics2D g, Point a, Point b, Color color) {
        final double angle = Math.atan2(b.y - a.y, b.x - a.x);
        final double mx = (a.x + b.x) / 2.0, my = (a.y + b.y) / 2.0;
        final double len = 11, spread = Math.toRadians(28);
        GeneralPath head = new GeneralPath();
        head.moveTo(mx + Math.cos(angle) * len / 2,
                    my + Math.sin(angle) * len / 2);
        head.lineTo(mx - Math.cos(angle - spread) * len / 2,
                    my - Math.sin(angle - spread) * len / 2);
        head.lineTo(mx - Math.cos(angle + spread) * len / 2,
                    my - Math.sin(angle + spread) * len / 2);
        head.closePath();
        g.setStroke(new BasicStroke(1f));
        g.setColor(color);
        g.fill(head);
        g.setColor(Color.BLACK);
        g.draw(head);
    }
}
