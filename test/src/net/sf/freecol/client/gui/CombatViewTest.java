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

package net.sf.freecol.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.client.gui.panel.CombatView;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The pieces of the combat screens (mod).
 */
public class CombatViewTest extends FreeColTestCase {

    public void testOutcome() {
        assertEquals(CombatView.Outcome.DEFEAT,
                     CombatView.outcome("combat.unitDemoted.ours"));
        assertEquals(CombatView.Outcome.VICTORY,
                     CombatView.outcome("combat.unitDemoted.enemy.artillery"));
        assertEquals(CombatView.Outcome.VICTORY,
                     CombatView.outcome("combat.colonyCaptured.enemy"));
        assertEquals(CombatView.Outcome.SKIRMISH,
                     CombatView.outcome("combat.shipEvaded.ours"));
        assertEquals(CombatView.Outcome.DEFEAT,
                     CombatView.outcome("combat.raid.goods"));
        assertEquals(CombatView.Outcome.DEFEAT,
                     CombatView.outcome("combat.unitLoseAutoEquip"));
        // Not one battle: the plain panel.
        assertNull(CombatView.outcome("combat.burnMissions"));
        assertNull(CombatView.outcome("combat.raid.other"));
        assertNull(CombatView.outcome("combat.unitPromoted"));
        assertNull(CombatView.outcome("model.player.soLIncrease"));
        assertTrue(CombatView.isBattle("combat.unitDemoted.ours",
            ModelMessage.MessageType.COMBAT_RESULT));
        assertFalse(CombatView.isBattle("combat.unitDemoted.ours",
            ModelMessage.MessageType.DEFAULT));
    }

    public void testUnitIdsFromLabels() {
        String[] ids = CombatView.unitIds(Messages.getUnitLabel(null,
            "model.unit.indianConvert", 1, null, "model.role.dragoon", null));
        assertEquals("model.unit.indianConvert", ids[0]);
        assertEquals("model.role.dragoon", ids[1]);
        ids = CombatView.unitIds(Messages.getUnitLabel(null,
            "model.unit.veteranSoldier", 1, "model.nation.russian",
            "model.role.dragoon", null));
        assertEquals("model.unit.veteranSoldier", ids[0]);
        assertEquals("model.role.dragoon", ids[1]);
        ids = CombatView.unitIds(Messages.getUnitLabel(null,
            "model.unit.freeColonist", 1, null, "model.role.default", null));
        assertEquals("model.unit.freeColonist", ids[0]);
        assertNull(ids[1]);
        ids = CombatView.unitIds(null);
        assertNull(ids[0]);
    }

    public void testWithoutOpening() {
        assertEquals("Dragoon has been demoted.",
            CombatView.withoutOpening("Defeat at Grassland (south of Kadyak): dragoon has been demoted."));
        assertEquals("Nothing to strip.",
            CombatView.withoutOpening("Nothing to strip."));
    }

    public void testStrengthAddsUp() {
        // The convert dragoon at Kadyak: 3, +25%, +50%, -33% = 3.77.
        final Turn turn = new Turn(1);
        List<Modifier> mods = new ArrayList<>();
        mods.add(new Modifier(Modifier.OFFENCE, 3f, Modifier.ModifierType.ADDITIVE));
        mods.add(new Modifier(Modifier.OFFENCE, 25f, Modifier.ModifierType.PERCENTAGE));
        mods.add(new Modifier(Modifier.OFFENCE, 50f, Modifier.ModifierType.PERCENTAGE));
        mods.add(new Modifier(Modifier.OFFENCE, -33f, Modifier.ModifierType.PERCENTAGE));
        final double total = net.sf.freecol.common.model.FeatureContainer
            .applyModifiers(0f, turn, mods);
        CombatView.Strength s = CombatView.strength(mods, turn, total);
        assertTrue(s.known);
        assertEquals(3.0, s.base, 1e-6);
        assertEquals(total, s.base + s.bonus - s.penalty, 1e-3);
        assertTrue(s.bonus > s.penalty);
        // A hidden defender: unknown base.
        mods.add(net.sf.freecol.common.model.SimpleCombatModel.UNKNOWN_DEFENCE_MODIFIER);
        assertFalse(CombatView.strength(mods, turn, 0).known);
    }

    public void testStakes() {
        Game game = getStandardGame();
        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        Unit dragoon = new ServerUnit(game, null, dutch,
            spec().getUnitType("model.unit.freeColonist"),
            spec().getRole("model.role.dragoon"));
        StringTemplate t = CombatView.stakeIfLose(dragoon);
        assertEquals("combat.stake.lose.demoted", t.getId());
        String s = Messages.message(t);
        assertTrue(s, s.contains("Soldier"));
        Unit soldier = new ServerUnit(game, null, dutch,
            spec().getUnitType("model.unit.freeColonist"),
            spec().getRole("model.role.soldier"));
        assertEquals("combat.stake.lose.disarmed",
                     CombatView.stakeIfLose(soldier).getId());
        Unit artillery = new ServerUnit(game, null, dutch,
            spec().getUnitType("model.unit.artillery"),
            spec().getDefaultRole());
        assertEquals("combat.stake.win.damaged",
                     CombatView.stakeIfWin(artillery).getId());
        Unit ship = new ServerUnit(game, null, dutch,
            spec().getUnitType("model.unit.frigate"), spec().getDefaultRole());
        assertEquals("combat.stake.lose.ship",
                     CombatView.stakeIfLose(ship).getId());
    }
}
