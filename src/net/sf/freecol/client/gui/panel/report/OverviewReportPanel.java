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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;


/**
 * Common ground for the overview reports: the colours, the goods the
 * player cares about, and the small building blocks of the tables.
 */
public abstract class OverviewReportPanel extends ReportPanel {

    /** Prefix of the action command of a button that selects a unit. */
    protected static final String UNIT = "unit:";

    /** Stock at or above this percentage of capacity gets a warning. */
    protected static final int NEARLY_FULL_PERCENT = 90;

    protected final ImageLibrary lib;

    protected final Color cAlarm;
    protected final Color cWarn;
    protected final Color cPlain;
    protected final Color cGood;
    protected final Color cCovered;


    /**
     * Creates the report.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param key A key for the title.
     */
    protected OverviewReportPanel(FreeColClient freeColClient, String key) {
        super(freeColClient, key);

        this.lib = getImageLibrary();
        // Reuse the colours of the compact colony report.
        this.cAlarm = ImageLibrary.getColor("color.report.colony.alarm",
                                            Color.RED);
        this.cWarn = ImageLibrary.getColor("color.report.colony.warning",
                                           Color.MAGENTA);
        this.cPlain = ImageLibrary.getColor("color.report.colony.plain",
                                            Color.DARK_GRAY);
        this.cGood = ImageLibrary.getColor("color.report.colony.good",
                                           Color.BLUE);
        this.cCovered = ImageLibrary.getColor("color.report.colony.export",
                                              Color.GREEN);
    }


    /**
     * Get the goods worth reporting: things that can be stored and
     * shipped, plus liberty bells and crosses.
     *
     * @return A sorted list of {@code GoodsType}s.
     */
    protected List<GoodsType> getReportGoodsTypes() {
        List<GoodsType> result = new ArrayList<>();
        for (GoodsType gt : getSpecification().getGoodsTypeList()) {
            if ((gt.isStorable() && !gt.isTradeGoods())
                || gt.isLibertyType() || gt.isImmigrationType()) {
                result.add(gt);
            }
        }
        result.sort(GoodsType.goodsTypeComparator);
        return result;
    }

    /**
     * Get the player's colonies in the order set in the preferences.
     *
     * @return A list of {@code Colony}s.
     */
    protected List<Colony> getSortedColonies() {
        List<Colony> result = new ArrayList<>();
        // Abandoned colonies can linger on the list with no units.
        for (Colony c : getMyPlayer().getColonyList()) {
            if (c.getUnitCount() > 0) result.add(c);
        }
        result.sort(getFreeColClient().getClientOptions()
            .getColonyComparator());
        return result;
    }

    protected JLabel label(String text, Color color) {
        JLabel l = new JLabel(text);
        if (color != null) l.setForeground(color);
        return l;
    }

    protected JLabel boldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    protected JPanel row() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        return p;
    }

    /**
     * Make a label with a goods icon.
     *
     * @param gt The {@code GoodsType} to show.
     * @param text The text beside the icon.
     * @param color The text colour.
     * @param toolTip An optional tooltip.
     * @return The label.
     */
    protected JLabel goodsLabel(GoodsType gt, String text, Color color,
                                String toolTip) {
        JLabel l = new JLabel(text,
            new ImageIcon(lib.getSmallerGoodsTypeImage(gt)), JLabel.LEADING);
        if (color != null) l.setForeground(color);
        l.setToolTipText((toolTip != null) ? toolTip : Messages.getName(gt));
        return l;
    }

    /**
     * Make a button that opens a colony.
     *
     * @param colony The {@code Colony} to open.
     * @return The button.
     */
    protected JButton colonyButton(Colony colony) {
        JButton b = Utility.getLinkButton(colony.getName(), null,
                                          colony.getId());
        b.addActionListener(this);
        return b;
    }

    /**
     * Make a button that selects a unit on the map.
     *
     * @param unit The {@code Unit} to select.
     * @return The button.
     */
    protected JButton unitButton(Unit unit) {
        JButton b = Utility.getLinkButton(
            Messages.message(unit.getLabel(Unit.UnitLabelType.PLAIN)),
            new ImageIcon(lib.getTinyUnitImage(unit)), UNIT + unit.getId());
        b.addActionListener(this);
        return b;
    }

    /**
     * Describe where a unit is.
     *
     * @param unit The {@code Unit} to locate.
     * @return A short description of its location.
     */
    protected String where(Unit unit) {
        return (unit.getLocation() == null) ? ""
            : Messages.message(unit.getLocation()
                .getLocationLabelFor(getMyPlayer()));
    }

    protected static String turns(String key, int turns) {
        return Messages.message(StringTemplate.template(key)
            .addAmount("%turns%", turns));
    }


    // Interface ActionListener

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent ae) {
        final String command = ae.getActionCommand();
        if (command != null && command.startsWith(UNIT)) {
            Unit unit = getGame().getFreeColGameObject(
                command.substring(UNIT.length()), Unit.class);
            if (unit == null || unit.isDisposed()) return;
            getGUI().removeComponent(this);
            if (unit.hasTile()) {
                // Select the unit and centre the map on it.
                getGUI().changeView(unit, true);
            } else {
                getGUI().displayObject(unit);
            }
        } else {
            super.actionPerformed(ae);
        }
    }
}
