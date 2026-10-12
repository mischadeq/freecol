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

package net.sf.freecol.client.gui.dialog;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.panel.FreeColButton;
import net.sf.freecol.client.gui.panel.FreeColButton.ButtonStyle;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;


/**
 * A dialog that allows a choice of goods amount.
 */
public final class SelectGoodsAmountDialog {

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(SelectGoodsAmountDialog.class.getName());

    /** The default amounts to try. */
    private static final int[] amounts = { 20, 40, 50, 60, 80, 100 };

    
    /**
     * Creates a goods amount dialog.
     *
     * @param freeColClient The enclosing {@code FreeColClient}.
     * @param goodsType The {@code GoodsType} to select an amount of.
     * @param available The amount of goods available.
     * @param defaultAmount The amount to select to start with.
     * @param pay If true, check the player has sufficient funds.
     */
    public static FreeColDialog<Integer> create(FreeColClient freeColClient, GoodsType goodsType, int availableAmount,
            int defaultAmount, boolean pay) {
        return create(freeColClient, goodsType, availableAmount,
                      defaultAmount, pay, false);
    }

    /**
     * Creates a goods amount dialog that, when trading with the market
     * in Europe, shows what the chosen amount costs or earns, the tax,
     * and the gold left afterwards.
     *
     * @param freeColClient The enclosing {@code FreeColClient}.
     * @param goodsType The {@code GoodsType} to select an amount of.
     * @param availableAmount The amount of goods available.
     * @param defaultAmount The amount to select to start with.
     * @param pay If true, a purchase: check the player has the funds.
     * @param sale If true, a sale to the market.
     * @return The dialog.
     */
    public static FreeColDialog<Integer> create(FreeColClient freeColClient, GoodsType goodsType, int availableAmount,
            int defaultAmount, boolean pay, boolean sale) {
        final FreeColDialog<Integer> dialog = new FreeColDialog<>(api -> {
            final JPanel content = new JPanel(new MigLayout("fill, wrap 1", "", ""));
            
            final int available;
            if (pay) {
                final Player player = freeColClient.getMyPlayer();
                final int gold = player.getGold();
                final int price = player.getMarket().getCostToBuy(goodsType);
                available = Math.min(availableAmount, gold/price);
            } else {
                available = availableAmount;
            }

            final JTextArea question = Utility.localizedTextArea("selectAmountDialog.text");
            content.add(question);
            
            if (goodsType != null) {
                final ImageIcon icon = new ImageIcon(freeColClient.getGUI().getFixedImageLibrary().getScaledGoodsTypeImage(goodsType));
                content.add(new JLabel(icon), "split 2");
            }

            List<Integer> values = new ArrayList<>();
            int defaultIndex = -1;
            for (int index = 0; index < amounts.length; index++) {
                if (amounts[index] < available) {
                    if (amounts[index] == defaultAmount) defaultIndex = index;
                    values.add(amounts[index]);
                } else {
                    if (available == defaultAmount) defaultIndex = index;
                    values.add(available);
                    break;
                }
            }
            if (defaultAmount > 0 && defaultIndex < 0) {
                for (int index = 0; index < values.size(); index++) {
                    if (defaultAmount < values.get(index)) {
                        values.add(index, defaultAmount);
                        defaultIndex = index;
                        break;
                    }
                }
            }
            
            final JComboBox<Integer> comboBox = new JComboBox<>(values.toArray(new Integer[0]));
            comboBox.setEditable(true);
            if (defaultIndex >= 0) {
                comboBox.setSelectedIndex(defaultIndex);
            }
            comboBox.setInputVerifier(new InputVerifier() {
                @Override @SuppressWarnings("unchecked")
                public boolean verify(JComponent input) {
                    return verifyWholeBox((JComboBox<Integer>)input, available);
                }
            });
            comboBox.getActionMap().put("enterPressed", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    api.setValue(determineValue(comboBox));
                }
            });
            content.add(comboBox, "wrap, growx");

            // The price of the chosen amount, kept up to date.
            if ((pay || sale) && goodsType != null) {
                final JLabel priceLabel = new JLabel(" ");
                content.add(priceLabel, "span, growx");
                final Runnable update = () -> priceLabel.setText(
                    priceText(freeColClient, goodsType, readValue(comboBox),
                              pay));
                comboBox.addActionListener(ae -> update.run());
                final java.awt.Component editor
                    = comboBox.getEditor().getEditorComponent();
                if (editor instanceof javax.swing.text.JTextComponent) {
                    ((javax.swing.text.JTextComponent)editor).getDocument()
                        .addDocumentListener(new javax.swing.event.DocumentListener() {
                            @Override
                            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                                javax.swing.SwingUtilities.invokeLater(update);
                            }
                            @Override
                            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                                javax.swing.SwingUtilities.invokeLater(update);
                            }
                            @Override
                            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
                        });
                }
                update.run();
            }

            final JButton okButton = new FreeColButton(Messages.message("ok")).withButtonStyle(ButtonStyle.IMPORTANT);
            okButton.addActionListener(ae -> {
                api.setValue(determineValue(comboBox));
            });
            content.add(okButton, "newline unrel, span, split 2, tag ok");
            
            final JButton cancelButton = new FreeColButton(Messages.message("cancel"));
            cancelButton.addActionListener(ae -> {
                api.setValue(null);
            });
            content.add(cancelButton, "tag cancel");
            
            api.setInitialFocusComponent(comboBox);
            return content;
        });
        return dialog;
    }


    /**
     * Read the amount currently typed or selected.
     *
     * @param comboBox The amount {@code JComboBox}.
     * @return The amount, or -1 if it is not a number.
     */
    private static int readValue(JComboBox<Integer> comboBox) {
        Object o = comboBox.getEditor().getItem();
        if (o == null) o = comboBox.getSelectedItem();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException nfe) {
            return -1;
        }
    }

    /**
     * Describe the price of an amount of goods in the market in Europe,
     * as the server will charge or pay it: purchases are not taxed,
     * sales are taxed at the current rate.
     *
     * @param freeColClient The {@code FreeColClient}.
     * @param goodsType The {@code GoodsType} traded.
     * @param amount The amount.
     * @param buy True for a purchase, false for a sale.
     * @return An HTML description.
     */
    private static String priceText(FreeColClient freeColClient,
                                    GoodsType goodsType, int amount,
                                    boolean buy) {
        final Player player = freeColClient.getMyPlayer();
        final net.sf.freecol.common.model.Market market
            = (player == null) ? null : player.getMarket();
        if (market == null || amount <= 0) return " ";
        final int gold = player.getGold();
        if (!player.canTrade(goodsType)) {
            return "<html><font color='#b00000'>"
                + Messages.message("selectAmountDialog.boycott")
                + "</font></html>";
        }
        net.sf.freecol.common.model.StringTemplate t;
        if (buy) {
            final int unit = market.getCostToBuy(goodsType);
            final int cost = market.getBidPrice(goodsType, amount);
            t = net.sf.freecol.common.model.StringTemplate
                .template("selectAmountDialog.buy")
                .addAmount("%amount%", amount)
                .addAmount("%price%", unit)
                .addAmount("%total%", cost)
                .addAmount("%gold%", gold - cost);
            final String s = Messages.message(t);
            return "<html>" + ((cost > gold) ? "<font color='#b00000'>"
                + s + "</font>" : s) + "</html>";
        }
        final int tax = player.getTax();
        final int unit = market.getPaidForSale(goodsType);
        final int before = market.getSalePrice(goodsType, amount);
        final int after = ((100 - tax) * before) / 100;
        t = net.sf.freecol.common.model.StringTemplate
            .template("selectAmountDialog.sell")
            .addAmount("%amount%", amount)
            .addAmount("%price%", unit)
            .addAmount("%total%", before)
            .addName("%tax%", tax + "%")
            .addAmount("%taxAmount%", before - after)
            .addAmount("%net%", after)
            .addAmount("%gold%", gold + after);
        return "<html>" + Messages.message(t) + "</html>";
    }

    private static boolean verifyWholeBox(JComboBox<Integer> box, int available) {
        final int n = box.getItemCount();
        for (int i = 0; i < n; i++) {
            Integer v = box.getItemAt(i);
            if (v < 0 || v > available) return false;
        }
        return true;
    }

    private static int determineValue(JComboBox<Integer> comboBox) {
        Object value = comboBox.getSelectedItem();
        return (value instanceof Integer) ? (Integer)value : -1;
    }
}
