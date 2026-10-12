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

package net.sf.freecol.client.gui.action;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.ImageIcon;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.StringTemplate;


/**
 * An action for executing goto orders immediately.  Its button in the
 * bottom bar shows how many units still have orders to carry out.
 */
public class ExecuteGotoOrdersAction extends MapboardAction {

    public static final String id = "executeGotoOrdersAction";


    /**
     * Creates a new {@code ExecuteGotoOrdersAction}.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ExecuteGotoOrdersAction(FreeColClient freeColClient) {
        super(freeColClient, id);

        addImageIcons("execute");
    }

    /** The count last drawn on the button, or -1 for none yet. */
    private int shownCount = -1;

    /** The button image last set, to notice when it is replaced. */
    private Object shownImage = null;


    // Override FreeColAction

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean shouldBeEnabled() {
        if (!super.shouldBeEnabled()) return false;
        final int n = igc().countPendingOrders();
        if (n != this.shownCount
            || getValue(BUTTON_IMAGE) != this.shownImage) showCount(n);
        return n > 0;
    }

    /**
     * Put the number of pending orders on the button and in its name.
     *
     * @param n The number of units with pending orders.
     */
    private void showCount(int n) {
        this.shownCount = n;
        putValue(NAME, (n <= 0) ? Messages.message(id + ".name")
            : Messages.message(StringTemplate.template(id + ".pending")
                .addAmount("%number%", n)));
        final List<BufferedImage> images = getGUI().getFixedImageLibrary()
            .getButtonImages("execute");
        if (images.size() < 4) return;
        final String[] keys = { BUTTON_IMAGE, BUTTON_ROLLOVER_IMAGE,
                                BUTTON_PRESSED_IMAGE, BUTTON_DISABLED_IMAGE };
        for (int i = 0; i < 4; i++) {
            BufferedImage base = images.get(i);
            putValue(keys[i], new ImageIcon((n <= 0 || i == 3) ? base
                    : withCount(base, n)));
        }
        this.shownImage = getValue(BUTTON_IMAGE);
    }

    /**
     * Draw a count in a red circle at the top right of a button image.
     *
     * @param base The button image.
     * @param n The count.
     * @return A new image.
     */
    private static BufferedImage withCount(BufferedImage base, int n) {
        final int w = base.getWidth(), h = base.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(base, 0, 0, null);
        final String text = (n > 99) ? "99+" : Integer.toString(n);
        final int d = Math.max(12, w * 2 / 5);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, d * 2 / 3)));
        final FontMetrics fm = g.getFontMetrics();
        final int bw = Math.max(d, fm.stringWidth(text) + d / 2);
        final int x = w - bw, y = 0;
        g.setColor(new Color(0xC62828));
        g.fillOval(x, y, bw, d);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1f, w / 40f)));
        g.drawOval(x, y, bw - 1, d - 1);
        g.drawString(text, x + (bw - fm.stringWidth(text)) / 2,
                     y + (d + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
        return bi;
    }


    // Interface ActionListener

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent ae) {
        igc().executeGotoOrders();
    }
}
