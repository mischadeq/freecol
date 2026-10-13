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

package net.sf.freecol.client.gui.panel;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.RoutePlan;
import net.sf.freecol.client.control.SupplyForecast;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.panel.report.LogisticsMap;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Unit;


/**
 * The trade route editor.
 *
 * One screen: the name, a small map with the route (click a colony to
 * add it as a stop), a row of buttons to add a stop, the stops as a
 * table with what is loaded and unloaded at each and what is in store
 * there, the goods to click to load at the selected stop, and the
 * mistakes found in the route.
 *
 * FreeCol stores, per stop, the cargo a carrier leaves with; what is
 * unloaded follows from the stop before.  This editor shows both.
 *
 * Carriers are assigned from the unit (or group), not here.
 */
public final class TradeRouteInputPanel extends FreeColPanel {

    private static final Logger logger = Logger.getLogger(TradeRouteInputPanel.class.getName());

    private static final String PREFIX = "tradeRouteEditor.";

    /** The route as given; only changed when OK is pressed. */
    private final TradeRoute newRoute;

    /** The stops being edited (copies). */
    private final List<TradeRouteStop> stops = new ArrayList<>();

    /** The selected stop, or -1. */
    private int selected = -1;

    /** The carriers on the route, and their smallest number of holds. */
    private final List<Unit> carriers;
    private final int minHolds;

    private final JTextField nameField;
    private final JCheckBox silenceBox;
    private final JPanel mapHolder = new JPanel(new MigLayout("insets 0, fill"));
    private final JPanel stopsPanel;
    private final JPanel goodsPanel = new JPanel(new MigLayout("insets 0, gap 2"));
    private final JLabel goodsTitle = new JLabel();
    private final JPanel warningsPanel = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 1"));

    private final Color cPlain, cAlarm, cDim, cSelected, cGood;

    /** Goods being dragged from the palette, or null. */
    private GoodsType dragging = null;


    /**
     * Create the trade route editor.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param newRoute The {@code TradeRoute} to edit.  Its name is set
     *     to null if the edit is cancelled.
     */
    public TradeRouteInputPanel(FreeColClient freeColClient,
                                TradeRoute newRoute) {
        super(freeColClient, null,
              new MigLayout("wrap 1, fill, insets 10 16 10 16, gap 0 6",
                            "[fill, grow]", ""));
        final Game game = freeColClient.getGame();
        final Player player = getMyPlayer();
        this.newRoute = newRoute;
        final TradeRoute copy = newRoute.copy(game);
        this.stops.addAll(copy.getStopList());

        this.cPlain = ImageLibrary.getColor("color.report.colony.plain",
                                            Color.DARK_GRAY);
        this.cAlarm = ImageLibrary.getColor("color.report.colony.alarm",
                                            Color.RED);
        this.cGood = new Color(0x2E7D32);
        this.cDim = new Color(110, 100, 85);
        this.cSelected = new Color(0xE9D8AA);

        this.carriers = SupplyForecast.getCarriers(player, newRoute);
        int h = 0;
        for (Unit u : this.carriers) {
            final int c = u.getCargoCapacity();
            if (c > 0) h = (h == 0) ? c : Math.min(h, c);
        }
        this.minHolds = h;

        // Title
        add(Utility.localizedHeader("tradeRouteInputPanel.editRoute",
                                    Utility.FONTSPEC_TITLE),
            "align center");

        // Name, silence, carriers
        this.nameField = new JTextField(copy.getName(), 24);
        this.silenceBox = new JCheckBox(Messages.message("tradeRouteInputPanel.silence"));
        this.silenceBox.setOpaque(false);
        this.silenceBox.setSelected(copy.isSilent());
        JPanel top = new JPanel(new MigLayout("insets 0", "[][grow, fill]20[]20[]"));
        top.setOpaque(false);
        top.add(Utility.localizedLabel("tradeRouteInputPanel.nameLabel"));
        top.add(this.nameField);
        top.add(this.silenceBox);
        top.add(carriersLabel(player));
        add(top);

        // Map
        this.mapHolder.setOpaque(false);
        add(this.mapHolder, "h 180!");

        // Add stop buttons
        JPanel addRow = new JPanel(new MigLayout("insets 0, gap 4"));
        addRow.setOpaque(false);
        addRow.add(boldLabel(Messages.message(PREFIX + "addStop")));
        for (Colony colony : player.getColonyList()) {
            addRow.add(addButton(colony, colony.getName()));
        }
        final Europe europe = player.getEurope();
        if (europe != null) {
            addRow.add(addButton(europe,
                Messages.message(europe.getLocationLabelFor(player))));
        }
        addRow.add(label(Messages.message(PREFIX + "addStop.hint"), this.cDim));
        add(addRow);

        // Stops
        this.stopsPanel = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 0",
                                                   "[fill, grow]", ""));
        this.stopsPanel.setOpaque(false);
        JScrollPane scroll = new JScrollPane(this.stopsPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, "h 150:300:, growy, pushy");

        // Goods palette
        this.goodsPanel.setOpaque(false);
        JPanel goodsRow = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 2"));
        goodsRow.setOpaque(false);
        goodsRow.add(this.goodsTitle);
        goodsRow.add(this.goodsPanel);
        goodsRow.add(label(Messages.message(PREFIX + "goods.hint"), this.cDim));
        add(goodsRow);

        // Warnings
        this.warningsPanel.setOpaque(false);
        add(this.warningsPanel, "gaptop 4");

        // Buttons
        JButton cancelButton = Utility.localizedButton("cancel");
        cancelButton.setActionCommand(CANCEL);
        cancelButton.addActionListener(this);
        add(okButton, "newline 10, split 2, tag ok, growx 0");
        add(cancelButton, "tag cancel, growx 0");
        setEscapeAction(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    cancelTradeRoute();
                }
            });
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteStop");
        getActionMap().put("deleteStop", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    if (!(SwingUtilities.getRootPane(nameField) != null
                          && nameField.isFocusOwner())) removeStop(selected);
                }
            });

        if (!this.stops.isEmpty()) this.selected = 0;
        refresh();
        setPreferredSize(new Dimension(
            Math.max(940, getPreferredSize().width), 820));
        getGUI().restoreSavedSize(this, getPreferredSize());
    }


    // Building blocks

    private static JLabel label(String text, Color color) {
        JLabel l = new JLabel(text);
        if (color != null) l.setForeground(color);
        return l;
    }

    private static JLabel boldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private ImageIcon goodsIcon(GoodsType gt) {
        return new ImageIcon(getImageLibrary().getSmallerGoodsTypeImage(gt));
    }

    private JLabel carriersLabel(Player player) {
        if (this.carriers.isEmpty()) {
            JLabel l = label(Messages.message(PREFIX + "noCarriers"), this.cDim);
            l.setToolTipText(Messages.message(PREFIX + "noCarriers.tip"));
            return l;
        }
        StringBuilder sb = new StringBuilder();
        for (Unit u : this.carriers) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Messages.message(u.getLabel(Unit.UnitLabelType.PLAIN)));
        }
        JLabel l = label(Messages.message(StringTemplate
                .template(PREFIX + "carriers")
                .addAmount("%number%", this.carriers.size())), this.cPlain);
        l.setToolTipText(sb.toString());
        return l;
    }

    private JButton addButton(Location loc, String name) {
        JButton b = new JButton(name);
        b.setMargin(new java.awt.Insets(1, 6, 1, 6));
        b.setToolTipText(Messages.message(StringTemplate
                .template(PREFIX + "addStop.tip").addName("%place%", name)));
        b.addActionListener(ae -> addStop(loc));
        return b;
    }

    private JButton smallButton(String text, String tipKey, Runnable r,
                                boolean enabled) {
        JButton b = new JButton(text);
        b.setMargin(new java.awt.Insets(0, 4, 0, 4));
        b.setToolTipText(Messages.message(PREFIX + tipKey));
        b.setEnabled(enabled);
        b.addActionListener(ae -> r.run());
        return b;
    }


    // Editing

    /**
     * Add a stop after the selected one (or at the end).
     *
     * @param loc The {@code Location} to stop at.
     */
    private void addStop(Location loc) {
        final TradeRouteStop stop = new TradeRouteStop(getGame(), loc);
        final int at = (this.selected < 0) ? this.stops.size()
            : this.selected + 1;
        this.stops.add(at, stop);
        this.selected = at;
        refresh();
    }

    private void removeStop(int i) {
        if (i < 0 || i >= this.stops.size()) return;
        this.stops.remove(i);
        if (this.selected >= this.stops.size()) {
            this.selected = this.stops.size() - 1;
        }
        refresh();
    }

    private void moveStop(int i, int delta) {
        final int j = i + delta;
        if (i < 0 || j < 0 || j >= this.stops.size()) return;
        this.stops.add(j, this.stops.remove(i));
        this.selected = j;
        refresh();
    }

    /** Load one more hold of goods at a stop. */
    private void addHold(int i, GoodsType gt) {
        if (i < 0 || i >= this.stops.size()) return;
        this.stops.get(i).addCargo(gt);
        this.selected = i;
        refresh();
    }

    /** Load one hold less of goods at a stop. */
    private void removeHold(int i, GoodsType gt) {
        if (i < 0 || i >= this.stops.size()) return;
        final TradeRouteStop stop = this.stops.get(i);
        List<GoodsType> cargo = new ArrayList<>(stop.getCargo());
        int k = cargo.lastIndexOf(gt);
        if (k >= 0) cargo.remove(k);
        stop.setCargo(cargo);
        this.selected = i;
        refresh();
    }

    private void select(int i) {
        if (i == this.selected) return;
        this.selected = i;
        refresh();
    }


    // Drawing

    /** Rebuild everything from the stops. */
    private void refresh() {
        rebuildMap();
        rebuildStops();
        rebuildGoods();
        rebuildWarnings();
        revalidate();
        repaint();
    }

    private void rebuildMap() {
        final Player player = getMyPlayer();
        LogisticsMap.Route line = new LogisticsMap.Route(this.nameField.getText(),
            LogisticsMap.routeColor(0));
        for (TradeRouteStop stop : this.stops) {
            final Location loc = stop.getLocation();
            if (loc != null && loc.getTile() != null) line.addStop(loc.getTile());
        }
        List<LogisticsMap.Route> routes = new ArrayList<>();
        routes.add(line);
        LogisticsMap map = new LogisticsMap(getImageLibrary(), getGame().getMap(),
            player, routes, (Tile t) -> {
                // Click a colony of ours: add it as a stop.
                if (t.getColony() != null && player.owns(t.getColony())) {
                    addStop(t.getColony());
                }
            }).includeAllColonies();
        map.setPreferredSize(new Dimension(600, 180));
        map.setMinimumSize(new Dimension(300, 180));
        this.mapHolder.removeAll();
        this.mapHolder.add(map, "grow");
    }

    private void rebuildStops() {
        final Player player = getMyPlayer();
        this.stopsPanel.removeAll();
        final String cols = "[24!][130!][210!][150!][grow, fill][]";
        JPanel head = new JPanel(new MigLayout("insets 2 6 2 6", cols));
        head.setOpaque(false);
        head.add(label("#", this.cDim));
        head.add(label(Messages.message(PREFIX + "col.stop"), this.cDim));
        head.add(label(Messages.message(PREFIX + "col.loads"), this.cDim));
        head.add(label(Messages.message(PREFIX + "col.unloads"), this.cDim));
        head.add(label(Messages.message(PREFIX + "col.store"), this.cDim));
        this.stopsPanel.add(head);
        if (this.stops.isEmpty()) {
            this.stopsPanel.add(label(Messages.message(PREFIX + "noStops"),
                                      this.cDim), "gapleft 8");
        }
        final int n = this.stops.size();
        for (int i = 0; i < n; i++) {
            final int index = i;
            final TradeRouteStop stop = this.stops.get(i);
            final String place = RoutePlan.placeName(stop, player);
            final boolean sel = i == this.selected;
            JPanel row = new JPanel(new MigLayout("insets 3 6 3 6", cols)) {
                    @Override
                    protected void paintComponent(java.awt.Graphics g) {
                        if (sel) {
                            g.setColor(cSelected);
                            g.fillRect(0, 0, getWidth(), getHeight());
                        }
                    }
                };
            row.setOpaque(false);
            row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                    new Color(0, 0, 0, 30)));
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.putClientProperty("stopIndex", i);
            row.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        select(index);
                    }
                });
            row.add(label(String.valueOf(i + 1), this.cDim));
            JLabel name = boldLabel(place);
            row.add(name);

            // Loads: one chip per goods, "x2" for two holds.
            JPanel loads = new JPanel(new MigLayout("insets 0, gap 3"));
            loads.setOpaque(false);
            final Map<GoodsType, Integer> holds = RoutePlan.holds(stop);
            if (holds.isEmpty()) loads.add(label("—", this.cDim));
            for (Map.Entry<GoodsType, Integer> e : holds.entrySet()) {
                final GoodsType gt = e.getKey();
                JLabel chip = new JLabel((e.getValue() > 1)
                    ? "×" + e.getValue() : "", goodsIcon(gt), JLabel.LEADING);
                final int at = RoutePlan.unloadedAt(this.stops, i, gt);
                chip.setToolTipText(Messages.message((at < 0)
                    ? StringTemplate.template(PREFIX + "loadTip.never")
                        .addNamed("%goods%", gt)
                        .addAmount("%holds%", e.getValue())
                    : StringTemplate.template(PREFIX + "loadTip")
                        .addNamed("%goods%", gt)
                        .addAmount("%holds%", e.getValue())
                        .addName("%place%",
                            RoutePlan.placeName(this.stops.get(at), player))));
                if (at < 0) chip.setForeground(this.cAlarm);
                chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                chip.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent me) {
                            removeHold(index, gt);
                        }
                    });
                loads.add(chip);
            }
            row.add(loads);

            // Unloads: worked out from the stop before.
            JPanel unloads = new JPanel(new MigLayout("insets 0, gap 3"));
            unloads.setOpaque(false);
            final List<GoodsType> un = RoutePlan.unloads(this.stops, i);
            if (un.isEmpty()) unloads.add(label("—", this.cDim));
            final String from = RoutePlan.placeName(
                this.stops.get((i - 1 + n) % n), player);
            for (GoodsType gt : un) {
                JLabel chip = new JLabel(goodsIcon(gt));
                chip.setToolTipText(Messages.message(StringTemplate
                        .template(PREFIX + "unloadTip")
                        .addNamed("%goods%", gt)
                        .addName("%from%", from)
                        .addName("%place%", place)));
                unloads.add(chip);
            }
            row.add(unloads);

            // What is in store, for the goods loaded or unloaded here.
            row.add(storeCell(stop, place, holds.keySet(), un));

            // Move and remove
            JPanel buttons = new JPanel(new MigLayout("insets 0, gap 2"));
            buttons.setOpaque(false);
            buttons.add(smallButton("↑", "up", () -> moveStop(index, -1), i > 0));
            buttons.add(smallButton("↓", "down", () -> moveStop(index, 1), i < n - 1));
            buttons.add(smallButton("✕", "remove", () -> removeStop(index), true));
            row.add(buttons);
            this.stopsPanel.add(row);
        }
    }

    private JComponent storeCell(TradeRouteStop stop, String place,
                                 java.util.Collection<GoodsType> loads,
                                 List<GoodsType> unloads) {
        JPanel p = new JPanel(new MigLayout("insets 0, gap 6"));
        p.setOpaque(false);
        if (!(stop.getLocation() instanceof Colony)) {
            p.add(label(Messages.message(PREFIX + "store.market"), this.cDim));
            return p;
        }
        final Colony colony = (Colony)stop.getLocation();
        List<GoodsType> goods = new ArrayList<>(loads);
        for (GoodsType gt : unloads) if (!goods.contains(gt)) goods.add(gt);
        if (goods.isEmpty()) p.add(label("", null));
        for (GoodsType gt : goods) {
            final int amount = colony.getGoodsCount(gt);
            final int net = colony.getNetProductionOf(gt);
            final int keep = colony.getExportData(gt).getExportLevel();
            final int cap = colony.getWarehouseCapacity();
            String text = String.valueOf(amount)
                + ((net == 0) ? "" : (net > 0) ? " +" + net : " " + net);
            JLabel l = new JLabel(text, goodsIcon(gt), JLabel.LEADING);
            boolean loadedHere = loads.contains(gt);
            l.setForeground((loadedHere && amount <= keep && net <= 0)
                ? this.cAlarm
                : (!loadedHere && gt.isStorable() && gt.limitIgnored() == false
                   && amount >= cap) ? this.cAlarm : this.cPlain);
            l.setToolTipText(Messages.message(StringTemplate
                    .template(PREFIX + "storeTip")
                    .addNamed("%goods%", gt)
                    .addName("%place%", place)
                    .addAmount("%amount%", amount)
                    .addAmount("%keep%", keep)
                    .addName("%net%", ((net > 0) ? "+" : "") + net)
                    .addAmount("%capacity%", cap)));
            p.add(l);
        }
        return p;
    }

    private void rebuildGoods() {
        final Player player = getMyPlayer();
        this.goodsPanel.removeAll();
        final boolean on = this.selected >= 0 && this.selected < this.stops.size();
        this.goodsTitle.setText(Messages.message((on)
            ? StringTemplate.template(PREFIX + "goods")
                .addName("%place%", RoutePlan.placeName(
                    this.stops.get(this.selected), player))
            : StringTemplate.template(PREFIX + "goods.none")));
        this.goodsTitle.setFont(this.goodsTitle.getFont().deriveFont(Font.BOLD));
        for (GoodsType gt : getSpecification().getStorableGoodsTypeList()) {
            JButton b = new JButton(goodsIcon(gt));
            b.setMargin(new java.awt.Insets(2, 2, 2, 2));
            b.setEnabled(on);
            b.setToolTipText(Messages.getName(gt));
            b.addActionListener(ae -> addHold(this.selected, gt));
            // Dragging onto a stop loads it there.
            b.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        dragging = gt;
                    }
                    @Override
                    public void mouseReleased(MouseEvent e) {
                        final GoodsType g = dragging;
                        dragging = null;
                        if (g == null || b.contains(e.getPoint())) return;
                        Point pt = SwingUtilities.convertPoint(b, e.getPoint(),
                            stopsPanel);
                        Component c = SwingUtilities.getDeepestComponentAt(
                            stopsPanel, pt.x, pt.y);
                        for (; c != null && c != stopsPanel; c = c.getParent()) {
                            if (c instanceof JComponent && ((JComponent)c)
                                .getClientProperty("stopIndex") != null) {
                                addHold((Integer)((JComponent)c)
                                    .getClientProperty("stopIndex"), g);
                                return;
                            }
                        }
                    }
                });
            this.goodsPanel.add(b);
        }
    }

    private void rebuildWarnings() {
        this.warningsPanel.removeAll();
        List<StringTemplate> ws = RoutePlan.warnings(this.stops, this.minHolds,
                                                     getMyPlayer());
        if (ws.isEmpty()) {
            this.warningsPanel.add(label("✓ " + Messages.message(PREFIX + "ok"),
                                         this.cGood));
        }
        for (StringTemplate w : ws) {
            this.warningsPanel.add(label("⚠ " + Messages.message(w), this.cAlarm));
        }
    }


    // Closing

    /**
     * Make sure the original route is invalid and remove this panel.
     *
     * Public so that this panel can be signalled to close if the parent
     * TradeRoutePanel is closed.
     */
    public void cancelTradeRoute() {
        this.newRoute.setName(null);
        getGUI().removeComponent(this);
    }

    /**
     * Copy the edit into the route and check it.
     *
     * @return True if the trade route is valid.
     */
    private boolean verifyNewTradeRoute() {
        // Check a copy first, so a refused edit leaves the route as it was.
        final TradeRoute check = this.newRoute.copy(getGame());
        check.setName(this.nameField.getText());
        check.clearStops();
        for (TradeRouteStop stop : this.stops) check.addStop(stop);
        StringTemplate err = check.verify();
        if (err != null) {
            getGUI().showInformationPanel(err);
            return false; // Stay open, so the route can be fixed
        }
        this.newRoute.setName(this.nameField.getText());
        this.newRoute.clearStops();
        for (TradeRouteStop stop : this.stops) this.newRoute.addStop(stop);
        this.newRoute.setSilent(this.silenceBox.isSelected());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent ae) {
        final String command = ae.getActionCommand();
        if (command == null) return;
        switch (command) {
        case OK:
            if (!verifyNewTradeRoute()) return;
            // Return to TradeRoutePanel, which will add the route
            // if needed, and it is valid.
            super.actionPerformed(ae);
            break;
        case CANCEL:
            cancelTradeRoute();
            break;
        default:
            super.actionPerformed(ae);
            break;
        }
    }
}
