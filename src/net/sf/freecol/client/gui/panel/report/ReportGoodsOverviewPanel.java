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
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JSeparator;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.StringTemplate;


/**
 * This panel displays the goods overview: one row per goods type and
 * one column per colony, showing what is in stock and how it changes
 * each turn, with the empire totals in front.
 */
public final class ReportGoodsOverviewPanel extends OverviewReportPanel {

    private static final String PREFIX = "report.goodsOverview.";

    /** Tooltip sentences shared with the colony overview. */
    private static final String COLONY_PREFIX = "report.colonyOverview.";


    /**
     * Creates the goods overview report.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ReportGoodsOverviewPanel(FreeColClient freeColClient) {
        super(freeColClient, "reportGoodsOverviewAction");

        update();
    }


    private static String signed(int n) {
        return (n > 0) ? "+" + n : Integer.toString(n);
    }

    /**
     * Make the cell for one goods type in one colony.
     *
     * @param colony The {@code Colony}.
     * @param gt The {@code GoodsType}.
     * @return A label for the cell.
     */
    private JLabel cell(Colony colony, GoodsType gt) {
        final int stock = colony.getGoodsCount(gt);
        final int p = colony.getAdjustedNetProductionOf(gt);
        final int capacity = colony.getWarehouseCapacity();
        if (stock == 0 && p == 0) return label("-", Color.LIGHT_GRAY);

        String text, tip;
        Color c;
        if (!gt.isStorable()) {
            // Bells and crosses are not kept in the warehouse.
            text = signed(p);
            c = (p > 0) ? cGood : (p < 0) ? cWarn : cPlain;
            tip = Messages.message(StringTemplate
                .template(COLONY_PREFIX + "produces.description")
                .addName("%colony%", colony.getName())
                .addNamed("%goods%", gt)
                .addAmount("%amount%", p));
        } else {
            text = (p == 0) ? Integer.toString(stock)
                : stock + " (" + signed(p) + ")";
            if (p < 0) {
                final int turns = stock / -p;
                c = (turns <= Colony.FAMINE_TURNS) ? cAlarm : cWarn;
                tip = Messages.message(StringTemplate
                    .template(COLONY_PREFIX + "draining.description")
                    .addName("%colony%", colony.getName())
                    .addNamed("%goods%", gt)
                    .addAmount("%amount%", -p)
                    .addAmount("%stock%", stock)
                    .addAmount("%turns%", turns));
            } else if (!gt.limitIgnored() && p > 0 && stock + p > capacity) {
                c = cAlarm;
                tip = Messages.message(StringTemplate
                    .template(COLONY_PREFIX + "overflow.description")
                    .addName("%colony%", colony.getName())
                    .addNamed("%goods%", gt)
                    .addAmount("%amount%", stock)
                    .addAmount("%capacity%", capacity)
                    .addAmount("%waste%", stock + p - capacity));
            } else {
                c = (!gt.limitIgnored()
                    && stock * 100 >= capacity * NEARLY_FULL_PERCENT) ? cWarn
                    : cPlain;
                tip = Messages.message(StringTemplate
                    .template(COLONY_PREFIX + "stock.description")
                    .addName("%colony%", colony.getName())
                    .addNamed("%goods%", gt)
                    .addAmount("%amount%", stock)
                    .addAmount("%capacity%", capacity));
            }
        }
        JLabel l = label(text, c);
        l.setToolTipText(tip);
        return l;
    }

    private void update() {
        final List<Colony> colonies = getSortedColonies();
        reportPanel.removeAll();
        if (colonies.isEmpty()) {
            reportPanel.setLayout(new MigLayout("fillx"));
            reportPanel.add(new JLabel(Messages.message(
                        COLONY_PREFIX + "none")));
            return;
        }

        final int columns = 3 + colonies.size();
        StringBuilder sb = new StringBuilder("[l][r][r]");
        for (int i = 0; i < colonies.size(); i++) sb.append("[r]");
        reportPanel.setLayout(new MigLayout("wrap " + columns
                + ", gap 16 4", sb.toString(), ""));

        // Header: totals first, then a link to every colony.
        reportPanel.add(new JLabel());
        JLabel h = boldLabel(Messages.message(PREFIX + "total"));
        h.setToolTipText(Messages.message(PREFIX + "total.description"));
        reportPanel.add(h);
        h = boldLabel(Messages.message(PREFIX + "perTurn"));
        h.setToolTipText(Messages.message(PREFIX + "perTurn.description"));
        reportPanel.add(h);
        for (Colony c : colonies) reportPanel.add(colonyButton(c));
        reportPanel.add(new JSeparator(JSeparator.HORIZONTAL), "span, growx");

        for (GoodsType gt : getReportGoodsTypes()) {
            int stock = 0, net = 0;
            boolean any = false;
            for (Colony c : colonies) {
                final int s = c.getGoodsCount(gt);
                final int p = c.getAdjustedNetProductionOf(gt);
                stock += s;
                net += p;
                any |= s != 0 || p != 0;
            }
            if (!any) continue; // Nothing to say about these goods.

            reportPanel.add(goodsLabel(gt, Messages.getName(gt), null, null));
            reportPanel.add((gt.isStorable())
                ? boldLabel(Integer.toString(stock)) : new JLabel());
            JLabel n = boldLabel(signed(net));
            n.setForeground((net > 0) ? cGood : (net < 0) ? cWarn : cPlain);
            reportPanel.add(n);
            for (Colony c : colonies) reportPanel.add(cell(c, gt));
        }
    }
}
