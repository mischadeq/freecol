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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.Map;

import javax.swing.JComponent;

import net.sf.freecol.client.control.RouteHistory;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Turn;


/**
 * A small bar chart of what a trade route unloaded in each of the
 * last turns.  Hover a bar for the goods of that turn.
 */
public class MovedChart extends JComponent {

    /** How many turns are shown. */
    public static final int TURNS = 10;

    private static final int BAR = 7, GAP = 2, HEIGHT = 26;

    private final RouteHistory history;
    private final String routeId;
    private final int lastTurn;
    private final Color color;
    private final Specification spec;
    private final int[] totals;


    /**
     * Create a chart.
     *
     * @param history The {@code RouteHistory} to show.
     * @param routeId The trade route id.
     * @param lastTurn The current turn (shown last).
     * @param color The bar colour (the route's colour).
     * @param spec The {@code Specification}, for goods names.
     */
    public MovedChart(RouteHistory history, String routeId, int lastTurn,
                      Color color, Specification spec) {
        this.history = history;
        this.routeId = routeId;
        this.lastTurn = lastTurn;
        this.color = color;
        this.spec = spec;
        this.totals = history.totals(routeId, lastTurn, TURNS);
        Dimension d = new Dimension(TURNS * (BAR + GAP), HEIGHT);
        setPreferredSize(d);
        setMinimumSize(d);
        setOpaque(false);
        setToolTipText(""); // Turns tool tips on; text per bar below
    }

    /** @return The largest amount moved in one turn. */
    public int getMax() {
        int max = 0;
        for (int t : this.totals) max = Math.max(max, t);
        return max;
    }

    /** @return The total moved over the turns shown. */
    public int getTotal() {
        int sum = 0;
        for (int t : this.totals) sum += t;
        return sum;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        final int max = Math.max(1, getMax());
        final int h = getHeight() - 2;
        g2.setColor(new Color(0, 0, 0, 40));
        g2.drawLine(0, h + 1, TURNS * (BAR + GAP) - GAP, h + 1);
        for (int i = 0; i < TURNS; i++) {
            final int x = i * (BAR + GAP);
            if (this.totals[i] <= 0) {
                g2.setColor(new Color(0, 0, 0, 50));
                g2.fillRect(x, h - 1, BAR, 2);
                continue;
            }
            final int bh = Math.max(3, Math.round(h * this.totals[i] / (float)max));
            g2.setColor((i == TURNS - 1) ? this.color.brighter() : this.color);
            g2.fillRect(x, h + 1 - bh, BAR, bh);
            g2.setColor(this.color.darker());
            g2.drawRect(x, h + 1 - bh, BAR - 1, bh - 1);
        }
        g2.dispose();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        final int i = Math.min(TURNS - 1, Math.max(0, e.getX() / (BAR + GAP)));
        final int turn = this.lastTurn - TURNS + 1 + i;
        final String when = Messages.message(Turn.getTurnLabel(turn));
        final Map<String, Integer> goods = this.history.byGoods(this.routeId, turn);
        if (goods.isEmpty()) {
            return Messages.message(net.sf.freecol.common.model.StringTemplate
                .template("movedChart.nothing").addName("%turn%", when));
        }
        StringBuilder sb = new StringBuilder("<html><b>").append(when)
            .append("</b>");
        for (Map.Entry<String, Integer> en : goods.entrySet()) {
            sb.append("<br>").append(en.getValue()).append(' ')
                .append(goodsName(en.getKey()));
        }
        return sb.append("</html>").toString();
    }

    private String goodsName(String id) {
        try {
            return Messages.getName(this.spec.getGoodsType(id));
        } catch (RuntimeException re) {
            return id;
        }
    }
}
