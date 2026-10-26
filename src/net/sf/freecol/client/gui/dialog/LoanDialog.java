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

import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.panel.FreeColButton;
import net.sf.freecol.client.gui.panel.FreeColButton.ButtonStyle;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.diplomacy.Loan;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.UnitType;


/**
 * A computer nation asks the player for a ship loan (mod): what it
 * needs, its gold, fleet and credit, and two sliders for the rent and
 * the term, with the cost shown as they move.  The answer is
 * rent * 100 + term, or null to decline.
 */
public final class LoanDialog {

    private LoanDialog() {}

    /**
     * The fields of the borrower's info line ("gold=400 ships=0 ...").
     *
     * @param info The line.
     * @return The fields.
     */
    public static Map<String, String> info(String info) {
        final Map<String, String> ret = new HashMap<>();
        if (info == null) return ret;
        for (String f : info.split(" ")) {
            final int i = f.indexOf('=');
            if (i > 0) ret.put(f.substring(0, i), f.substring(i + 1));
        }
        return ret;
    }

    private static int num(Map<String, String> m, String key) {
        try {
            return Integer.parseInt(m.getOrDefault(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The colour of a credit standing chip. */
    public static Color standingColor(String standing) {
        switch (standing) {
        case "GOOD": return new Color(0x2e7d32);
        case "WARY": return new Color(0xef6c00);
        case "BAD": return new Color(0xc62828);
        default: return new Color(0x616161);
        }
    }

    /** A coloured chip. */
    private static JLabel chip(String text, Color c) {
        final JLabel l = new JLabel(" " + text + " ");
        l.setOpaque(true);
        l.setBackground(c);
        l.setForeground(Color.WHITE);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return l;
    }

    /**
     * The borrower's record in short.
     *
     * @param m The info fields.
     * @return The text, e.g. "2 repaid, 1 default 14 turns ago, 1 ship
     *     seized", or "no loans yet".
     */
    public static String record(Map<String, String> m) {
        final StringTemplate t = StringTemplate.template("loan.dialog.record")
            .addAmount("%repaid%", num(m, "repaid") + num(m, "early"))
            .addAmount("%defaults%", num(m, "defaults"))
            .addAmount("%ago%", Math.max(0, num(m, "last")))
            .addAmount("%seized%", num(m, "seized"));
        if (num(m, "repaid") + num(m, "early") + num(m, "defaults")
            + num(m, "seized") == 0) {
            return Messages.message("loan.dialog.noRecord");
        }
        return Messages.message(t);
    }

    /**
     * Create the dialog.
     *
     * @param freeColClient The enclosing {@code FreeColClient}.
     * @param o The LOAN {@code Offer}.
     * @return The dialog.
     */
    public static FreeColDialog<Integer> create(FreeColClient freeColClient,
                                                Offer o) {
        return new FreeColDialog<>(api -> {
            final Player from = freeColClient.getGame()
                .getPlayerByNationId(o.from);
            final UnitType ut = freeColClient.getGame().getSpecification()
                .getUnitType(o.goods);
            final Map<String, String> m = info(o.placeName);
            final JPanel content = new JPanel(new MigLayout("wrap 2, fillx",
                                                            "[][grow]", ""));
            content.add(Utility.localizedTextArea(StringTemplate
                .template("loan.dialog.ask")
                .addStringTemplate("%nation%", from.getNationLabel())
                .addAmount("%gold%", o.amount)
                .addName("%ship%", (ut == null) ? o.goods
                         : Messages.getName(ut))
                .addAmount("%price%", o.gold)), "span, growx");
            final String st = m.getOrDefault("standing", "NEW");
            content.add(new JLabel(Messages.message("loan.dialog.credit")));
            final JPanel chips = new JPanel(new MigLayout("insets 0", "", ""));
            chips.setOpaque(false);
            chips.add(chip(Messages.message("loan.standing."
                                            + st.toLowerCase()),
                           standingColor(st)));
            chips.add(new JLabel(record(m)));
            content.add(chips, "growx");
            content.add(new JLabel(Messages.message("loan.dialog.theirs")));
            content.add(new JLabel(Messages.message(StringTemplate
                .template("loan.dialog.gold")
                .addAmount("%gold%", num(m, "gold"))
                .addAmount("%ships%", num(m, "ships")))));
            if (num(m, "desperate") > 0) {
                content.add(chip(Messages.message("loan.dialog.desperate"),
                                 new Color(0x6a1b9a)), "span");
            }

            final JSlider rent = new JSlider(Loan.MIN_RENT, Loan.MAX_RENT,
                Loan.clampRent(o.delivered));
            rent.setMajorTickSpacing(10);
            rent.setMinorTickSpacing(5);
            rent.setSnapToTicks(true);
            rent.setPaintTicks(true);
            final java.util.Hashtable<Integer, JLabel> labels
                = new java.util.Hashtable<>();
            for (int r = Loan.MIN_RENT; r <= Loan.MAX_RENT; r += 10) {
                labels.put(r, new JLabel(r / 10 + "%"));
            }
            rent.setLabelTable(labels);
            rent.setPaintLabels(true);
            final JSlider term = new JSlider(Loan.MIN_TERM, Loan.MAX_TERM,
                Loan.clampTerm(o.deadline));
            term.setMajorTickSpacing(5);
            term.setPaintTicks(true);
            term.setPaintLabels(true);
            final JLabel rentValue = new JLabel();
            final JLabel termValue = new JLabel();
            content.add(new JLabel(Messages.message("loan.dialog.rent")));
            content.add(rentValue, "split 2");
            content.add(rent, "growx");
            content.add(new JLabel(Messages.message("loan.dialog.term")));
            content.add(termValue, "split 2");
            content.add(term, "growx");
            final JLabel cost = new JLabel(" ");
            cost.setFont(cost.getFont().deriveFont(Font.BOLD));
            content.add(cost, "span, growx");
            final Runnable update = () -> {
                final int r = rent.getValue(), t = term.getValue();
                rentValue.setText(r / 10 + "." + r % 10 + "%");
                termValue.setText(String.valueOf(t));
                final int total = Loan.totalRent(o.amount, r, t);
                cost.setText(Messages.message(StringTemplate
                    .template("loan.dialog.cost")
                    .addAmount("%first%", Loan.firstInstallment(o.amount, r, t))
                    .addAmount("%total%", total)
                    .addAmount("%share%", (o.amount <= 0) ? 0
                               : Math.round(100f * total / o.amount))
                    .addAmount("%all%", o.amount + total)));
            };
            rent.addChangeListener(e -> update.run());
            term.addChangeListener(e -> update.run());
            update.run();
            content.add(Utility.localizedTextArea("loan.dialog.note"),
                        "span, growx");

            final JButton lend = new FreeColButton(Messages.message(
                "loan.dialog.lend")).withButtonStyle(ButtonStyle.IMPORTANT);
            lend.addActionListener(ae ->
                api.setValue(rent.getValue() * 100 + term.getValue()));
            content.add(lend, "newline unrel, span, split 2, tag ok");
            final JButton no = new FreeColButton(Messages.message(
                "offer.decline"));
            no.addActionListener(ae -> api.setValue(null));
            content.add(no, "tag cancel");
            api.setInitialFocusComponent(lend);
            return content;
        });
    }
}
