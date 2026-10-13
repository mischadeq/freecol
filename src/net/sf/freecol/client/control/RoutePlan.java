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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.TradeRouteStop;


/**
 * What a trade route really does at each stop, for the trade route
 * editor.
 *
 * FreeCol stores, per stop, the cargo a carrier leaves with.  At a
 * stop the carrier unloads everything it carries that is not in that
 * list, and loads (tops up) everything that is.  So what is unloaded
 * where is never written down; it follows from the stop before.  This
 * class works that out, and lists the mistakes that are easy to make.
 */
public final class RoutePlan {

    private RoutePlan() {}

    /**
     * The goods loaded at a stop, each once, with the number of holds.
     *
     * @param stop The {@code TradeRouteStop}.
     * @return Goods type to holds, in the order first listed.
     */
    public static Map<GoodsType, Integer> holds(TradeRouteStop stop) {
        Map<GoodsType, Integer> ret = new LinkedHashMap<>();
        for (GoodsType gt : stop.getCargo()) ret.merge(gt, 1, Integer::sum);
        return ret;
    }

    /**
     * The goods unloaded at a stop: carried in from the stop before
     * and not kept aboard here.
     *
     * @param stops The stops of the route.
     * @param i The index of the stop.
     * @return The goods types unloaded, each once.
     */
    public static List<GoodsType> unloads(List<TradeRouteStop> stops, int i) {
        List<GoodsType> ret = new ArrayList<>();
        final int n = stops.size();
        if (n < 2) return ret;
        final TradeRouteStop here = stops.get(i);
        final TradeRouteStop before = stops.get((i - 1 + n) % n);
        for (GoodsType gt : before.getCargo()) {
            if (!here.getCargo().contains(gt) && !ret.contains(gt)) ret.add(gt);
        }
        return ret;
    }

    /**
     * Where goods loaded at a stop are unloaded: the first stop after
     * it that does not list them.
     *
     * @param stops The stops of the route.
     * @param i The index of the loading stop.
     * @param gt The {@code GoodsType}.
     * @return The index of the unloading stop, or -1 if they are
     *     never unloaded.
     */
    public static int unloadedAt(List<TradeRouteStop> stops, int i,
                                 GoodsType gt) {
        final int n = stops.size();
        for (int k = 1; k < n; k++) {
            final int j = (i + k) % n;
            if (!stops.get(j).getCargo().contains(gt)) return j;
        }
        return -1;
    }

    /**
     * Easy mistakes in a route, in words.
     *
     * @param stops The stops of the route.
     * @param holds The number of holds of the smallest carrier on the
     *     route, or 0 if unknown.
     * @param player The {@code Player} (for place names).
     * @return The warnings, empty if none.
     */
    public static List<StringTemplate> warnings(List<TradeRouteStop> stops,
                                                int holds, Player player) {
        List<StringTemplate> ret = new ArrayList<>();
        final int n = stops.size();
        if (n < 2) {
            ret.add(StringTemplate.template("tradeRouteEditor.warn.tooFew"));
            return ret;
        }
        // Goods listed at every stop are never unloaded.
        List<GoodsType> never = new ArrayList<>();
        for (GoodsType gt : stops.get(0).getCargo()) {
            if (never.contains(gt)) continue;
            if (unloadedAt(stops, 0, gt) < 0) {
                never.add(gt);
                ret.add(StringTemplate.template("tradeRouteEditor.warn.neverUnloaded")
                    .addNamed("%goods%", gt));
            }
        }
        for (int i = 0; i < n; i++) {
            final TradeRouteStop stop = stops.get(i);
            final TradeRouteStop next = stops.get((i + 1) % n);
            final String name = placeName(stop, player);
            if (stop.getLocation() != null
                && stop.getLocation() == next.getLocation()) {
                ret.add(StringTemplate.template("tradeRouteEditor.warn.twice")
                    .addName("%place%", name));
            }
            if (stop.getCargo().isEmpty() && unloads(stops, i).isEmpty()) {
                ret.add(StringTemplate.template("tradeRouteEditor.warn.idle")
                    .addName("%place%", name));
            }
            if (holds > 0 && stop.getCargo().size() > holds) {
                ret.add(StringTemplate.template("tradeRouteEditor.warn.holds")
                    .addName("%place%", name)
                    .addAmount("%number%", stop.getCargo().size())
                    .addAmount("%holds%", holds));
            }
            // Loading goods a colony neither has nor makes.
            final Location loc = stop.getLocation();
            if (loc instanceof Colony) {
                final Colony colony = (Colony)loc;
                final List<GoodsType> carriedIn = stops.get((i - 1 + n) % n)
                    .getCargo();
                for (GoodsType gt : holds(stop).keySet()) {
                    if (carriedIn.contains(gt)) continue; // Passing through
                    if (colony.getGoodsCount(gt) <= 0
                        && colony.getNetProductionOf(gt) <= 0) {
                        ret.add(StringTemplate.template("tradeRouteEditor.warn.nothingToLoad")
                            .addName("%place%", name)
                            .addNamed("%goods%", gt));
                    }
                }
            }
        }
        return ret;
    }

    /**
     * The name of the place of a stop.
     *
     * @param stop The {@code TradeRouteStop}.
     * @param player The {@code Player} looking.
     * @return The name.
     */
    public static String placeName(TradeRouteStop stop, Player player) {
        final Location loc = stop.getLocation();
        return (loc == null) ? "?"
            : Messages.message(loc.getLocationLabelFor(player));
    }
}
