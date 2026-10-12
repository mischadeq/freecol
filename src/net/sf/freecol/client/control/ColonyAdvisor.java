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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.common.option.GameOptions;


/**
 * Advice on how each colony could do better, that never breaks the
 * supply network.
 *
 * The network is read from the trade routes: a colony where a route
 * loads goods that are then unloaded in another colony has a duty to
 * supply them.  If the receiving colony is short of those goods
 * (it uses more than it makes), the supplier must keep sending at
 * least its share of that shortfall.  Every suggestion is checked
 * against two rules before it is shown:
 *
 *  - it never brings the production of anything below what the
 *    colony's duties require, and
 *  - it never turns a surplus into a deficit (so a colony never
 *    starts starving, and a carpenter never runs out of lumber).
 *
 * Suggestions are only advice: nothing is moved.  Everything runs on
 * the client and changes nothing in the game.
 */
public final class ColonyAdvisor {

    private static final Logger logger = Logger.getLogger(ColonyAdvisor.class.getName());

    /** How urgent a piece of advice is. */
    public enum Level { ALARM, WARN, INFO }

    /** One piece of advice for a colony. */
    public static final class Advice {
        public final Level level;
        /** A short key for the kind of advice, e.g. "swap". */
        public final String kind;
        public final StringTemplate text;
        /** Goods to show an icon for, or null. */
        public final GoodsType goods;
        /** A few words saying the same, or null. */
        public StringTemplate brief = null;

        Advice(Level level, String kind, StringTemplate text,
               GoodsType goods) {
            this.level = level;
            this.kind = kind;
            this.text = text;
            this.goods = goods;
        }

        /**
         * Set the few words that say the same.
         *
         * @param brief The brief {@code StringTemplate}.
         * @return This advice.
         */
        Advice brief(StringTemplate brief) {
            this.brief = brief;
            return this;
        }

        @Override
        public String toString() {
            return level + " " + kind + ": " + Messages.message(text);
        }
    }

    /**
     * A duty: a colony supplies goods to another colony (or Europe)
     * along a trade route.
     */
    public static final class Duty {
        public final Colony from;
        public final GoodsType goods;
        /** The receiving colony, or null for Europe or elsewhere. */
        public final Colony to;
        /** The name of the receiving place. */
        public final StringTemplate toLabel;
        public final TradeRoute route;
        /** The receiver's shortfall per turn this supplier covers. */
        public int need;

        Duty(Colony from, GoodsType goods, Colony to, StringTemplate toLabel,
             TradeRoute route) {
            this.from = from;
            this.goods = goods;
            this.to = to;
            this.toLabel = toLabel;
            this.route = route;
        }
    }

    /** One colonist's job: who, where, making what, how much. */
    private static final class Job {
        final Unit unit;
        final WorkLocation wl;
        final GoodsType goods;
        final int amount;

        Job(Unit unit, WorkLocation wl, GoodsType goods, int amount) {
            this.unit = unit;
            this.wl = wl;
            this.goods = goods;
            this.amount = amount;
        }
    }

    /** Swaps and moves shown per colony at most. */
    public static final int MAX_MOVES = 3;

    /** Stock of unused goods worth mentioning. */
    public static final int IDLE_STOCK = 100;

    /** Enough of an input in store to count on it for a while. */
    private static final int INPUT_STOCK = 30;

    private final Player player;
    private final Specification spec;

    /** All duties, by supplying colony. */
    private final Map<Colony, List<Duty>> duties = new LinkedHashMap<>();

    /** All duties, by receiving colony. */
    private final Map<Colony, List<Duty>> supplies = new LinkedHashMap<>();

    /** Goods some route loads, by colony. */
    private final Map<Colony, List<GoodsType>> loaded = new HashMap<>();


    /**
     * Build the advisor for a player.
     *
     * @param player The {@code Player} to advise.
     */
    public ColonyAdvisor(Player player) {
        this.player = player;
        this.spec = player.getSpecification();
        for (TradeRoute route : player.getTradeRoutes()) {
            try {
                readRoute(route);
            } catch (RuntimeException re) {
                logger.log(java.util.logging.Level.WARNING,
                    "Advisor could not read route " + route.getName(), re);
            }
        }
        // Share each receiver's shortfall among its suppliers.
        for (Map.Entry<Colony, List<Duty>> e : this.supplies.entrySet()) {
            final Colony to = e.getKey();
            final Map<GoodsType, Integer> suppliers = new HashMap<>();
            for (Duty d : e.getValue()) suppliers.merge(d.goods, 1, Integer::sum);
            for (Duty d : e.getValue()) {
                final int short_ = Math.max(0,
                    -to.getAdjustedNetProductionOf(d.goods));
                final int n = suppliers.get(d.goods);
                d.need = (short_ + n - 1) / n;
            }
        }
    }

    /**
     * Read the duties a trade route sets up.  A carrier leaves each
     * stop with that stop's cargo list on board and unloads whatever
     * the next stop does not list.
     *
     * @param route The {@code TradeRoute} to read.
     */
    private void readRoute(TradeRoute route) {
        final List<TradeRouteStop> stops = route.getStopList();
        final int n = stops.size();
        if (n < 2) return;
        for (int i = 0; i < n; i++) {
            final TradeRouteStop stop = stops.get(i);
            if (!(stop.getLocation() instanceof Colony)) continue;
            final Colony from = (Colony)stop.getLocation();
            if (from.getOwner() != this.player) continue;
            final List<GoodsType> before = stops.get((i - 1 + n) % n)
                .getCargo();
            for (GoodsType gt : stop.getCargo()) {
                if (before.contains(gt)) continue; // Already aboard
                List<GoodsType> l = this.loaded.computeIfAbsent(from,
                    k -> new ArrayList<>());
                if (l.contains(gt)) continue;
                l.add(gt);
                // Follow the goods to where they are unloaded.
                for (int k = 1; k < n; k++) {
                    final TradeRouteStop next = stops.get((i + k) % n);
                    if (next.getCargo().contains(gt)) continue;
                    final Location loc = next.getLocation();
                    final Colony to = (loc instanceof Colony
                        && loc != from) ? (Colony)loc : null;
                    if (loc == from) break; // Back where it came from
                    Duty d = new Duty(from, gt, to,
                        (loc == null) ? StringTemplate.name("?")
                            : loc.getLocationLabelFor(this.player), route);
                    this.duties.computeIfAbsent(from, x -> new ArrayList<>())
                        .add(d);
                    if (to != null) {
                        this.supplies.computeIfAbsent(to,
                            x -> new ArrayList<>()).add(d);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Get the duties of a colony: what it sends where.
     *
     * @param colony The supplying {@code Colony}.
     * @return A list of {@code Duty}s.
     */
    public List<Duty> getDuties(Colony colony) {
        List<Duty> l = this.duties.get(colony);
        return (l == null) ? new ArrayList<>() : new ArrayList<>(l);
    }

    /**
     * Get what other colonies send to this one.
     *
     * @param colony The receiving {@code Colony}.
     * @return A list of {@code Duty}s.
     */
    public List<Duty> getSupplies(Colony colony) {
        List<Duty> l = this.supplies.get(colony);
        return (l == null) ? new ArrayList<>() : new ArrayList<>(l);
    }

    /**
     * How much of some goods a colony must keep making per turn for
     * the colonies it supplies.
     *
     * @param colony The {@code Colony}.
     * @param gt The {@code GoodsType}.
     * @return The required net production.
     */
    public int getRequired(Colony colony, GoodsType gt) {
        gt = gt.getStoredAs(); // Grain is stored as food
        int r = 0;
        for (Duty d : getDuties(colony)) {
            if (d.goods == gt) r += d.need;
        }
        return r;
    }

    /**
     * Is a duty covered?
     *
     * @param d The {@code Duty}.
     * @return "short" if the supplier makes less than the receiver
     *     lacks, "none" if it has nothing to send, else "ok".
     */
    public String getStatus(Duty d) {
        final GoodsType gt = d.goods;
        final int net = d.from.getAdjustedNetProductionOf(gt.getStoredAs());
        final int need = getRequired(d.from, gt);
        if (need > 0 && net < need) return "short";
        if (net <= 0 && d.from.getGoodsCount(gt) < 50
            && d.from.getNetProductionOf(gt) <= 0) return "none";
        return "ok";
    }

    /**
     * May a colony lose some production of some goods?
     *
     * @param colony The {@code Colony}.
     * @param gt The {@code GoodsType}.
     * @param loss The amount lost per turn (positive).
     * @return True if no duty is broken and no surplus turns into a
     *     deficit.
     */
    public boolean mayLose(Colony colony, GoodsType gt, int loss) {
        if (loss <= 0) return true;
        gt = gt.getStoredAs(); // Grain is stored as food
        final int net = colony.getAdjustedNetProductionOf(gt);
        final int after = net - loss;
        if (after < getRequired(colony, gt)) return false;
        return after >= Math.min(0, net);
    }


    // Advice

    /**
     * Advise a colony.
     *
     * @param colony The {@code Colony} to advise.
     * @return The advice, most urgent first.
     */
    public List<Advice> advise(Colony colony) {
        final List<Advice> result = new ArrayList<>();
        try {
            duties(colony, result);
            government(colony, result);
            work(colony, result);
            idleStock(colony, result);
            defence(colony, result);
        } catch (RuntimeException re) {
            // Advice is a convenience, never let it break the game.
            logger.log(java.util.logging.Level.WARNING,
                "Advisor failed for " + colony.getName(), re);
        }
        result.sort(Comparator.comparing((Advice a) -> a.level));
        return result;
    }

    /** Is each duty of the colony covered by what it makes? */
    private void duties(Colony colony, List<Advice> result) {
        final Map<GoodsType, List<Duty>> byGoods = new LinkedHashMap<>();
        for (Duty d : getDuties(colony)) {
            if (d.to == null) continue;
            byGoods.computeIfAbsent(d.goods, k -> new ArrayList<>()).add(d);
        }
        for (Map.Entry<GoodsType, List<Duty>> e : byGoods.entrySet()) {
            final GoodsType gt = e.getKey();
            final int net = colony.getAdjustedNetProductionOf(gt);
            final int need = getRequired(colony, gt);
            final Duty d = e.getValue().get(0);
            if (need > 0 && net < need) {
                // How long can the receiver hold out on its stock?
                final int recvNet = d.to.getAdjustedNetProductionOf(gt);
                final int turns = (recvNet < 0)
                    ? d.to.getGoodsCount(gt) / -recvNet : Integer.MAX_VALUE;
                result.add(new Advice((turns <= 10) ? Level.ALARM : Level.WARN,
                    "roleShort", StringTemplate
                        .template("companion.advice.roleShort")
                        .addNamed("%goods%", gt)
                        .addStringTemplate("%to%", d.toLabel)
                        .addAmount("%need%", need)
                        .addAmount("%have%", Math.max(0, net)), gt)
                    .brief(StringTemplate.template("companion.brief.roleShort")
                        .addStringTemplate("%to%", d.toLabel)
                        .addAmount("%need%", need)
                        .addAmount("%have%", Math.max(0, net))));
            } else if (net <= 0 && colony.getGoodsCount(gt) < 50
                && colony.getNetProductionOf(gt) <= 0) {
                result.add(new Advice(Level.WARN, "roleNone", StringTemplate
                    .template("companion.advice.roleNone")
                    .addNamed("%goods%", gt)
                    .addStringTemplate("%to%", d.toLabel)
                    .addName("%route%", d.route.getName())
                    .addAmount("%stock%", colony.getGoodsCount(gt)), gt)
                    .brief(StringTemplate.template("companion.brief.roleNone")
                        .addStringTemplate("%to%", d.toLabel)));
            }
        }
    }

    /** Bells, rebels and the tory penalty. */
    private void government(Colony colony, List<Advice> result) {
        final int size = colony.getUnitCount();
        if (size <= 0) return;
        final int sol = colony.getSonsOfLiberty();
        final int tories = Colony.calculateToryCount(size, sol);
        final int bad = spec.getInteger(GameOptions.BAD_GOVERNMENT_LIMIT);
        final int veryBad = spec.getInteger(GameOptions.VERY_BAD_GOVERNMENT_LIMIT);
        final int good = spec.getInteger(GameOptions.GOOD_GOVERNMENT_LIMIT);
        final GoodsType bells = spec.getGoodsType("model.goods.bells");
        final int bonus = colony.getProductionBonus();

        if (bonus < 0) {
            result.add(new Advice(Level.ALARM, "tory", StringTemplate
                .template("companion.advice.toryPenalty")
                .addAmount("%penalty%", -bonus)
                .addAmount("%tories%", tories)
                .addAmount("%size%", size)
                .addAmount("%limit%", (bonus < -1) ? veryBad : bad), bells)
                .brief(StringTemplate.template("companion.brief.tory")
                    .addAmount("%penalty%", -bonus)));
        } else if (bonus == 0 && sol < good && tories == bad) {
            result.add(new Advice(Level.WARN, "toryEdge", StringTemplate
                .template("companion.advice.toryEdge")
                .addAmount("%tories%", tories)
                .addAmount("%limit%", bad), bells));
            // No brief: the liberty gauge shows the limit.
        }

        // Who makes the bells?
        final WorkLocation hall = colony.getWorkLocationForProducing(bells);
        // Every colonist but the first few uses a bell a turn; only
        // what is left over makes rebels.
        final int rate = colony.getNetProductionOf(bells);
        final int use = colony.getConsumptionOf(bells);
        final int made = rate + use;
        if (hall instanceof Building && ((Building)hall).getUnitCount() == 0
            && size >= 2 && sol < 100) {
            Job best = bestMoveTo(colony, (Building)hall, bells);
            StringTemplate t;
            if (best != null && inTrade(best)) {
                // Only experts could go: better bring someone in.
                t = StringTemplate.template("companion.advice.noBellsBring")
                    .addAmount("%bells%", hall.getPotentialProduction(bells,
                            spec.getDefaultUnitType(this.player)));
            } else if (best != null) {
                t = StringTemplate.template("companion.advice.noBellsMove")
                    .addNamed("%unit%", best.unit.getType())
                    .addNamed("%from%", best.goods)
                    .addAmount("%loss%", best.amount)
                    .addAmount("%bells%", hall.getPotentialProduction(bells,
                            best.unit.getType()));
            } else {
                t = StringTemplate.template("companion.advice.noBells");
            }
            result.add(new Advice(Level.WARN, "noBells", t, bells)
                .brief(StringTemplate.template("companion.brief.noBells")));
        }

        // How far to the next step up?
        if (sol < good) {
            final int needed = (int)Math.ceil(good / 100.0
                * Colony.LIBERTY_PER_REBEL * size) - colony.getLiberty();
            if (needed > 0) {
                StringTemplate t = (rate > 0)
                    ? StringTemplate.template("companion.advice.toGood")
                        .addAmount("%bells%", needed)
                        .addAmount("%rate%", rate)
                        .addAmount("%turns%", (needed + rate - 1) / rate)
                    : (made > 0)
                    ? StringTemplate.template("companion.advice.toGoodShort")
                        .addAmount("%bells%", needed)
                        .addAmount("%made%", made)
                        .addAmount("%use%", use)
                    : StringTemplate.template("companion.advice.toGoodNone")
                        .addAmount("%bells%", needed)
                        .addAmount("%use%", use);
                result.add(new Advice((rate > 0) ? Level.INFO : Level.WARN,
                    "toGood",
                    t.addAmount("%sol%", sol).addAmount("%good%", good),
                    bells));
                // No brief: the liberty gauge shows this.
            }
        }
    }

    /**
     * The colonists' jobs.
     *
     * @param colony The {@code Colony}.
     * @return A list of {@code Job}s.
     */
    private List<Job> jobs(Colony colony) {
        List<Job> jobs = new ArrayList<>();
        for (Unit u : colony.getUnitList()) {
            if (!(u.getLocation() instanceof WorkLocation)) continue;
            final WorkLocation wl = (WorkLocation)u.getLocation();
            final GoodsType gt = u.getWorkType();
            if (gt == null) continue;
            jobs.add(new Job(u, wl, gt, output(colony, wl, gt,
                        u.getType(), 0)));
        }
        return jobs;
    }

    /**
     * What a unit type would make at a work place, as far as the
     * colony has the material for it.
     *
     * @param colony The {@code Colony}.
     * @param wl The {@code WorkLocation}.
     * @param gt The {@code GoodsType} to make.
     * @param type The {@code UnitType} working.
     * @param current What is made there now by the unit being
     *     replaced, used to limit the gain to the material at hand.
     * @return The amount per turn.
     */
    private int output(Colony colony, WorkLocation wl, GoodsType gt,
                       UnitType type, int current) {
        int p = wl.getPotentialProduction(gt, type);
        final GoodsType input = gt.getInputType();
        if (input != null && wl instanceof Building && p > current
            && colony.getGoodsCount(input) < INPUT_STOCK) {
            // Not enough in store: only what is spare each turn helps.
            p = Math.min(p, current
                + Math.max(0, colony.getAdjustedNetProductionOf(input)));
        }
        return p;
    }

    /**
     * Find the colonist who is best moved into a building to make
     * some goods, if any may leave his job.
     */
    private Job bestMoveTo(Colony colony, Building b, GoodsType gt) {
        Job best = null;
        int[] bestKey = null;
        for (Job j : jobs(colony)) {
            if (j.wl == b) continue;
            if (!b.canAddType(j.unit.getType())) continue;
            final int gain = b.getPotentialProduction(gt, j.unit.getType());
            if (gain <= 0 || !mayLose(colony, j.goods, j.amount)) continue;
            // Leave experts in their trade if anyone else can go;
            // then the most bells, then the smallest loss.
            final int[] key = { inTrade(j) ? 1 : 0, -gain, j.amount };
            if (best == null || java.util.Arrays.compare(key, bestKey) < 0) {
                best = j;
                bestKey = key;
            }
        }
        return best;
    }

    /** Is a colonist working in his own trade? */
    private static boolean inTrade(Job j) {
        final GoodsType skill = j.unit.getType().getExpertProduction();
        return skill != null && skill == j.goods;
    }

    /**
     * Swaps of two colonists that make more of something and less of
     * nothing, and experts that could move to their own trade.
     */
    private void work(Colony colony, List<Advice> result) {
        final List<Job> jobs = jobs(colony);
        // {priority, gain, unit, other unit, Advice}
        final List<Object[]> found = new ArrayList<>();
        final List<Unit> used = new ArrayList<>();

        // Swaps: every pair of colonists of different types.
        for (int a = 0; a < jobs.size(); a++) {
            for (int b = a + 1; b < jobs.size(); b++) {
                final Job ja = jobs.get(a), jb = jobs.get(b);
                if (ja.unit.getType() == jb.unit.getType()) continue;
                final int na = output(colony, ja.wl, ja.goods,
                                      jb.unit.getType(), ja.amount);
                final int nb = output(colony, jb.wl, jb.goods,
                                      ja.unit.getType(), jb.amount);
                final Map<GoodsType, Integer> delta = new LinkedHashMap<>();
                delta.merge(ja.goods, na - ja.amount, Integer::sum);
                delta.merge(jb.goods, nb - jb.amount, Integer::sum);
                int gain = 0;
                boolean ok = true;
                for (Map.Entry<GoodsType, Integer> e : delta.entrySet()) {
                    if (e.getValue() < 0) ok = false; // Only clear wins
                    gain += Math.max(0, e.getValue());
                }
                if (!ok || gain <= 0) continue;
                found.add(new Object[] { 0, gain, ja.unit, jb.unit,
                    new Advice(Level.INFO, "swap", StringTemplate
                        .template("companion.advice.swap")
                        .addNamed("%unit1%", ja.unit.getType())
                        .addNamed("%goods1%", ja.goods)
                        .addNamed("%unit2%", jb.unit.getType())
                        .addNamed("%goods2%", jb.goods)
                        .addName("%gain%", gains(delta)),
                        (delta.get(ja.goods) > 0) ? ja.goods : jb.goods)
                    .brief(StringTemplate.template("companion.brief.swap")
                        .addName("%gain%", gains(delta))) });
            }
        }

        // Experts not working in their trade, with a free place to go.
        for (Job j : jobs) {
            final GoodsType skill = j.unit.getType().getExpertProduction();
            if (skill == null || skill == j.goods) continue;
            WorkLocation place = null;
            int best = 0;
            for (WorkLocation wl : colony.getAvailableWorkLocationsList()) {
                if (wl == j.wl || !wl.canAdd(j.unit)) continue;
                if (wl instanceof ColonyTile
                    && ((ColonyTile)wl).getWorkTile().getOwningSettlement()
                    != colony) continue;
                final int p = output(colony, wl, skill, j.unit.getType(), 0);
                if (p > best) { best = p; place = wl; }
            }
            if (place == null || best <= j.amount) continue;
            if (!mayLose(colony, j.goods, j.amount)) continue;
            final Map<GoodsType, Integer> delta = new LinkedHashMap<>();
            delta.put(skill, best);
            delta.merge(j.goods, -j.amount, Integer::sum);
            found.add(new Object[] { 1, best - j.amount, j.unit, null,
                new Advice(Level.INFO, "expert", StringTemplate
                    .template("companion.advice.expert")
                    .addNamed("%unit%", j.unit.getType())
                    .addNamed("%goods%", j.goods)
                    .addNamed("%skill%", skill)
                    .addName("%gain%", gains(delta)), skill)
                .brief(StringTemplate.template("companion.brief.expert")
                    .addName("%gain%", gains(delta))) });
        }

        // Swaps that lose nothing first, then the biggest gains; each
        // colonist in one suggestion only.
        found.sort(Comparator.comparingInt((Object[] f) -> (Integer)f[0])
            .thenComparingInt(f -> -(Integer)f[1]));
        int shown = 0;
        for (Object[] f : found) {
            if (shown >= MAX_MOVES) break;
            if (used.contains((Unit)f[2])
                || (f[3] != null && used.contains((Unit)f[3]))) continue;
            used.add((Unit)f[2]);
            if (f[3] != null) used.add((Unit)f[3]);
            result.add((Advice)f[4]);
            shown++;
        }
    }

    /** "+3 food, −2 ore" */
    private static String gains(Map<GoodsType, Integer> delta) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<GoodsType, Integer> e : delta.entrySet()) {
            if (e.getValue() == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append((e.getValue() > 0) ? "+" : "−")
                .append(Math.abs(e.getValue())).append(' ')
                .append(Messages.getName(e.getKey()).toLowerCase());
        }
        return sb.toString();
    }

    /** Goods piling up that nothing here uses and no route takes. */
    private void idleStock(Colony colony, List<Advice> result) {
        final Market market = this.player.getMarket();
        final List<GoodsType> taken = this.loaded.get(colony);
        for (GoodsType gt : spec.getStorableGoodsTypeList()) {
            final int amount = colony.getGoodsCount(gt);
            if (amount < IDLE_STOCK) continue;
            if (gt.isFoodType() || gt.isBuildingMaterial()
                || gt.isBreedable() || gt.getMilitary()) continue;
            if (taken != null && taken.contains(gt)) continue;
            if (usedHere(colony, gt)) continue;
            Duty delivered = null;
            for (Duty d : getSupplies(colony)) {
                if (d.goods == gt) delivered = d;
            }
            final int sell = Math.min(amount, colony.getWarehouseCapacity());
            final int gold = (market == null || !this.player.canTrade(gt))
                ? 0 : market.getSalePrice(gt, sell)
                    * (100 - this.player.getTax()) / 100;
            if (delivered != null) {
                // A route brings them here, but none takes them on.
                result.add(new Advice(Level.WARN, "deadEnd", StringTemplate
                    .template("companion.advice.deadEnd")
                    .addNamed("%goods%", gt)
                    .addName("%from%", delivered.from.getName())
                    .addAmount("%amount%", amount)
                    .addAmount("%gold%", gold), gt)
                    .brief(StringTemplate.template("companion.brief.deadEnd")
                        .addAmount("%amount%", amount)));
                continue;
            }
            result.add(new Advice(Level.INFO, "idle", StringTemplate
                .template((gold > 0) ? "companion.advice.idleSell"
                    : "companion.advice.idle")
                .addAmount("%amount%", amount)
                .addNamed("%goods%", gt)
                .addAmount("%gold%", gold), gt)
                .brief(StringTemplate.template("companion.brief.idle")
                    .addAmount("%amount%", amount)
                    .addAmount("%gold%", gold)));
        }
    }

    /** Does a colonist here work up these goods (lumber into hammers)? */
    private static boolean usedHere(Colony colony, GoodsType gt) {
        if (colony.getConsumptionOf(gt) > 0) return true;
        for (Unit u : colony.getUnitList()) {
            final GoodsType wt = u.getWorkType();
            if (wt != null && wt.getInputType() == gt) return true;
        }
        return false;
    }

    /** Muskets in store but nobody to defend the colony. */
    private void defence(Colony colony, List<Advice> result) {
        final GoodsType muskets = spec.getGoodsType("model.goods.muskets");
        final GoodsType horses = spec.getGoodsType("model.goods.horses");
        if (muskets == null || colony.getGoodsCount(muskets) < 50) return;
        for (Unit u : colony.getTile().getUnitList()) {
            if (u.isOffensiveUnit()) return;
        }
        result.add(new Advice(Level.INFO, "arm", StringTemplate
            .template((horses != null && colony.getGoodsCount(horses) >= 50)
                ? "companion.advice.armDragoon" : "companion.advice.arm")
            .addAmount("%muskets%", colony.getGoodsCount(muskets))
            .addAmount("%horses%", (horses == null) ? 0
                : colony.getGoodsCount(horses)), muskets)
            .brief(StringTemplate.template("companion.brief."
                + ((horses != null && colony.getGoodsCount(horses) >= 50)
                ? "armDragoon" : "arm"))));
    }
}
