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

package net.sf.freecol.common.diplomacy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.FeatureContainer;
import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.NationType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.UnitType;


/**
 * Attack and defence worked out with the same modifiers as the game's
 * combat model, for units that are not on the map (a soldier we might
 * arm, the King's regulars, the defenders we guess are in a colony).
 * Every fight is a dice roll: the attacker wins with probability
 * attack / (attack + defence).
 */
public final class CombatMath {

    private CombatMath() {}


    /**
     * The attack strength of one unit attacking a colony, worked out as
     * the combat model does for a real unit (full moves left, landing
     * before the attack).
     *
     * @param spec The {@code Specification}.
     * @param turn The current {@code Turn}.
     * @param type The {@code UnitType}.
     * @param role The {@code Role}.
     * @param nationType The attacker's {@code NationType} (for its
     *     bombard bonus), may be null.
     * @param popularSupport The attacker's popular support in percent
     *     (for the King: the colony's tories), 0 for none.
     * @param settlement True if attacking a colony (bombard and popular
     *     support apply), false for a plain measurement.
     * @return The attack strength.
     */
    public static double attack(Specification spec, Turn turn, UnitType type,
                         Role role, NationType nationType, int popularSupport,
                         boolean settlement) {
        Set<Modifier> mods = new HashSet<>();
        mods.add(new Modifier(Modifier.OFFENCE, type.getBaseOffence(),
                              Modifier.ModifierType.ADDITIVE,
                              Specification.BASE_OFFENCE_SOURCE,
                              Modifier.BASE_COMBAT_INDEX));
        mods.addAll(type.getModifiers(Modifier.OFFENCE, type, turn)
            .map(m -> {
                Modifier c = Modifier.makeModifier(m);
                c.setModifierIndex((m.getType() == Modifier.ModifierType.ADDITIVE)
                    ? Modifier.UNIT_ADDITIVE_COMBAT_INDEX
                    : Modifier.UNIT_NORMAL_COMBAT_INDEX);
                return c;
            }).collect(Collectors.toList()));
        if (role != null) {
            mods.addAll(role.getModifiers(Modifier.OFFENCE, type, turn)
                .map(m -> {
                    Modifier c = Modifier.makeModifier(m);
                    c.setModifierIndex(Modifier.ROLE_COMBAT_INDEX);
                    return c;
                }).collect(Collectors.toList()));
        }
        mods.addAll(spec.getModifiers(Modifier.ATTACK_BONUS)
            .collect(Collectors.toList()));
        if (settlement) {
            if (nationType != null) {
                mods.addAll(nationType.getModifiers(Modifier.BOMBARD_BONUS)
                    .collect(Collectors.toList()));
            }
            if (popularSupport > 0) {
                Modifier ps = new Modifier(Modifier.POPULAR_SUPPORT,
                    popularSupport, Modifier.ModifierType.PERCENTAGE);
                ps.setModifierIndex(Modifier.GENERAL_COMBAT_INDEX);
                mods.add(ps);
            }
        }
        return FeatureContainer.applyModifiers(0.0f, turn, mods);
    }

    /**
     * The defence of a unit in one of our colonies, worked out as the
     * combat model does (base defence, unit type, owner, role, the
     * colony's walls, and fortifying unless the walls are strong
     * enough to make it pointless).
     *
     * @param spec The {@code Specification}.
     * @param turn The current {@code Turn}.
     * @param owner The owner {@code Player}.
     * @param type The {@code UnitType}.
     * @param role The {@code Role}.
     * @param colony The {@code Colony} it defends.
     * @param fortified Is the unit fortified?
     * @return The defence strength.
     */
    public static double defence(Specification spec, Turn turn, Player owner,
                          UnitType type, Role role, Colony colony,
                          boolean fortified) {
        Set<Modifier> mods = new HashSet<>();
        mods.add(new Modifier(Modifier.DEFENCE, type.getBaseDefence(),
                              Modifier.ModifierType.ADDITIVE,
                              Specification.BASE_DEFENCE_SOURCE,
                              Modifier.BASE_COMBAT_INDEX));
        mods.addAll(type.getModifiers(Modifier.DEFENCE, type, turn)
            .map(m -> {
                Modifier c = Modifier.makeModifier(m);
                c.setModifierIndex((m.getType() == Modifier.ModifierType.ADDITIVE)
                    ? Modifier.UNIT_ADDITIVE_COMBAT_INDEX
                    : Modifier.UNIT_NORMAL_COMBAT_INDEX);
                return c;
            }).collect(Collectors.toList()));
        mods.addAll(owner.getModifiers(Modifier.DEFENCE, type, turn)
            .map(m -> {
                Modifier c = Modifier.makeModifier(m);
                c.setModifierIndex(Modifier.GENERAL_COMBAT_INDEX);
                return c;
            }).collect(Collectors.toList()));
        if (role != null) {
            mods.addAll(role.getModifiers(Modifier.DEFENCE, type, turn)
                .map(m -> {
                    Modifier c = Modifier.makeModifier(m);
                    c.setModifierIndex(Modifier.ROLE_COMBAT_INDEX);
                    return c;
                }).collect(Collectors.toList()));
        }
        boolean strong = strong(colony.getTile().getType().getDefenceModifiers());
        mods.addAll(colony.getDefenceModifiers());
        final Building stockade = colony.getStockade();
        if (stockade != null) strong |= strong(stockade.getType().getDefenceModifiers());
        if (fortified && !strong) {
            mods.addAll(spec.getModifiers(Modifier.FORTIFIED)
                .collect(Collectors.toList()));
        }
        return FeatureContainer.applyModifiers(0.0f, turn, mods);
    }

    private static boolean strong(List<Modifier> mods) {
        for (Modifier m : mods) {
            if (m.getType() == Modifier.ModifierType.PERCENTAGE
                && m.getValue() >= 150) return true;
        }
        return false;
    }

    /**
     * The chance a defender holds against one attack.
     *
     * @param attack The attack strength.
     * @param defence The defence strength.
     * @return The chance, 0..1.
     */
    public static double holdChance(double attack, double defence) {
        if (defence <= 0) return 0;
        if (attack <= 0) return 1;
        return defence / (attack + defence);
    }

    /**
     * The chance an attacker wins one fight.
     *
     * @param attack The attack strength.
     * @param defence The defence strength.
     * @return The chance, 0..1.
     */
    public static double winChance(double attack, double defence) {
        return 1.0 - holdChance(attack, defence);
    }
}
