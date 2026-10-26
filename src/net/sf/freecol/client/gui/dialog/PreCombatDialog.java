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
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.DefenderSightings;
import net.sf.freecol.client.gui.*;
import net.sf.freecol.client.gui.label.UnitLabel;
import net.sf.freecol.client.gui.panel.*;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.CombatModel;
import net.sf.freecol.common.model.FeatureContainer;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import static net.sf.freecol.common.util.CollectionUtils.*;


/**
 * The dialog that is shown prior to a possible combat.
 *
 * Mod: drawn as a duel.  Both sides' strength as bars (base, what the
 * bonuses add, what the penalties take), the chance to win in the
 * middle when the defender is in sight, what each side's bonuses are,
 * the defender last seen in a settlement whose defender is hidden, and
 * what is at stake either way.
 */
public class PreCombatDialog extends FreeColConfirmDialog {

    /**
     * Create a new pre-combat dialog.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param frame The owner frame.
     * @param attacker The attacker {@code Unit}.
     * @param defender The defender (either a {@code Unit} or
     *     a {@code Settlement}).
     */
    public PreCombatDialog(FreeColClient freeColClient, JFrame frame,
            Unit attacker, FreeColGameObject defender) {
        super(freeColClient, frame);

        final ImageLibrary lib = getImageLibrary();
        final Game game = attacker.getGame();
        final CombatModel combatModel = game.getCombatModel();
        final Turn turn = game.getTurn();
        final Set<Modifier> attackModifiers
            = combatModel.getOffensiveModifiers(attacker, defender);
        final Set<Modifier> defenceModifiers
            = combatModel.getDefensiveModifiers(attacker, defender);
        final List<Modifier> offence
            = sort(attackModifiers, Modifier.ascendingModifierIndexComparator);
        final List<Modifier> defence
            = sort(defenceModifiers, Modifier.ascendingModifierIndexComparator);
        final float scale = lib.getScaleFactor();
        final int width = (int)(560 * scale);

        final Font nameFont = FontLibrary.getScaledFont("normal-bold-smaller");
        final Font plainFont = FontLibrary.getScaledFont("normal-plain-smaller");
        final Font bigFont = FontLibrary.getScaledFont("header-plain-big");

        // left hand side: attacker
        // right hand side: defender
        final String attackerName
            = attacker.getDescription(Unit.UnitLabelType.NATIONAL);
        final JLabel attackerLabel = new UnitLabel(freeColClient, attacker,
                                                   false, true);
        String defenderName;
        JLabel defenderLabel;
        Unit defenderUnit = null;
        Settlement settlement = null;
        if (combatModel.combatIsAttack(attacker, defender)) {
            defenderUnit = (Unit)defender;
            defenderName
                = defenderUnit.getDescription(Unit.UnitLabelType.NATIONAL);
            defenderLabel = new UnitLabel(freeColClient, defenderUnit,
                                          false, true);
        } else if (combatModel.combatIsSettlementAttack(attacker, defender)) {
            settlement = (Settlement)defender;
            defenderName = settlement.getName();
            defenderLabel = new JLabel(new ImageIcon(
                lib.getScaledSettlementImage(settlement)));
        } else {
            throw new RuntimeException("Bogus attack: " + attacker
                + " v " + defender);
        }

        final float offenceResult
            = FeatureContainer.applyModifiers(0f, turn, attackModifiers);
        final float defenceResult
            = FeatureContainer.applyModifiers(0f, turn, defenceModifiers);
        final CombatView.Strength ours
            = CombatView.strength(attackModifiers, turn, offenceResult);
        final CombatView.Strength theirs
            = CombatView.strength(defenceModifiers, turn, defenceResult);

        JPanel panel = new MigPanel(new MigLayout("wrap 3, insets 4",
                "[45%, center][10%, center][45%, center]", ""));
        panel.setOpaque(false);
        panel.add(label(attackerName, nameFont, null));
        panel.add(label(Messages.message("combat.vs"), plainFont, null));
        panel.add(label(defenderName, nameFont, null));

        // The duel: both pictures, the chance between them.
        panel.add(attackerLabel);
        JPanel chance = new MigPanel(new MigLayout("wrap 1, insets 0", "[center]"));
        chance.setOpaque(false);
        double odds = (defenderUnit == null) ? -1
            : combatModel.calculateCombatOdds(attacker, defenderUnit).win;
        if (odds < 0 || Double.isNaN(odds)) {
            chance.add(label("?", bigFont, CombatView.chanceColor(-1)));
        } else {
            chance.add(label(Math.round(100 * odds) + "%", bigFont,
                             CombatView.chanceColor(odds)));
        }
        chance.add(label(Messages.message("combat.chance"), plainFont, null));
        panel.add(chance);
        panel.add(defenderLabel);

        panel.add(new CombatView.StrengthBars(ours,
                Messages.message("combat.yours"), theirs,
                Messages.message("combat.theirs"), plainFont, width),
            "span 3, growx, gaptop 6");

        // What each side's strength is made of.
        panel.add(modifiers(offence, turn, plainFont), "top, growx");
        panel.add(new JLabel(""));
        panel.add(modifiers(defence, turn, plainFont), "top, growx");

        // A settlement hides its defender: who fought for it last.
        if (settlement != null && !theirs.known) {
            final DefenderSightings.Seen seen = freeColClient
                .getInGameController().getDefenderSightings().last(settlement);
            final StringTemplate t = (seen == null)
                ? StringTemplate.template("combat.notSeen")
                : StringTemplate.template("combat.lastSeen")
                    .addName("%unit%", seen.label)
                    .addAmount("%turns%", Math.max(0, turn.getNumber() - seen.turn));
            panel.add(label(Messages.message(t), plainFont, CombatView.INK_MUTED),
                      "span 3, gaptop 6");
        }

        // What is at stake.
        panel.add(label(Messages.message(CombatView.stakeIfLose(attacker)),
                        plainFont, CombatView.LOSS), "span 3, gaptop 6");
        panel.add(label(Messages.message((defenderUnit != null)
                    ? CombatView.stakeIfWin(defenderUnit)
                    : StringTemplate.template((settlement instanceof IndianSettlement)
                        ? "combat.stake.win.camp" : "combat.stake.win.colony")),
                        plainFont, CombatView.WIN), "span 3");
        panel.setSize(panel.getPreferredSize());

        initializeConfirmDialog(frame, true, panel, null, "ok", "cancel");
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(font);
        if (color != null) l.setForeground(color);
        return l;
    }

    /** A side's bonuses and penalties, one per line: name, value. */
    private static JPanel modifiers(List<Modifier> mods, Turn turn, Font font) {
        JPanel p = new MigPanel(new MigLayout("wrap 2, insets 0",
                                              "[left]12[right]"));
        p.setOpaque(false);
        for (Modifier m : mods) {
            if (Float.compare(m.getValue(turn), Modifier.UNKNOWN) == 0) {
                p.add(label(Messages.message("combat.defenderHidden"), font,
                            CombatView.INK_MUTED), "span 2");
                continue;
            }
            JLabel[] l = ModifierFormat.getModifierLabels(m, null, turn);
            JLabel name = l[0];
            name.setFont(font);
            String v = l[1].getText() + ((l[2] == null) ? "" : l[2].getText());
            JLabel value = label(v, font, (m.getType() == Modifier.ModifierType.PERCENTAGE)
                ? ((m.getValue(turn) < 0) ? CombatView.LOSS : CombatView.WIN)
                : null);
            p.add(name);
            p.add(value);
        }
        return p;
    }
}
