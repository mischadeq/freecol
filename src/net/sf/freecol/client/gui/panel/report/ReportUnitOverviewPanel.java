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
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.UnitGroups;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Unit.UnitState;


/**
 * This panel displays the unit overview: every unit that is not at
 * work inside a colony, with the group it belongs to, where it is and
 * what it is doing.  The list can be narrowed down, most usefully to
 * the units that are standing idle.  Clicking a unit selects it.
 */
public final class ReportUnitOverviewPanel extends OverviewReportPanel {

    private static final String PREFIX = "report.unitOverview.";

    /** Prefix of the action command of the filter buttons. */
    private static final String FILTER = "filter:";

    /** The filters, in display order. */
    private static final String[] FILTERS = {
        "all", "idle", "military", "wagons", "ships", "groups"
    };

    /**
     * The current filter.  Static so that the choice is remembered
     * while the game is running.
     */
    private static String filter = "all";


    /**
     * Creates the unit overview report.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ReportUnitOverviewPanel(FreeColClient freeColClient) {
        super(freeColClient, "reportUnitOverviewAction");

        update();
    }


    /**
     * Is a unit standing around on the map with nothing to do?
     *
     * @param unit The {@code Unit} to check.
     * @return True if the unit is idle.
     */
    private static boolean isIdle(Unit unit) {
        return unit.hasTile() && !unit.isOnCarrier()
            && (unit.getState() == UnitState.ACTIVE
                || unit.getState() == UnitState.SKIPPED)
            && unit.getDestination() == null
            && unit.getTradeRoute() == null
            && !unit.isDamaged();
    }

    private boolean accept(Unit unit, String group) {
        switch (filter) {
        case "idle": return isIdle(unit);
        case "military": return unit.isOffensiveUnit() && !unit.isNaval();
        case "wagons": return unit.isCarrier() && !unit.isNaval();
        case "ships": return unit.isNaval();
        case "groups": return group != null;
        default: return true;
        }
    }

    /**
     * Describe what a unit is doing.
     *
     * @param unit The {@code Unit} to describe.
     * @return A label with the description.
     */
    private JLabel doing(Unit unit) {
        final Player player = getMyPlayer();
        if (unit.isDamaged()) {
            return label(Messages.message(unit.getRepairLabel()), cWarn);
        }
        if (unit.getTradeRoute() != null) {
            StringTemplate t = StringTemplate
                .template(PREFIX + "doing.tradeRoute")
                .addName("%route%", unit.getTradeRoute().getName());
            String text = Messages.message(t);
            if (unit.getStop() != null
                && unit.getStop().getLocation() != null) {
                text += ", " + Messages.message(StringTemplate
                    .template("report.logistics.heading")
                    .addStringTemplate("%location%", unit.getStop()
                        .getLocation().getLocationLabelFor(player)));
            }
            return label(text, cPlain);
        }
        if (unit.getDestination() != null) {
            String text = Messages.message(unit.getDestinationLabel());
            if (unit.hasTile()) {
                int turns = unit.getTurnsToReach(unit.getDestination());
                if (turns < Unit.MANY_TURNS) {
                    text += " (" + turns(PREFIX + "turns", turns) + ")";
                } else {
                    return label(text + " ("
                        + Messages.message(PREFIX + "noPath") + ")", cAlarm);
                }
            }
            return label(text, cPlain);
        }
        if (unit.isInEurope()) {
            return label(Messages.message(PREFIX + "doing.inEurope"), cPlain);
        }
        if (unit.isOnCarrier()) {
            return label(Messages.message(PREFIX + "doing.aboard"), cPlain);
        }
        switch (unit.getState()) {
        case IMPROVING:
            return label((unit.getWorkImprovement() == null)
                ? Messages.message(PREFIX + "doing.working")
                : Messages.getName(unit.getWorkImprovement().getType())
                    + " (" + turns(PREFIX + "turns",
                                   unit.getWorkTurnsLeft()) + ")", cPlain);
        case FORTIFYING: case FORTIFIED:
            return label(Messages.message(PREFIX + "doing.fortified"), cPlain);
        case SENTRY:
            return label(Messages.message(PREFIX + "doing.sentry"), cPlain);
        default:
            break;
        }
        return label(Messages.message(PREFIX + "doing.idle"), cWarn);
    }

    private void update() {
        final Player player = getMyPlayer();
        final UnitGroups groups = igc().getUnitGroups();
        reportPanel.removeAll();
        reportPanel.setLayout(new MigLayout("fillx, wrap 5, gap 14 3",
                                            "[l][l][l][l, grow][r]", ""));

        // The filter buttons, the current one in bold.
        JPanel bar = row();
        bar.add(label(Messages.message(PREFIX + "show"), cPlain));
        for (String f : FILTERS) {
            JButton b = Utility.getLinkButton(
                Messages.message(PREFIX + "filter." + f), null, FILTER + f);
            if (f.equals(filter)) {
                b.setFont(b.getFont().deriveFont(Font.BOLD));
                b.setForeground(Color.BLACK);
            }
            b.addActionListener(this);
            bar.add(b);
        }
        reportPanel.add(bar, "span");

        // Collect the units, leaving out those at work in a colony.
        final List<Unit> units = new ArrayList<>();
        final Map<String, String> groupOf = new HashMap<>();
        for (Unit u : player.getUnitSet()) {
            if (u.isDisposed() || u.isInColony()) continue;
            String g = groups.getGroupName(u);
            if (!accept(u, g)) continue;
            units.add(u);
            if (g != null) groupOf.put(u.getId(), g);
        }
        // Groups first and together, then by kind of unit, then place.
        units.sort(Comparator
            .comparing((Unit u) -> groupOf.get(u.getId()) == null)
            .thenComparing(u -> String.valueOf(groupOf.get(u.getId())))
            .thenComparingInt(u -> u.getType().getIndex())
            .thenComparing(u -> where(u)));

        JLabel count = label(Messages.message(StringTemplate
                .template(PREFIX + "count")
                .addAmount("%number%", units.size())), Color.GRAY);
        reportPanel.add(count, "span");

        for (String column : new String[] {
                "unit", "group", "where", "doing", "moves" }) {
            reportPanel.add(boldLabel(Messages.message(PREFIX + "header."
                        + column)));
        }
        reportPanel.add(new JSeparator(JSeparator.HORIZONTAL), "span, growx");

        for (Unit u : units) {
            reportPanel.add(unitButton(u));
            String g = groupOf.get(u.getId());
            reportPanel.add(label((g == null) ? "" : g, cGood));
            reportPanel.add(label(where(u), cPlain));
            reportPanel.add(doing(u));
            reportPanel.add(label((u.isInEurope()) ? "" : u.getMovesAsString(),
                                  cPlain));
        }
    }


    // Interface ActionListener

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent ae) {
        final String command = ae.getActionCommand();
        if (command != null && command.startsWith(FILTER)) {
            filter = command.substring(FILTER.length());
            update();
            reportPanel.revalidate();
            reportPanel.repaint();
        } else {
            super.actionPerformed(ae);
        }
    }
}
