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

package net.sf.freecol.tools.replay;

import net.sf.freecol.common.diplomacy.Action;


/**
 * Something a nation was seen to do (or suffer) between two saves.
 */
public final class Observed {

    /** What happened. */
    public enum What {
        WAR, PEACE, CAPTURE, DESTROY, COLONY_LOST, UNITS_LOST,
        SHIPS_DAMAGED, ARM, ARM_TRIBE, MISSION, MISSION_GONE, SOLD_IN_EUROPE
    }

    public final What what;
    /** Who did it (nation id), or who it happened to. */
    public final String actor;
    /** The other nation, or null. */
    public final String other;
    /** Forced on the actor (the King), not its own choice. */
    public final boolean forced;
    /** Count, gold or soldiers, depending on what. */
    public final int amount;
    /** Extra words (a colony name, how the declarer was found). */
    public final String detail;


    public Observed(What what, String actor, String other, boolean forced,
                    int amount, String detail) {
        this.what = what;
        this.actor = actor;
        this.other = other;
        this.forced = forced;
        this.amount = amount;
        this.detail = (detail == null) ? "" : detail;
    }

    /**
     * The same thing as a diplomat move, when it is one the nation chose.
     *
     * @return The {@code Action}, or null.
     */
    public Action asAction() {
        if (forced) return null;
        switch (what) {
        case WAR: return Action.war(other);
        case PEACE: return Action.peace(other);
        case CAPTURE: case DESTROY: return Action.attack(other, null);
        case ARM: return Action.arm(amount);
        case ARM_TRIBE: return Action.armTribe(other, null, null, 0, 0);
        default: return null;
        }
    }

    public String label() {
        StringBuilder sb = new StringBuilder(what.name().toLowerCase());
        if (other != null) sb.append(' ').append(Action.shortId(other));
        if (amount != 0) sb.append(' ').append(amount);
        if (!detail.isEmpty()) sb.append(" (").append(detail).append(')');
        if (forced) sb.append(" [forced]");
        return sb.toString();
    }

    @Override
    public String toString() {
        return Action.shortId(actor) + ": " + label();
    }
}
