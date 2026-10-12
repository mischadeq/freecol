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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Unit.UnitState;
import net.sf.freecol.common.util.ImageUtils;


/**
 * The small badge drawn next to each unit on the map, saying what the
 * unit is doing.  The game's original badges are single letters (G, S,
 * F, 0, ...).  These badges use symbols instead: an arrow for a unit on
 * its way, an eye for a sentry, a shield for a fortified unit, and so
 * on.  Hovering over a unit on the map explains its badge in words.
 *
 * Two client options control this: symbols or the original letters,
 * and whether the explanation appears on hover.
 */
public final class UnitBadges {

    /** What a badge can say about one of the player's units. */
    public enum Kind {
        ACTIVE, NO_MOVES, GOING, TRADE_ROUTE, SENTRY, FORTIFYING,
        FORTIFIED, SKIPPED, IMPROVING, REPAIR, IN_COLONY, UNKNOWN;

        /** @return The message key stem for this kind. */
        public String getKey() {
            return "unitBadge." + name().toLowerCase().replace("_", "");
        }
    }

    /** The client, for its options.  Set when the game starts. */
    private static FreeColClient client = null;


    private UnitBadges() {}

    /**
     * Tell the badges which client they belong to.
     *
     * @param freeColClient The {@code FreeColClient}.
     */
    public static void setClient(FreeColClient freeColClient) {
        client = freeColClient;
    }

    private static boolean option(String id, boolean fallback) {
        try {
            return (client == null || client.getClientOptions() == null)
                ? fallback : client.getClientOptions().getBoolean(id);
        } catch (RuntimeException re) {
            return fallback; // Option missing from an old options file.
        }
    }

    /** @return True if badges are drawn as symbols. */
    public static boolean useSymbols() {
        return option(ClientOptions.UNIT_BADGE_SYMBOLS, true);
    }

    /** @return True if badges are explained when hovering. */
    public static boolean explainOnHover() {
        return option(ClientOptions.UNIT_BADGE_HOVER, true);
    }

    /**
     * Work out what a unit's badge should say.  This follows the same
     * rules as {@link Unit#getOccupationLabel}.
     *
     * @param unit The {@code Unit} to examine.
     * @return The badge {@code Kind}.
     */
    public static Kind kindOf(Unit unit) {
        if (unit.isDamagedAndUnderForcedRepair()) return Kind.REPAIR;
        if (unit.getTradeRoute() != null) return Kind.TRADE_ROUTE;
        if (unit.getState() == UnitState.ACTIVE && unit.getMovesLeft() == 0
            && !unit.isInEurope()) return Kind.NO_MOVES;
        if (unit.getState() == UnitState.IMPROVING
            && unit.getWorkImprovement() != null) return Kind.IMPROVING;
        if (unit.getDestination() != null) return Kind.GOING;
        switch (unit.getState()) {
        case ACTIVE: return Kind.ACTIVE;
        case SENTRY: return Kind.SENTRY;
        case FORTIFYING: return Kind.FORTIFYING;
        case FORTIFIED: return Kind.FORTIFIED;
        case SKIPPED: return Kind.SKIPPED;
        case IN_COLONY: return Kind.IN_COLONY;
        default: return Kind.UNKNOWN;
        }
    }

    /**
     * Make the badge for a unit.
     *
     * @param lib The {@code ImageLibrary} to draw with.
     * @param g A {@code Graphics2D} for font metrics.
     * @param unit The {@code Unit} the badge is for.
     * @param viewer The {@code Player} looking at the map.
     * @param text The classic letter for the badge.
     * @return The badge image.
     */
    public static BufferedImage chip(ImageLibrary lib, Graphics2D g,
                                     Unit unit, Player viewer, String text) {
        if (!useSymbols() || viewer == null || !viewer.owns(unit)) {
            return lib.getOccupationIndicatorChip(g, unit, text);
        }
        final Color background = unit.getOwner().getNationColor();
        return symbolChip(lib, g, kindOf(unit), background);
    }

    /**
     * Draw a symbol badge.
     *
     * @param lib The {@code ImageLibrary} for sizes.
     * @param g A {@code Graphics2D} for font metrics, may be null.
     * @param kind The {@code Kind} of badge.
     * @param background The badge colour.
     * @return The badge image.
     */
    public static BufferedImage symbolChip(ImageLibrary lib, Graphics2D g,
                                           Kind kind, Color background) {
        // Same height as the classic letter badges.
        final Font font = lib.getScaledFont("simple-bold-tiny", null);
        int size;
        if (g != null) {
            final FontMetrics fm = g.getFontMetrics(font);
            size = fm.getMaxAscent() + fm.getMaxDescent() + lib.scaleInt(6);
        } else {
            size = font.getSize() + lib.scaleInt(9);
        }
        size = Math.max(10, size);
        final BufferedImage bi = ImageUtils.createBufferedImage(size, size);
        final Graphics2D g2 = bi.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, size, size);
        g2.setColor(background);
        g2.fillRect(1, 1, size - 2, size - 2);
        final Color fg = ImageLibrary.makeForegroundColor(background);
        g2.setColor(fg);
        drawSymbol(g2, kind, 1, 1, size - 2, fg, background, font);
        g2.dispose();
        return bi;
    }

    /**
     * Draw the symbol of a badge kind into a square.
     */
    private static void drawSymbol(Graphics2D g, Kind kind, int x0, int y0,
                                   int s, Color fg, Color bg, Font font) {
        final float w = Math.max(1.3f, s / 8f);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND,
                                    BasicStroke.JOIN_ROUND));
        final float m = s * 0.2f;            // margin
        final float l = x0 + m, t = y0 + m;  // left, top
        final float r = x0 + s - m, b = y0 + s - m;
        final float cx = x0 + s / 2f, cy = y0 + s / 2f;
        GeneralPath p = new GeneralPath();
        switch (kind) {
        case ACTIVE: // "!": waiting for orders
            g.draw(new Line2D.Float(cx, t, cx, cy + s * 0.08f));
            g.fill(new Ellipse2D.Float(cx - w * 0.75f, b - w * 1.5f,
                                       w * 1.5f, w * 1.5f));
            break;
        case NO_MOVES: // "z z": resting until next turn
            p.moveTo(l, cy - s * 0.02f); p.lineTo(cx, cy - s * 0.02f);
            p.lineTo(l, b); p.lineTo(cx, b);
            g.draw(p);
            p = new GeneralPath();
            p.moveTo(cx + s * 0.05f, t); p.lineTo(r, t);
            p.lineTo(cx + s * 0.05f, cy - s * 0.12f);
            p.lineTo(r, cy - s * 0.12f);
            g.draw(p);
            break;
        case GOING: // arrow to the right
            g.draw(new Line2D.Float(l, cy, r, cy));
            p.moveTo(cx + s * 0.05f, t); p.lineTo(r, cy);
            p.lineTo(cx + s * 0.05f, b);
            g.draw(p);
            break;
        case TRADE_ROUTE: // circular arrow
            g.draw(new Arc2D.Float(l, t, r - l, b - t, 60, 280, Arc2D.OPEN));
            p.moveTo(r - s * 0.02f, t - s * 0.02f);
            p.lineTo(r - s * 0.02f, t + s * 0.2f);
            p.lineTo(r - s * 0.24f, t + s * 0.16f);
            g.draw(p);
            break;
        case SENTRY: { // an eye
            float eh = (b - t) * 0.75f;
            GeneralPath eye = new GeneralPath();
            eye.moveTo(l - s * 0.05f, cy);
            eye.quadTo(cx, cy - eh, r + s * 0.05f, cy);
            eye.quadTo(cx, cy + eh, l - s * 0.05f, cy);
            g.draw(eye);
            float pr = s * 0.13f;
            g.fill(new Ellipse2D.Float(cx - pr, cy - pr, 2 * pr, 2 * pr));
            break;
        }
        case FORTIFYING: case FORTIFIED: { // a shield
            GeneralPath sh = new GeneralPath();
            sh.moveTo(l, t); sh.lineTo(r, t); sh.lineTo(r, cy);
            sh.quadTo(r, b, cx, b + s * 0.05f);
            sh.quadTo(l, b, l, cy);
            sh.closePath();
            if (kind == Kind.FORTIFIED) g.fill(sh); else g.draw(sh);
            break;
        }
        case SKIPPED: // double chevron
            for (float dx : new float[] { -s * 0.13f, s * 0.13f }) {
                p = new GeneralPath();
                p.moveTo(cx + dx - s * 0.13f, t + s * 0.05f);
                p.lineTo(cx + dx + s * 0.13f, cy);
                p.lineTo(cx + dx - s * 0.13f, b - s * 0.05f);
                g.draw(p);
            }
            break;
        case IMPROVING: // a hammer
            g.draw(new Line2D.Float(l + s * 0.05f, b, cx + s * 0.08f,
                                    cy - s * 0.05f));
            p.moveTo(cx - s * 0.12f, t + s * 0.12f);
            p.lineTo(cx + s * 0.12f, t - s * 0.05f);
            p.lineTo(r + s * 0.05f, cy - s * 0.12f);
            p.lineTo(r - s * 0.12f, cy + s * 0.08f);
            p.closePath();
            g.fill(p);
            break;
        case REPAIR: // a plus, as on a first aid kit
            g.draw(new Line2D.Float(cx, t, cx, b));
            g.draw(new Line2D.Float(l, cy, r, cy));
            break;
        case IN_COLONY: // a house
            p.moveTo(l, cy); p.lineTo(cx, t); p.lineTo(r, cy);
            p.lineTo(r, b); p.lineTo(l, b); p.closePath();
            g.draw(p);
            break;
        default: { // "?"
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            g.drawString("?", cx - fm.stringWidth("?") / 2f,
                         cy + fm.getAscent() / 2f - 1);
            break;
        }
        }
    }

    /**
     * Get the name of a badge kind.
     *
     * @param kind The {@code Kind}.
     * @return Its name.
     */
    public static String getName(Kind kind) {
        return Messages.message(kind.getKey() + ".name");
    }

    /**
     * Get the explanation of a badge kind.
     *
     * @param kind The {@code Kind}.
     * @return What it means.
     */
    public static String getDescription(Kind kind) {
        return Messages.message(kind.getKey() + ".description");
    }

    /**
     * Explain the badge of a unit, for a tooltip.
     *
     * @param unit The {@code Unit}.
     * @return A short sentence.
     */
    public static String explain(Unit unit) {
        final Kind kind = kindOf(unit);
        String s = Messages.message(unit.getLabel(Unit.UnitLabelType.PLAIN))
            + ": " + getName(kind) + ". " + getDescription(kind);
        if (kind == Kind.IMPROVING && unit.getWorkImprovement() != null) {
            s += " (" + Messages.getName(unit.getWorkImprovement().getType())
                + ", " + Messages.message(StringTemplate
                    .template("report.unitOverview.turns")
                    .addAmount("%turns%", unit.getWorkTurnsLeft())) + ")";
        } else if (kind == Kind.TRADE_ROUTE) {
            s += " (" + unit.getTradeRoute().getName() + ")";
        } else if (kind == Kind.GOING && unit.getDestination() != null) {
            s += " (" + Messages.message(unit.getDestinationLabel()) + ")";
        }
        return s;
    }

    /**
     * The tooltip for a map tile: the badge of each of the player's
     * units there, explained.
     *
     * @param freeColClient The {@code FreeColClient}.
     * @param tile The {@code Tile} under the mouse, may be null.
     * @return Tooltip text, or null for no tooltip.
     */
    public static String mapToolTip(FreeColClient freeColClient, Tile tile) {
        if (tile == null || !explainOnHover()) return null;
        final Player player = freeColClient.getMyPlayer();
        if (player == null || !tile.isExplored()) return null;
        StringBuilder sb = new StringBuilder("<html>");
        int n = 0;
        for (Unit u : tile.getUnitList()) {
            if (!player.owns(u)) continue;
            if (n == 6) {
                sb.append("...");
                break;
            }
            if (n > 0) sb.append("<br>");
            sb.append(escape(explain(u)));
            n++;
        }
        return (n == 0) ? null : sb.append("</html>").toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
