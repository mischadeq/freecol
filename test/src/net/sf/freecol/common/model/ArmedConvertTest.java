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

package net.sf.freecol.common.model;

import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Indian converts can be armed (mod): with a musket 25% worse than a
 * colonist, on a horse 25% better, unarmed they defend at 2, not 1.
 */
public class ArmedConvertTest extends FreeColTestCase {

    private static final UnitType colonistType
        = spec().getUnitType("model.unit.freeColonist");
    private static final UnitType convertType
        = spec().getUnitType("model.unit.indianConvert");
    private static final Role soldierRole = spec().getRole("model.role.soldier");
    private static final Role dragoonRole = spec().getRole("model.role.dragoon");
    private static final Role scoutRole = spec().getRole("model.role.scout");
    private static final Role pioneerRole = spec().getRole("model.role.pioneer");
    private static final Role missionaryRole
        = spec().getRole("model.role.missionary");

    private Game game;
    private Player dutch, french;
    private Tile here, there;

    private void setUpGame() {
        game = getStandardGame();
        final Map map = getTestMap(spec().getTileType("model.tile.plains"));
        game.changeMap(map);
        dutch = game.getPlayerByNationId("model.nation.dutch");
        french = game.getPlayerByNationId("model.nation.french");
        here = map.getTile(5, 8);
        there = map.getTile(4, 8);
        for (Tile t : new Tile[] { here, there }) {
            t.setExplored(dutch, true);
            t.setExplored(french, true);
        }
    }

    /** The defence of a unit against the same attacker. */
    private double defence(UnitType type, Role role) {
        final Unit attacker = new ServerUnit(game, there, french, colonistType,
                                             soldierRole);
        final Unit u = (role == null) ? new ServerUnit(game, here, dutch, type)
            : new ServerUnit(game, here, dutch, type, role);
        final double d = game.getCombatModel().getDefencePower(attacker, u);
        u.dispose();
        attacker.dispose();
        return d;
    }

    /** The offence of a unit against the same defender. */
    private double offence(UnitType type, Role role) {
        final Unit defender = new ServerUnit(game, there, french, colonistType,
                                             soldierRole);
        final Unit u = (role == null) ? new ServerUnit(game, here, dutch, type)
            : new ServerUnit(game, here, dutch, type, role);
        u.setMovesLeft(u.getInitialMovesLeft());
        final double d = game.getCombatModel().getOffencePower(u, defender);
        u.dispose();
        defender.dispose();
        return d;
    }

    public void testConvertsTakeEquipment() {
        setUpGame();
        assertTrue(convertType.hasAbility(Ability.CAN_BE_EQUIPPED));
        final Unit u = new ServerUnit(game, here, dutch, convertType);
        for (Role r : new Role[] { soldierRole, dragoonRole, scoutRole,
                                   pioneerRole }) {
            assertTrue(r.getId(), u.roleIsAvailable(r));
        }
        assertFalse("no missionaries", u.roleIsAvailable(missionaryRole));
        // The bare type is not a soldier: the AI does not take unarmed
        // converts for defenders.
        assertFalse(convertType.isDefensive());
        assertFalse(convertType.isOffensive());
    }

    public void testStrength() {
        setUpGame();
        final double eps = 0.0001;
        // Unarmed: defends at 2, a colonist at 1.
        assertEquals(2.0, defence(convertType, null)
                     / defence(colonistType, null), eps);
        // A musket: 25% worse than a colonist.
        assertEquals(0.75, defence(convertType, soldierRole)
                     / defence(colonistType, soldierRole), eps);
        assertEquals(0.75, offence(convertType, soldierRole)
                     / offence(colonistType, soldierRole), eps);
        // A horse too: 25% better.
        assertEquals(1.25, defence(convertType, dragoonRole)
                     / defence(colonistType, dragoonRole), eps);
        assertEquals(1.25, offence(convertType, dragoonRole)
                     / offence(colonistType, dragoonRole), eps);
        // Scouts and pioneers keep the better defence.
        assertEquals(2.0, defence(convertType, pioneerRole)
                     / defence(colonistType, pioneerRole), eps);
    }
}
