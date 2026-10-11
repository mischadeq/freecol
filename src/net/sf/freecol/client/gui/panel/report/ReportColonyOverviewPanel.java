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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.SupplyForecast;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.ProductionInfo;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.WorkLocation;


/**
 * This panel displays the colony overview: one row per colony showing
 * what it mainly produces, what it is short of, what is piling up in
 * the warehouse and what it is building.
 */
public final class ReportColonyOverviewPanel extends ReportPanel {

    /** How many goods to show in each of the goods columns. */
    private static final int MAX_PRODUCES = 3;
    private static final int MAX_NEEDS = 4;
    private static final int MAX_STOCK = 4;

    /** Stock at or above this percentage of capacity gets a warning. */
    private static final int NEARLY_FULL_PERCENT = 90;

    private static final String PREFIX = "report.colonyOverview.";

    /** Prefix of the action command of the column header buttons. */
    private static final String SORT = "sort:";

    /** The columns, in display order. */
    private static final String[] COLUMNS = {
        "colony", "size", "produces", "needs", "stock", "capacity",
        "building"
    };

    /**
     * The column the report is sorted by, or null for the order set
     * in the preferences.  Static so that the choice is remembered
     * while the game is running.
     */
    private static String sortColumn = null;

    /** Is the sort order reversed? */
    private static boolean sortReversed = false;

    /** Building and unit icons are fitted into a box of this size. */
    private static final Dimension BUILD_ICON_SIZE = new Dimension(48, 32);

    /** A goods type with an amount and a severity, for display. */
    private static final class Entry {
        final GoodsType type;
        final int amount;
        final Color color;
        final String toolTip;
        /** Lower ranks are more pressing.  Used for sorting only. */
        final int rank;

        Entry(GoodsType type, int amount, Color color, String toolTip) {
            this(type, amount, color, toolTip, 0);
        }

        Entry(GoodsType type, int amount, Color color, String toolTip,
              int rank) {
            this.type = type;
            this.amount = amount;
            this.color = color;
            this.toolTip = toolTip;
            this.rank = rank;
        }
    }

    private final Specification spec;
    private final ImageLibrary lib;
    private final List<Colony> colonies = new ArrayList<>();
    private final List<GoodsType> goodsTypes = new ArrayList<>();

    private final Color cAlarm;
    private final Color cWarn;
    private final Color cPlain;
    private final Color cGood;
    private final Color cCovered;

    /** What the trade routes are going to deliver, and when. */
    private final SupplyForecast forecast;


    /**
     * Creates the colony overview report.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ReportColonyOverviewPanel(FreeColClient freeColClient) {
        super(freeColClient, "reportColonyOverviewAction");

        this.spec = getSpecification();
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

        this.forecast = new SupplyForecast(getMyPlayer());

        // Abandoned colonies can linger on the list with no units.
        final Comparator<Colony> colonyComparator
            = freeColClient.getClientOptions().getColonyComparator();
        for (Colony c : getMyPlayer().getColonyList()) {
            if (c.getUnitCount() > 0) this.colonies.add(c);
        }
        this.colonies.sort(colonyComparator);

        // Debug aid: when the debug menus are on and we are running
        // the server ourselves, also list every other European
        // nation's colonies, read from the server's copy of the game.
        // This gives well developed colonies to test the report with.
        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)
            && freeColClient.getFreeColServer() != null) {
            final String myId = getMyPlayer().getId();
            for (Player p : freeColClient.getFreeColServer().getGame()
                     .getLiveEuropeanPlayerList()) {
                if (myId.equals(p.getId())) continue;
                List<Colony> theirs = new ArrayList<>();
                for (Colony c : p.getColonyList()) {
                    if (c.getUnitCount() > 0) theirs.add(c);
                }
                theirs.sort(colonyComparator);
                this.colonies.addAll(theirs);
            }
        }

        // The goods worth reporting: things that can be stored and
        // shipped, plus liberty bells and crosses.
        for (GoodsType gt : spec.getGoodsTypeList()) {
            if ((gt.isStorable() && !gt.isTradeGoods())
                || gt.isLibertyType() || gt.isImmigrationType()) {
                this.goodsTypes.add(gt);
            }
        }
        this.goodsTypes.sort(GoodsType.goodsTypeComparator);

        update();
    }


    // Data gathering

    /**
     * What does a colony mainly produce?  The goods with the highest
     * positive net production.
     *
     * @param colony The {@code Colony} to examine.
     * @return A list of entries, best first.
     */
    private List<Entry> getProduces(Colony colony) {
        List<Entry> result = new ArrayList<>();
        for (GoodsType gt : this.goodsTypes) {
            int p = colony.getAdjustedNetProductionOf(gt);
            if (p <= 0) continue;
            result.add(new Entry(gt, p, cGood,
                    Messages.message(message("produces.description",
                                             colony, gt)
                        .addAmount("%amount%", p))));
        }
        result.sort(Comparator.comparingInt((Entry e) -> e.amount)
            .reversed());
        return truncate(result, MAX_PRODUCES);
    }

    /**
     * What is a colony short of?  Goods that are draining away each
     * turn, then inputs its buildings could use more of.
     *
     * @param colony The {@code Colony} to examine.
     * @return A list of entries, most urgent first.
     */
    private List<Entry> getNeeds(Colony colony) {
        List<Entry> result = new ArrayList<>();

        // Goods with negative net production, soonest to run out first.
        // A need that a trade route will cover in time is shown in
        // green and sorted after the ones that are not covered.
        List<Entry> draining = new ArrayList<>();
        final Map<GoodsType, Integer> turnsLeft = new HashMap<>();
        final Map<GoodsType, Integer> urgency = new HashMap<>();
        final boolean mine = isMine(colony);
        for (GoodsType gt : this.goodsTypes) {
            int p = colony.getAdjustedNetProductionOf(gt);
            if (p >= 0) continue;
            int stock = colony.getGoodsCount(gt);
            int turns = stock / -p;
            turnsLeft.put(gt, turns);
            String tip = Messages.message(message("draining.description",
                                                  colony, gt)
                .addAmount("%amount%", -p)
                .addAmount("%stock%", stock)
                .addAmount("%turns%", turns));
            Color c = (turns <= Colony.FAMINE_TURNS) ? cAlarm : cWarn;
            boolean covered = false;
            if (mine) {
                SupplyForecast.Outlook o
                    = this.forecast.getOutlook(colony, gt, turns);
                String supply = supplyTip(o);
                if (supply != null) tip += " " + supply;
                if (o.status == SupplyForecast.Status.IN_TIME) {
                    c = cCovered;
                    covered = true;
                } else if (o.isBroken()) {
                    c = cAlarm;
                }
            }
            urgency.put(gt, turns + ((covered) ? Unit.MANY_TURNS : 0));
            draining.add(new Entry(gt, p, c, tip, urgency.get(gt)));
        }
        draining.sort(Comparator.comparingInt(e -> urgency.get(e.type)));
        result.addAll(draining);

        // Inputs that buildings would consume more of if they had it.
        final Map<GoodsType, Integer> shortfall = new HashMap<>();
        for (WorkLocation wl : colony.getCurrentWorkLocationsList()) {
            ProductionInfo pi = colony.getProductionInfo(wl);
            if (pi == null) continue;
            for (AbstractGoods ag : pi.getConsumptionDeficit()) {
                if (ag.getAmount() <= 0) continue;
                shortfall.merge(ag.getType(), ag.getAmount(), Integer::sum);
            }
        }
        List<Entry> starved = new ArrayList<>();
        for (Map.Entry<GoodsType, Integer> e : shortfall.entrySet()) {
            if (turnsLeft.containsKey(e.getKey())) continue; // Already shown
            starved.add(new Entry(e.getKey(), -e.getValue(), cPlain,
                    Messages.message(message("shortfall.description",
                                             colony, e.getKey())
                        .addAmount("%amount%", e.getValue())),
                    2 * Unit.MANY_TURNS));
        }
        starved.sort(Comparator.comparingInt(e -> e.amount));
        result.addAll(starved);

        return truncate(result, MAX_NEEDS);
    }

    /**
     * What is piling up in the warehouse?  Goods about to overflow
     * come first, then the largest stocks.
     *
     * @param colony The {@code Colony} to examine.
     * @return A list of entries.
     */
    private List<Entry> getStock(Colony colony) {
        final int capacity = colony.getWarehouseCapacity();
        List<Entry> overflowing = new ArrayList<>();
        List<Entry> others = new ArrayList<>();
        for (GoodsType gt : this.goodsTypes) {
            if (!gt.isStorable()) continue;
            int stock = colony.getGoodsCount(gt);
            if (stock <= 0) continue;
            int p = colony.getAdjustedNetProductionOf(gt);
            if (!gt.limitIgnored() && p > 0 && stock + p > capacity) {
                overflowing.add(new Entry(gt, stock, cAlarm,
                        Messages.message(message("overflow.description",
                                                 colony, gt)
                            .addAmount("%amount%", stock)
                            .addAmount("%capacity%", capacity)
                            .addAmount("%waste%", stock + p - capacity))));
            } else {
                boolean nearlyFull = !gt.limitIgnored()
                    && stock * 100 >= capacity * NEARLY_FULL_PERCENT;
                others.add(new Entry(gt, stock,
                        (nearlyFull) ? cWarn : cPlain,
                        Messages.message(message("stock.description",
                                                 colony, gt)
                            .addAmount("%amount%", stock)
                            .addAmount("%capacity%", capacity))));
            }
        }
        final Comparator<Entry> largestFirst
            = Comparator.comparingInt((Entry e) -> e.amount).reversed();
        overflowing.sort(largestFirst);
        others.sort(largestFirst);
        overflowing.addAll(others);
        return truncate(overflowing, MAX_STOCK);
    }

    /**
     * Is this one of our own colonies?  In debug mode the report
     * also lists other nations' colonies.
     *
     * @param colony The {@code Colony} to check.
     * @return True if the colony belongs to the player.
     */
    private boolean isMine(Colony colony) {
        return getMyPlayer().getId().equals(colony.getOwner().getId());
    }

    /**
     * Describe how the trade routes cover a need.
     *
     * @param o The supply {@code Outlook} for the need.
     * @return A sentence for a tooltip, or null if no trade route
     *     is involved.
     */
    private String supplyTip(SupplyForecast.Outlook o) {
        final String key = PREFIX + "supply.";
        switch (o.status) {
        case IN_TIME: case LATE:
            return Messages.message(StringTemplate
                .template(key + ((o.status == SupplyForecast.Status.LATE)
                        ? "late" : "inTime"))
                .addStringTemplate("%unit%", o.delivery.unit
                    .getLabel(Unit.UnitLabelType.NATIONAL))
                .addName("%route%", o.route.getName())
                .addAmount("%turns%", o.delivery.turns)
                .addAmount("%amount%", o.delivery.amount));
        case NO_CARRIER:
            return Messages.message(StringTemplate
                .template(key + "noCarrier")
                .addName("%route%", o.route.getName()));
        case NOTHING_COMING:
            return Messages.message(StringTemplate
                .template(key + "nothingComing")
                .addName("%route%", o.route.getName()));
        default:
            return null;
        }
    }

    private static List<Entry> truncate(List<Entry> list, int n) {
        return (list.size() <= n) ? list : new ArrayList<>(list.subList(0, n));
    }

    /**
     * Start a tooltip message for a colony and goods type.
     *
     * @param suffix The message key suffix.
     * @param colony The {@code Colony} the message is about.
     * @param gt The {@code GoodsType} the message is about.
     * @return A {@code StringTemplate} to add further amounts to.
     */
    private StringTemplate message(String suffix, Colony colony,
                                   GoodsType gt) {
        return StringTemplate.template(PREFIX + suffix)
            .addName("%colony%", colony.getName())
            .addNamed("%goods%", gt);
    }


    // Display

    /**
     * Make a column header.  Clicking it sorts the report by that
     * column, clicking it again reverses the order.
     *
     * @param column The column name.
     * @return The header button.
     */
    private JButton headerButton(String column) {
        String text = Messages.message(PREFIX + "header." + column);
        JButton b = Utility.getLinkButton(text, null, SORT + column);
        final Font font = b.getFont().deriveFont(Font.BOLD);
        b.setFont(font);
        if (column.equals(sortColumn)) {
            // Mark the sorted column with an arrow if the font has one.
            final char arrow = (sortReversed) ? '\u25B2' : '\u25BC';
            b.setText(text + " " + ((font.canDisplay(arrow))
                    ? String.valueOf(arrow)
                    : (sortReversed) ? "^" : "v"));
        }
        b.setForeground(Color.BLACK);
        b.setToolTipText(Messages.message(PREFIX + "header." + column
                + ".description")
            + " " + Messages.message(PREFIX + "header.sort"));
        b.addActionListener(this);
        return b;
    }

    /**
     * Get the comparator that sorts colonies by a column.  The most
     * interesting colonies come first: the biggest, the most urgent
     * need, the fullest warehouse, the idle build queue.
     *
     * @param column The column name.
     * @return A suitable {@code Comparator}.
     */
    private Comparator<Colony> getComparator(String column) {
        switch (column) {
        case "colony":
            return Comparator.comparing(Colony::getName,
                                        String.CASE_INSENSITIVE_ORDER);
        case "size":
            return Comparator.comparingInt(c -> -c.getUnitCount());
        case "produces":
            // Group by main product, biggest producer first.
            return Comparator.comparingInt((Colony c) -> {
                    List<Entry> l = getProduces(c);
                    return (l.isEmpty()) ? Integer.MAX_VALUE
                        : l.get(0).type.getIndex();
                }).thenComparingInt(c -> {
                    List<Entry> l = getProduces(c);
                    return (l.isEmpty()) ? 0 : -l.get(0).amount;
                });
        case "needs":
            return Comparator.comparingInt(c -> {
                    List<Entry> l = getNeeds(c);
                    return (l.isEmpty()) ? Integer.MAX_VALUE : l.get(0).rank;
                });
        case "stock":
            // Overflowing warehouses first, then the largest stock.
            return Comparator.comparingInt(c -> {
                    List<Entry> l = getStock(c);
                    if (l.isEmpty()) return Integer.MAX_VALUE;
                    Entry e = l.get(0);
                    return -e.amount - ((e.color == cAlarm) ? 1000000 : 0);
                });
        case "capacity":
            return Comparator.comparingInt(c -> -c.getWarehouseCapacity());
        case "building":
            // Idle first, then blocked, then by turns to completion.
            return Comparator.comparingInt(c -> {
                    BuildableType build = c.getCurrentlyBuilding();
                    if (build == null) return -2;
                    int turns = c.getTurnsToComplete(build, null);
                    return (turns < 0) ? -1 : turns;
                });
        default:
            return null;
        }
    }

    private JPanel cell() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        return p;
    }

    /**
     * Make a cell full of goods entries.
     *
     * @param entries The entries to show.
     * @param showName Show the goods name as well as the icon.
     * @param signed Show a plus sign on positive amounts.
     * @return The cell.
     */
    private JPanel goodsCell(List<Entry> entries, boolean showName,
                             boolean signed) {
        JPanel p = cell();
        if (entries.isEmpty()) {
            JLabel none = new JLabel("-");
            none.setForeground(Color.GRAY);
            p.add(none);
            return p;
        }
        for (Entry e : entries) {
            String amount = (signed && e.amount > 0) ? "+" + e.amount
                : Integer.toString(e.amount);
            String text = (showName)
                ? Messages.getName(e.type) + " " + amount
                : amount;
            JLabel l = new JLabel(text,
                new ImageIcon(lib.getSmallerGoodsTypeImage(e.type)),
                JLabel.LEADING);
            l.setForeground(e.color);
            l.setToolTipText(e.toolTip);
            p.add(l);
        }
        return p;
    }

    /**
     * Make the cell describing what a colony is building.
     *
     * @param colony The {@code Colony} to examine.
     * @return The cell.
     */
    private JPanel buildingCell(Colony colony) {
        JPanel p = cell();
        final BuildableType build = colony.getCurrentlyBuilding();
        if (build == null) {
            JLabel l = new JLabel(Messages.message(PREFIX + "build.nothing"));
            l.setForeground(cAlarm);
            p.add(l);
            return p;
        }

        AbstractGoods needed = new AbstractGoods();
        int turns = colony.getTurnsToComplete(build, needed);
        String status, toolTip = null;
        Color c;
        if (turns >= 0) {
            status = Messages.message(StringTemplate
                .template(PREFIX + "build.turns")
                .addAmount("%turns%", turns));
            c = cPlain;
        } else if (needed.getType() != null) {
            status = Messages.message(StringTemplate
                .template(PREFIX + "build.needs")
                .addAmount("%amount%", needed.getAmount())
                .addNamed("%goods%", needed.getType()));
            c = cWarn; // Waiting, not idle
            if (isMine(colony)) {
                SupplyForecast.Outlook o = this.forecast
                    .getOutlook(colony, needed.getType(), -1);
                toolTip = supplyTip(o);
                if (o.delivery != null
                    && o.delivery.amount >= needed.getAmount()) {
                    // Enough is on its way, no cause for concern.
                    status = Messages.message(StringTemplate
                        .template(PREFIX + "build.arriving")
                        .addNamed("%goods%", needed.getType())
                        .addAmount("%turns%", o.delivery.turns));
                    c = cPlain;
                } else if (o.isBroken()) {
                    c = cAlarm;
                }
            }
        } else {
            status = Messages.message(PREFIX + "build.blocked");
            c = cWarn; // Waiting, not idle
        }
        JLabel l = new JLabel(Messages.getName(build) + ", " + status,
            new ImageIcon(lib.getSmallBuildableTypeImageWithWithSize(build,
                    colony.getOwner(), lib.scale(BUILD_ICON_SIZE))),
            JLabel.LEADING);
        l.setForeground(c);
        if (toolTip != null) l.setToolTipText(toolTip);
        p.add(l);
        return p;
    }

    private void update() {
        reportPanel.removeAll();
        reportPanel.setLayout(new MigLayout("fillx, wrap 7, gap 12 6",
                "[l][r][l][l][l][r][l, grow]", ""));

        for (String column : COLUMNS) {
            reportPanel.add(headerButton(column));
        }
        reportPanel.add(new JSeparator(JSeparator.HORIZONTAL),
                        "span, growx");

        if (this.colonies.isEmpty()) {
            reportPanel.add(new JLabel(Messages.message(PREFIX + "none")),
                            "span");
            return;
        }

        // Sort a copy, so the order from the preferences is kept as
        // the tie-breaker and as the fallback.
        List<Colony> sorted = new ArrayList<>(this.colonies);
        Comparator<Colony> comparator = (sortColumn == null) ? null
            : getComparator(sortColumn);
        if (comparator != null) {
            sorted.sort((sortReversed) ? comparator.reversed() : comparator);
        }

        for (Colony colony : sorted) {
            if (isMine(colony)) {
                JButton b = Utility.getLinkButton(colony.getName(), null,
                                                  colony.getId());
                b.addActionListener(this);
                reportPanel.add(b);
            } else { // Debug mode only: another nation's colony
                JLabel l = new JLabel(colony.getName() + " ("
                    + Messages.message(colony.getOwner().getNationLabel())
                    + ")");
                l.setForeground(Color.GRAY);
                reportPanel.add(l);
            }

            reportPanel.add(new JLabel(Integer.toString(colony.getUnitCount())));
            reportPanel.add(goodsCell(getProduces(colony), true, true));
            reportPanel.add(goodsCell(getNeeds(colony), false, false));
            reportPanel.add(goodsCell(getStock(colony), false, false));
            reportPanel.add(new JLabel(Integer.toString(colony.getWarehouseCapacity())));
            reportPanel.add(buildingCell(colony));
        }
    }


    // Interface ActionListener

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent ae) {
        final String command = ae.getActionCommand();
        if (command != null && command.startsWith(SORT)) {
            final String column = command.substring(SORT.length());
            if (column.equals(sortColumn)) {
                sortReversed = !sortReversed;
            } else {
                sortColumn = column;
                sortReversed = false;
            }
            update();
            reportPanel.revalidate();
            reportPanel.repaint();
        } else {
            super.actionPerformed(ae);
        }
    }
}
