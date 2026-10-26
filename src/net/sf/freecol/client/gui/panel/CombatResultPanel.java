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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * The battles of the turn, one card each (mod): the two units clash, a
 * big "Victory" or "Defeat", where it was, and what changed in one
 * short line ("Dragoon became Soldier").  On the same parchment, with
 * the same advisor on top, as the game's information panel.
 */
public final class CombatResultPanel extends FreeColPanel {

    private final BufferedImage skin;


    /**
     * Create the panel.
     *
     * @param freeColClient The {@code FreeColClient}.
     * @param battles The battle messages ({@link CombatView#isBattle}).
     */
    public CombatResultPanel(FreeColClient freeColClient,
                             List<ModelMessage> battles) {
        super(freeColClient, null, new MigLayout());
        final ImageLibrary lib = freeColClient.getGUI().getFixedImageLibrary();
        this.skin = lib.getInformationPanelSkin(freeColClient.getMyPlayer());
        final float scale = lib.getScaleFactor();
        final int top = (int)(lib.getInformationPanelSkinTopInset(
            freeColClient.getMyPlayer()) * scale);
        final int gap = 10;
        getMigLayout().setLayoutConstraints("fill, wrap 1, insets 0 0 0 0");
        getMigLayout().setColumnConstraints(gap + "[grow]" + gap);
        getMigLayout().setRowConstraints(top + "px[grow]" + gap + "[]" + gap);

        final JPanel cards = new MigPanel(new MigLayout("fillx, wrap 1, insets 0"));
        cards.setOpaque(false);
        final int width = this.skin.getWidth() - 4 * gap;
        for (ModelMessage m : battles) {
            cards.add(card(m, lib, width), "growx, gapbottom 14");
        }
        final JScrollPane scroll = new JScrollPane(cards,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setBorder(null);
        add(scroll, "grow");
        add(okButton, "tag ok");
        setPreferredSize(new Dimension(this.skin.getWidth(), this.skin.getHeight()));
        setBorder(null);
        setEscapeAction(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    okButton.doClick();
                }
            });
    }

    /** One battle. */
    private JPanel card(ModelMessage m, ImageLibrary lib, int width) {
        final Game game = getGame();
        final CombatView.Outcome outcome = CombatView.outcome(m.getId());
        final boolean ours = outcome == CombatView.Outcome.DEFEAT
            || m.getId().contains(".ours");
        // Our unit: as it was before the battle if it changed.
        final StringTemplate oldName = m.getReplacement("%oldName%");
        final StringTemplate unit = m.getReplacement("%unit%");
        final StringTemplate enemy = m.getReplacement("%enemyUnit%");
        final StringTemplate mine = (ours && oldName != null) ? oldName : unit;
        final StringTemplate theirs = (!ours && oldName != null) ? oldName : enemy;

        final JPanel p = new MigPanel(new MigLayout("fillx, wrap 1, insets 0",
                                                    "[center]"));
        p.setOpaque(false);
        p.add(new CombatView.Clash(picture(mine, lib), picture(theirs, lib),
                                   outcome, width), "growx");

        final Font big = FontLibrary.getScaledFont("header-plain-big");
        final Font plain = FontLibrary.getScaledFont("normal-plain-smaller");
        final String head = Messages.message("combat.result."
            + outcome.name().toLowerCase(java.util.Locale.ROOT));
        final JLabel h = new JLabel(head);
        h.setFont(big);
        h.setForeground((outcome == CombatView.Outcome.VICTORY) ? CombatView.WIN
            : (outcome == CombatView.Outcome.DEFEAT) ? CombatView.LOSS
            : CombatView.INK);
        p.add(h);
        final StringTemplate where = firstOf(m, "%location%", "%colony%",
                                             "%settlement%");
        final StringTemplate nation = m.getReplacement("%enemyNation%");
        if (where != null) {
            StringTemplate w = StringTemplate.template("combat.result.where")
                .addStringTemplate("%location%", where);
            if (theirs != null) {
                w = StringTemplate.template("combat.result.whereAgainst")
                    .addStringTemplate("%location%", where)
                    .addStringTemplate("%enemy%", (nation == null) ? theirs
                        : StringTemplate.label(" ").addStringTemplate(nation)
                            .addStringTemplate(theirs));
            }
            final JLabel wl = new JLabel(Messages.message(w));
            wl.setFont(plain);
            wl.setForeground(CombatView.INK_MUTED);
            p.add(wl);
        }

        // What changed: "Dragoon (Indian Convert) became Soldier"; else
        // the game's sentence without its "Defeat at ...:" opening.
        String what;
        if (oldName != null && (ours ? unit : enemy) != null) {
            what = Messages.message(StringTemplate.template(ours
                    ? "combat.result.change" : "combat.result.theirChange")
                .addStringTemplate("%from%", oldName)
                .addStringTemplate("%to%", ours ? unit : enemy));
        } else {
            what = CombatView.withoutOpening(Messages.message(m));
        }
        final JTextArea txt = Utility.getDefaultTextArea(what);
        txt.setFont(FontLibrary.getScaledFont("normal-bold-smaller"));
        txt.setForeground(CombatView.INK);
        p.add(txt, "growx, wmax " + width);

        // Show where it happened.
        final FreeColObject fco = game.getMessageSource(m);
        final Tile tile = (fco instanceof Location) ? ((Location)fco).getTile() : null;
        if (tile != null) {
            final JButton show = Utility.localizedButton(StringTemplate
                .template("informationPanel.display")
                .addStringTemplate("%object%", tile.getLocationLabelFor(getMyPlayer())));
            show.addActionListener(ae -> getGUI().displayObject(tile));
            p.add(show);
        }
        return p;
    }

    private static StringTemplate firstOf(ModelMessage m, String... keys) {
        for (String k : keys) {
            final StringTemplate t = m.getReplacement(k);
            if (t != null) return t;
        }
        return null;
    }

    /** The picture of the unit named in a label template, or null. */
    private Image picture(StringTemplate label, ImageLibrary lib) {
        final String[] ids = CombatView.unitIds(label);
        if (ids[0] == null) return null;
        final UnitType type = getSpecification().getUnitType(ids[0]);
        if (type == null) return null;
        try {
            return lib.getScaledUnitTypeImage(type, ids[1], false);
        } catch (RuntimeException re) {
            return lib.getScaledUnitTypeImage(type, null, false);
        }
    }


    // Override JComponent

    @Override
    public void paintComponent(Graphics g) {
        g.drawImage(this.skin, 0, 0, this);
    }
}
