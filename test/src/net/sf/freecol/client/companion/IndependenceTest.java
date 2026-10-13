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

package net.sf.freecol.client.companion;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.CombatModel;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.NationType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The war of independence figures on the companion page.
 */
public class IndependenceTest extends FreeColTestCase {

    private static final Specification S = spec();

    /** Our copy of the attack formula agrees with the combat model. */
    public void testAttackMatchesCombatModel() {
        Game game = getGame();
        game.changeMap(getTestMap(S.getTileType("model.tile.plains"), true));
        Colony colony = createStandardColony(2);
        Player player = colony.getOwner();
        Tile tile = game.getMap().getTile(6, 6);
        CombatModel cm = game.getCombatModel();
        String[][] cases = {
            { "model.unit.freeColonist", "model.role.soldier" },
            { "model.unit.freeColonist", "model.role.dragoon" },
            { "model.unit.veteranSoldier", "model.role.soldier" },
            { "model.unit.veteranSoldier", "model.role.dragoon" },
            { "model.unit.artillery", "model.role.default" },
        };
        for (String[] c : cases) {
            UnitType ut = S.getUnitType(c[0]);
            Role role = S.getRole(c[1]);
            Unit u = new ServerUnit(game, tile, player, ut, role);
            double real = cm.getOffencePower(u, null);
            double ours = CompanionIndependence.attack(S, game.getTurn(), ut,
                role, null, 0, false);
            assertEquals(c[0] + "/" + c[1], real, ours, 0.001);
        }
    }

    /** The King's units attacking a colony: bombard bonus and tories. */
    public void testKingsRegularsAgainstAColony() {
        Game game = getGame();
        UnitType regular = S.getUnitType("model.unit.kingsRegular");
        Role infantry = S.getRole("model.role.infantry");
        NationType ref = S.getNationType("model.nationType.ref");
        double plain = CompanionIndependence.attack(S, game.getTurn(),
            regular, infantry, ref, 0, false);
        double colony = CompanionIndependence.attack(S, game.getTurn(),
            regular, infantry, ref, 0, true);
        double tory = CompanionIndependence.attack(S, game.getTurn(),
            regular, infantry, ref, 100, true);
        assertTrue("Stronger than a colonist soldier",
            plain > NationEstimate.soldierPower(S));
        assertTrue("Bombard bonus against a colony", colony > plain);
        assertTrue("A colony of tories helps the King", tory > colony);
    }

    /** Our copy of the defence formula agrees with the combat model. */
    public void testDefenceMatchesCombatModel() {
        Game game = getGame();
        game.changeMap(getTestMap(S.getTileType("model.tile.plains"), true));
        Colony colony = createStandardColony(3);
        Player player = colony.getOwner();
        CombatModel cm = game.getCombatModel();
        String[] walls = { null, "model.building.stockade", "model.building.fort" };
        for (String w : walls) {
            if (w != null) {
                colony.addBuilding(new net.sf.freecol.server.model.ServerBuilding(
                    game, colony, S.getBuildingType(w)));
            }
            for (String[] c : new String[][] {
                    { "model.unit.veteranSoldier", "model.role.soldier" },
                    { "model.unit.freeColonist", "model.role.dragoon" },
                    { "model.unit.artillery", "model.role.default" } }) {
                UnitType ut = S.getUnitType(c[0]);
                Role role = S.getRole(c[1]);
                Unit u = new ServerUnit(game, colony.getTile(), player, ut, role);
                for (boolean fort : new boolean[] { false, true }) {
                    if (fort) {
                        u.setState(Unit.UnitState.FORTIFYING);
                        u.setState(Unit.UnitState.FORTIFIED);
                    } else {
                        u.setState(Unit.UnitState.ACTIVE);
                    }
                    double real = cm.getDefencePower(null, u);
                    double ours = CompanionIndependence.defence(S, game.getTurn(),
                        player, ut, role, colony, fort);
                    assertEquals(w + " " + c[0] + "/" + c[1] + " fortified=" + fort,
                                 real, ours, 0.001);
                }
                u.dispose();
            }
        }
    }

    public void testHoldChance() {
        // Attack 10 against defence 30: the defender holds 3 times in 4.
        assertEquals(0.75, CompanionIndependence.holdChance(10, 30), 1e-9);
        assertEquals(0.0, CompanionIndependence.holdChance(10, 0), 1e-9);
        assertEquals(1.0, CompanionIndependence.holdChance(0, 5), 1e-9);
    }

    public void testMusterLimit() {
        // Nijmegen: 100% rebels, 18 units in and around it -> up to 10.
        assertEquals(10, CompanionIndependence.musterLimit(100, 18));
        // Arnhem: 94%, 8 units -> (10 * 44) / 100 = 4.
        assertEquals(4, CompanionIndependence.musterLimit(94, 8));
        // 50% or less: nobody.
        assertEquals(0, CompanionIndependence.musterLimit(50, 20));
        assertEquals(0, CompanionIndependence.musterLimit(26, 9));
    }
}
