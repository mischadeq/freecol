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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Unit;


/**
 * A forecast of what the player's trade routes are going to deliver
 * to each colony, and when.
 *
 * The forecast walks every wagon train and ship along the stops of
 * its trade route, starting from where it is now, and notes where it
 * will unload which goods and after how many turns.  It is an
 * estimate: it assumes each carrier visits every stop in order, and
 * that what a colony can spare stays roughly as it is today.
 *
 * This runs entirely on the client and changes nothing in the game.
 */
public final class SupplyForecast {

    private static final Logger logger = Logger.getLogger(SupplyForecast.class.getName());

    /** How well a colony's need for some goods is covered. */
    public enum Status {
        /** No trade route delivers these goods to the colony. */
        NO_ROUTE,
        /** A route should deliver, but has no carrier assigned. */
        NO_CARRIER,
        /** A route has carriers, but none will bring any. */
        NOTHING_COMING,
        /** A delivery is on its way but will arrive too late. */
        LATE,
        /** A delivery will arrive in time. */
        IN_TIME
    }

    /** An expected delivery of some goods to a colony. */
    public static final class Delivery {
        /** The carrier making the delivery. */
        public final Unit unit;
        /** The trade route the carrier is following. */
        public final TradeRoute route;
        /** Turns until the carrier reaches the colony. */
        public final int turns;
        /** The amount expected to be unloaded. */
        public final int amount;
        /** The colony the goods are unloaded at. */
        public final Colony colony;
        /** The goods unloaded. */
        public final GoodsType goodsType;

        Delivery(Unit unit, TradeRoute route, int turns, int amount,
                 Colony colony, GoodsType goodsType) {
            this.unit = unit;
            this.route = route;
            this.turns = turns;
            this.amount = amount;
            this.colony = colony;
            this.goodsType = goodsType;
        }
    }

    /** The answer to "is this need covered?". */
    public static final class Outlook {
        public final Status status;
        /** The earliest delivery, or null if there is none. */
        public final Delivery delivery;
        /** A trade route that should deliver, or null if none does. */
        public final TradeRoute route;

        Outlook(Status status, Delivery delivery, TradeRoute route) {
            this.status = status;
            this.delivery = delivery;
            this.route = route;
        }

        /**
         * Is there a problem with a supply line that exists?
         *
         * @return True if a route should deliver but will not do so
         *     in time.
         */
        public boolean isBroken() {
            return this.status == Status.NO_CARRIER
                || this.status == Status.NOTHING_COMING
                || this.status == Status.LATE;
        }
    }

    /** One colony and goods type that some trade route delivers to. */
    public static final class Target {
        public final Colony colony;
        public final GoodsType goodsType;
        /** A route that is set up to make this delivery. */
        public final TradeRoute route;

        Target(Colony colony, GoodsType goodsType, TradeRoute route) {
            this.colony = colony;
            this.goodsType = goodsType;
            this.route = route;
        }
    }


    /** Expected deliveries, by colony and goods type. */
    private final Map<String, List<Delivery>> deliveries = new HashMap<>();

    /** A route that is set up to deliver, by colony and goods type. */
    private final Map<String, TradeRoute> routes = new HashMap<>();

    /** The colony and goods type behind each key of {@code routes}. */
    private final Map<String, Target> targets = new HashMap<>();

    /** Expected deliveries, by the identifier of the carrier. */
    private final Map<String, List<Delivery>> byUnit = new HashMap<>();

    /** The number of carriers assigned to each route. */
    private final Map<TradeRoute, Integer> carriers = new HashMap<>();


    /**
     * Build the forecast for a player.
     *
     * @param player The {@code Player} to forecast for.
     */
    public SupplyForecast(Player player) {
        for (TradeRoute route : player.getTradeRoutes()) {
            try {
                examine(route);
            } catch (RuntimeException re) {
                // A forecast is a convenience, never let it break the game.
                logger.log(Level.WARNING, "Supply forecast failed for "
                    + route.getName(), re);
            }
        }
    }


    private static String key(Colony colony, GoodsType goodsType) {
        return colony.getId() + "/" + goodsType.getId();
    }

    /**
     * Work out what one trade route is set up to deliver, and what
     * its carriers are actually going to deliver.
     *
     * @param route The {@code TradeRoute} to examine.
     */
    private void examine(TradeRoute route) {
        final List<TradeRouteStop> stops = route.getStopList();
        final int n = stops.size();
        if (n < 2) return;

        // A carrier leaves each stop with that stop's cargo list on
        // board, and unloads whatever the next stop does not list.
        // So goods are delivered to a stop when the stop before it
        // lists them and the stop itself does not.
        for (int i = 0; i < n; i++) {
            final TradeRouteStop stop = stops.get(i);
            if (!(stop.getLocation() instanceof Colony)) continue;
            final Colony colony = (Colony)stop.getLocation();
            final TradeRouteStop before = stops.get((i - 1 + n) % n);
            for (GoodsType gt : before.getCargo()) {
                if (stop.getCargo().contains(gt)) continue;
                final String k = key(colony, gt);
                if (!this.routes.containsKey(k)) {
                    this.routes.put(k, route);
                    this.targets.put(k, new Target(colony, gt, route));
                }
            }
        }

        final List<Unit> units = route.getAssignedUnits();
        this.carriers.put(route, units.size());
        for (Unit unit : units) follow(unit, route, stops);
    }

    /**
     * Follow one carrier around its route and record what it will
     * deliver where.
     *
     * @param unit The carrier {@code Unit}.
     * @param route The {@code TradeRoute} it is following.
     * @param stops The stops of the route.
     */
    private void follow(Unit unit, TradeRoute route,
                        List<TradeRouteStop> stops) {
        final int n = stops.size();
        int current = unit.getCurrentStop();
        if (current < 0 || current >= n) current = 0;

        final Map<GoodsType, Integer> aboard = new HashMap<>();
        for (Goods g : unit.getCompactGoodsList()) {
            aboard.merge(g.getType(), g.getAmount(), Integer::sum);
        }

        // Only the first delivery of each kind by this carrier matters.
        final Set<String> seen = new HashSet<>();
        Location from = null;
        int turns = 0;

        // Twice around is enough to see everything loaded anywhere
        // on the route get unloaded again.
        for (int step = 0; step < 2 * n; step++) {
            final TradeRouteStop stop = stops.get((current + step) % n);
            final Location here = stop.getLocation();
            if (here == null) return;

            int leg;
            if (from == null) {
                leg = (unit.atStop(stop)) ? 0 : unit.getTurnsToReach(here);
            } else {
                leg = unit.getTurnsToReach(from, here);
            }
            if (leg >= Unit.MANY_TURNS) return; // No way through.
            turns += leg;
            from = here;

            // Unload everything this stop does not list.
            final List<GoodsType> cargo = stop.getCargo();
            for (GoodsType gt : new ArrayList<>(aboard.keySet())) {
                if (cargo.contains(gt)) continue;
                final int amount = aboard.remove(gt);
                if (amount <= 0 || !(here instanceof Colony)) continue;
                final String k = key((Colony)here, gt);
                if (!seen.add(k)) continue;
                final Delivery d = new Delivery(unit, route, turns, amount,
                                                (Colony)here, gt);
                this.deliveries.computeIfAbsent(k, x -> new ArrayList<>())
                    .add(d);
                this.byUnit.computeIfAbsent(unit.getId(),
                    x -> new ArrayList<>()).add(d);
                if (!this.routes.containsKey(k)) {
                    this.routes.put(k, route);
                    this.targets.put(k, new Target((Colony)here, gt, route));
                }
            }

            // Load what this stop lists, as far as it can spare it.
            for (AbstractGoods ag : stop.getCompactCargo()) {
                final GoodsType gt = ag.getType();
                final int have = aboard.getOrDefault(gt, 0);
                final int room = ag.getAmount() - have;
                if (room <= 0) continue;
                final int add = Math.min(room, stop.getExportAmount(gt, turns));
                if (add > 0) aboard.put(gt, have + add);
            }
        }
    }


    /**
     * Get the colonies and goods types that trade routes are set up
     * to deliver to.
     *
     * @return A list of {@code Target}s.
     */
    public List<Target> getTargets() {
        return new ArrayList<>(this.targets.values());
    }

    /**
     * Get what one carrier is expected to deliver on its next round.
     *
     * @param unit The carrier {@code Unit}.
     * @return A list of {@code Delivery}s, soonest first.
     */
    public List<Delivery> getDeliveries(Unit unit) {
        List<Delivery> result = new ArrayList<>();
        final List<Delivery> list = this.byUnit.get(unit.getId());
        if (list != null) result.addAll(list);
        result.sort((a, b) -> Integer.compare(a.turns, b.turns));
        return result;
    }

    /**
     * Get the earliest expected delivery of some goods to a colony.
     *
     * @param colony The {@code Colony} to deliver to.
     * @param goodsType The {@code GoodsType} to deliver.
     * @return The earliest {@code Delivery}, or null if none expected.
     */
    public Delivery getEarliest(Colony colony, GoodsType goodsType) {
        final List<Delivery> list = this.deliveries.get(key(colony, goodsType));
        if (list == null) return null;
        Delivery best = null;
        for (Delivery d : list) {
            if (best == null || d.turns < best.turns) best = d;
        }
        return best;
    }

    /**
     * Is a colony's need for some goods covered by its supply lines?
     *
     * @param colony The {@code Colony} in need.
     * @param goodsType The {@code GoodsType} needed.
     * @param turnsLeft The number of turns until the colony runs out,
     *     or negative if there is no deadline.
     * @return The {@code Outlook} for this need.
     */
    public Outlook getOutlook(Colony colony, GoodsType goodsType,
                              int turnsLeft) {
        final TradeRoute route = this.routes.get(key(colony, goodsType));
        final Delivery d = getEarliest(colony, goodsType);
        if (d == null) {
            if (route == null) {
                return new Outlook(Status.NO_ROUTE, null, null);
            }
            final Integer count = this.carriers.get(route);
            return new Outlook((count == null || count == 0)
                ? Status.NO_CARRIER : Status.NOTHING_COMING, null, route);
        }
        return new Outlook((turnsLeft < 0 || d.turns <= turnsLeft)
            ? Status.IN_TIME : Status.LATE, d, d.route);
    }
}
