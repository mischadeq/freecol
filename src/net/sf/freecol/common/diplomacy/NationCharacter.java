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

import java.util.EnumSet;
import java.util.Set;


/**
 * What a nation cares about: the weights of the score function and the
 * moves its long-term ambition favours.
 */
public enum NationCharacter {

    //               gold trade land  sec  rel   rho
    TRADING_EMPIRE(  1.0, 1.5, 0.6, 0.8, 1.0, 1.2,
        EnumSet.of(Action.Kind.SELL_GOODS, Action.Kind.BUY_GOODS,
                   Action.Kind.TRADE_AGREEMENT, Action.Kind.MAKE_PEACE,
                   Action.Kind.ARM_TRIBE)),
    CONQUEROR(       0.6, 0.4, 1.5, 0.6, 0.4, 0.6,
        EnumSet.of(Action.Kind.DECLARE_WAR, Action.Kind.ATTACK_COLONY,
                   Action.Kind.ARM)),
    BALANCE_KEEPER(  0.6, 0.8, 0.6, 1.5, 0.8, 1.0,
        EnumSet.of(Action.Kind.ALLIANCE, Action.Kind.INCITE,
                   Action.Kind.ARM_TRIBE)),
    SETTLER(         0.8, 0.8, 1.2, 1.0, 1.0, 1.0,
        EnumSet.of(Action.Kind.MAKE_PEACE, Action.Kind.BUY_GOODS)),
    INDEPENDENCE_FIRST(0.8, 0.6, 0.4, 1.4, 0.8, 1.1,
        EnumSet.of(Action.Kind.ARM, Action.Kind.MAKE_PEACE)),
    /** Native tribes (a first guess; their own step comes later). */
    TRIBE(           0.5, 0.8, 1.0, 1.5, 1.0, 1.2,
        EnumSet.of(Action.Kind.MAKE_PEACE, Action.Kind.SELL_GOODS));

    public final double gold, trade, land, security, relations, rho;

    private final Set<Action.Kind> ambition;

    NationCharacter(double gold, double trade, double land, double security,
              double relations, double rho, Set<Action.Kind> ambition) {
        this.gold = gold;
        this.trade = trade;
        this.land = land;
        this.security = security;
        this.relations = relations;
        this.rho = rho;
        this.ambition = ambition;
    }

    /**
     * Does the long-term ambition favour a kind of move?
     *
     * @param kind The {@code Action.Kind}.
     * @return True if it does.
     */
    public boolean favours(Action.Kind kind) {
        return ambition.contains(kind);
    }

    /**
     * The character of a nation, from its nation type.
     *
     * @param nationTypeId The nation type id, e.g. "model.nationType.trade".
     * @param european True for a European nation, false for a tribe.
     * @return The {@code NationCharacter}.
     */
    public static NationCharacter forNationType(String nationTypeId,
                                          boolean european) {
        if (!european) return TRIBE;
        if (nationTypeId == null) return SETTLER;
        switch (nationTypeId) {
        case "model.nationType.trade":
        case "model.nationType.naval":
        case "model.nationType.furTrapping":
            return TRADING_EMPIRE;
        case "model.nationType.conquest":
            return CONQUEROR;
        case "model.nationType.cooperation":
            return BALANCE_KEEPER;
        case "model.nationType.immigration":
        case "model.nationType.building":
        case "model.nationType.agriculture":
        default:
            return SETTLER;
        }
    }
}
