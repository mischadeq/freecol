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

package net.sf.freecol.client.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.client.control.SupplyForecast.Outlook;
import net.sf.freecol.client.control.SupplyForecast.Status;
import net.sf.freecol.client.control.SupplyForecast.Target;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.ModelMessage.MessageType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;


/**
 * Watches the player's supply lines from turn to turn and produces a
 * message when one of them stops covering a colony's needs: the last
 * carrier was taken off the route, nothing is coming any more, or the
 * next delivery will be too late (saying so when a carrier was held
 * up).  A carrier that is held up but still arrives in time gives no
 * message.
 *
 * A message is produced when the situation changes, not on every
 * turn that it persists.  What was expected last turn is remembered
 * here, in memory only, so the first turn after loading a game starts
 * with a clean slate.
 */
public final class SupplyWatch {

    /** What was known about one supply line last time we looked. */
    private static final class Memory {
        final Status status;
        /** The turn number the delivery was expected, or -1. */
        final int arrival;
        /** The id of the carrier expected to deliver, or null. */
        final String unitId;

        Memory(Status status, int arrival, String unitId) {
            this.status = status;
            this.arrival = arrival;
            this.unitId = unitId;
        }
    }

    private final Map<String, Memory> memory = new HashMap<>();

    private int lastTurn = -1;


    /**
     * How urgent is a colony's need for some goods?
     *
     * @param colony The {@code Colony} to examine.
     * @param goodsType The {@code GoodsType} to examine.
     * @return The number of turns until the colony runs out, -1 if
     *     it needs the goods for its current build but is not
     *     running out, or null if it has no need at all.
     */
    public static Integer getTurnsLeft(Colony colony, GoodsType goodsType) {
        final int p = colony.getAdjustedNetProductionOf(goodsType);
        if (p < 0) return colony.getGoodsCount(goodsType) / -p;

        final BuildableType build = colony.getCurrentlyBuilding();
        if (build != null) {
            AbstractGoods needed = new AbstractGoods();
            if (colony.getTurnsToComplete(build, needed) < 0
                && needed.getType() == goodsType) return -1;
        }
        return null;
    }

    /**
     * Check the supply lines at the start of a turn.
     *
     * @param player The {@code Player} to check for.
     * @param turn The current turn number.
     * @return A list of new {@code ModelMessage}s, possibly empty.
     */
    public List<ModelMessage> check(Player player, int turn) {
        List<ModelMessage> result = new ArrayList<>();
        if (turn < this.lastTurn) this.memory.clear(); // A different game
        this.lastTurn = turn;

        final SupplyForecast forecast = new SupplyForecast(player);
        final Map<String, Memory> now = new HashMap<>();
        for (Target target : forecast.getTargets()) {
            final Colony colony = target.colony;
            final GoodsType gt = target.goodsType;
            if (colony.isDisposed() || !player.owns(colony)) continue;
            final Integer turnsLeft = getTurnsLeft(colony, gt);
            if (turnsLeft == null) continue; // No need, nothing to watch.

            final String k = colony.getId() + "/" + gt.getId();
            final Outlook o = forecast.getOutlook(colony, gt, turnsLeft);
            final Memory before = this.memory.get(k);
            final int arrival = (o.delivery == null) ? -1
                : turn + o.delivery.turns;
            final String unitId = (o.delivery == null) ? null
                : o.delivery.unit.getId();
            now.put(k, new Memory(o.status, arrival, unitId));

            // The same carrier, still on its way, now expected later?
            final boolean slipped = o.delivery != null && before != null
                && before.unitId != null && before.unitId.equals(unitId)
                && before.arrival >= turn && arrival > before.arrival;

            // Delays only matter when they make the delivery late: a
            // carrier that slips but still arrives before the colony
            // runs out is not worth a message.
            ModelMessage m = null;
            if (o.isBroken() && (before == null || before.status != o.status)) {
                switch (o.status) {
                case NO_CARRIER:
                    m = message("supply.noCarrier", colony, gt)
                        .addName("%route%", o.route.getName());
                    break;
                case NOTHING_COMING:
                    m = message("supply.nothingComing", colony, gt)
                        .addName("%route%", o.route.getName());
                    break;
                case LATE:
                    // Late because the carrier was held up: say so.
                    m = message((slipped) ? "supply.delayed" : "supply.late",
                                colony, gt)
                        .addName("%route%", o.route.getName())
                        .addStringTemplate("%unit%", o.delivery.unit
                            .getLabel(Unit.UnitLabelType.NATIONAL))
                        .addAmount("%turns%", turnsLeft)
                        .addAmount("%arrival%", o.delivery.turns);
                    if (slipped) m.addAmount("%delay%", arrival - before.arrival);
                    break;
                default:
                    break;
                }
            }
            if (m != null) result.add(m);
        }
        this.memory.clear();
        this.memory.putAll(now);
        return result;
    }

    private static ModelMessage message(String id, Colony colony,
                                        GoodsType goodsType) {
        return new ModelMessage(MessageType.MISSING_GOODS, id, colony)
            .addName("%colony%", colony.getName())
            .addNamed("%goods%", goodsType)
            .addAmount("%stock%", colony.getGoodsCount(goodsType));
    }
}
