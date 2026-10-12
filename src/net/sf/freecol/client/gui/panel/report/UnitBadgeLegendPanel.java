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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JSeparator;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.UnitBadges;
import net.sf.freecol.common.i18n.Messages;


/**
 * This panel explains the badges drawn next to units on the map.
 */
public final class UnitBadgeLegendPanel extends OverviewReportPanel {

    private static final String PREFIX = "report.unitBadgeLegend.";


    /**
     * Creates the legend.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public UnitBadgeLegendPanel(FreeColClient freeColClient) {
        super(freeColClient, "unitBadgeLegendAction");

        update();
    }

    /** The classic letter of a badge kind, for players who know them. */
    private static String classic(UnitBadges.Kind kind) {
        switch (kind) {
        case ACTIVE: return Messages.message("model.unit.unitState.active");
        case NO_MOVES:
            return Messages.message("model.unit.occupation.activeNoMovesLeft");
        case GOING:
            return Messages.message("model.unit.occupation.goingSomewhere");
        case TRADE_ROUTE:
            return Messages.message("model.unit.occupation.inTradeRoute");
        case SENTRY: return Messages.message("model.unit.unitState.sentry");
        case FORTIFYING: case FORTIFIED:
            return Messages.message("model.unit.unitState.fortified");
        case SKIPPED: return Messages.message("model.unit.unitState.skipped");
        case IMPROVING: return "P / R / C";
        case REPAIR:
            return Messages.message("model.unit.occupation.underRepair");
        case IN_COLONY: return Messages.message("model.unit.unitState.inColony");
        default: return "?";
        }
    }

    /**
     * Let a long text wrap within a width, scaled with the interface.
     *
     * @param text The text.
     * @param width The width in unscaled pixels.
     * @return HTML for a label.
     */
    private String wrap(String text, int width) {
        int w = (int)(width * lib.getScaleFactor());
        return "<html><div style='width:" + w + "px'>"
            + text.replace("&", "&amp;").replace("<", "&lt;")
            + "</div></html>";
    }

    private void update() {
        reportPanel.removeAll();
        reportPanel.setLayout(new MigLayout("wrap 4, gap 14 8",
                                            "[c][c][l][l, grow]", ""));
        reportPanel.add(label(wrap(Messages.message(PREFIX + "help"), 680),
                              Color.DARK_GRAY), "span, gapbottom 8");
        reportPanel.add(boldLabel(""));
        reportPanel.add(boldLabel(Messages.message(PREFIX + "classic")));
        reportPanel.add(boldLabel(""));
        reportPanel.add(boldLabel(""));
        reportPanel.add(new JSeparator(JSeparator.HORIZONTAL), "span, growx");

        final Color colour = getMyPlayer().getNationColor();
        final BufferedImage scratch = new BufferedImage(4, 4,
            BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = scratch.createGraphics();
        for (UnitBadges.Kind kind : UnitBadges.Kind.values()) {
            if (kind == UnitBadges.Kind.UNKNOWN) continue;
            BufferedImage chip = UnitBadges.symbolChip(lib, g, kind, colour);
            // Show the badges a little larger than on the map.
            reportPanel.add(new JLabel(new ImageIcon(chip.getScaledInstance(
                chip.getWidth() * 2, chip.getHeight() * 2,
                java.awt.Image.SCALE_SMOOTH))));
            reportPanel.add(label(classic(kind), cPlain));
            reportPanel.add(boldLabel(UnitBadges.getName(kind)));
            reportPanel.add(label(wrap(UnitBadges.getDescription(kind), 520),
                                  cPlain));
        }
        g.dispose();
    }
}
