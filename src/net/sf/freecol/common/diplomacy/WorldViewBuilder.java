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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.CombatModel;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.LastSale;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * Builds the {@link WorldView} of one nation from a game.
 *
 * Honest mode uses only what that nation could know in the game: its
 * own colonies and units exactly; of others, the Foreign Affairs report
 * (the game's own {@code NationSummary}), the colonies on tiles it has
 * explored, and public news (who is at war with whom).  Defenders of a
 * foreign colony are guessed from the owner's army.  Hindsight mode
 * uses everything, and is only for judging afterwards.
 */
public final class WorldViewBuilder {

    /** Tiles counted as "near" on the same land. */
    public static final int NEAR_TILES = 10;

    /** Tiles from my colonies counted as land I could use. */
    public static final int FREED_RANGE = 2;
    /** Share of freed tiles that would really be worked. */
    public static final double WORKED_SHARE = 0.5;
    /** Turns of my own trade with a tribe that count. */
    public static final int TRADE_MEMORY = 20;
    /** Units guessed per recorded sale (a wagon or hold: 100). */
    public static final int SALE_UNITS = 100;

    /** Braves' worth in soldiers (honest guess for a tribe's army). */
    private static final double BRAVE_SOLDIERS = 0.5;

    private WorldViewBuilder() {}


    /**
     * Build a world view.
     *
     * @param game The {@code Game}.
     * @param me The {@code Player} whose view it is.
     * @param honest True for the honest view, false for hindsight.
     * @param extraMemories Memories known from elsewhere (e.g. units
     *     lost, seen by the replay), may be null.
     * @return The {@code WorldView}.
     */
    public static WorldView build(Game game, Player me, boolean honest,
                                  Collection<Memory> extraMemories) {
        return build(game, me, honest, extraMemories, null);
    }

    /**
     * Build a world view, with what the nation guesses from sightings.
     *
     * @param game The {@code Game}.
     * @param me The {@code Player} whose view it is.
     * @param honest True for the honest view, false for hindsight.
     * @param extraMemories Memories known from elsewhere, may be null.
     * @param bearings Its sightings, may be null.
     * @return The {@code WorldView}.
     */
    public static WorldView build(Game game, Player me, boolean honest,
                                  Collection<Memory> extraMemories,
                                  Bearings bearings) {
        final Specification spec = game.getSpecification();
        final Turn turn = game.getTurn();
        final int now = turn.getNumber();
        final double sp = Estimates.soldierPower(spec);
        final double fp = Estimates.frigatePower(spec);
        final CombatModel cm = game.getCombatModel();

        NationView self = new NationView(me.getNationId(), me.isEuropean(),
                                         true);
        self.army = me.calculateStrength(false) / sp;
        self.navy = me.calculateStrength(true) / fp;
        self.gold = me.getGold();
        self.output = output(me, me.getMarket());
        for (Colony c : me.getColonyList()) {
            self.colonists += c.getUnitCount();
            self.places.add(exactPlace(c, spec, turn, cm));
        }
        self.settlements = self.places.size();

        WorldView w = new WorldView(now, self,
            NationCharacter.forNationType(me.getNationType().getId(),
                                          me.isEuropean()));
        w.soldierAttack = sp;
        w.coalitionLine = Coalition.line(spec.getDifficultyLevel());
        fillSelf(w, me, spec, cm);
        if (me.isEuropean() && me.getMonarch() != null
            && me.getMonarch().getExpeditionaryForce() != null) {
            w.kingArmy = me.getMonarch().getExpeditionaryForce()
                .calculateStrength(false) / sp;
            w.rebels = me.getSoL();
        }

        // Memories from the game's history, plus those given.
        for (HistoryEvent h : me.getHistory()) {
            Memory.Kind k = memoryKind(h);
            if (k == null) continue;
            Player p = game.getFreeColGameObject(h.getPlayerId(), Player.class);
            if (p == null) continue;
            w.memories.add(new Memory(k, p.getNationId(),
                                      h.getTurn().getNumber(), 1));
        }
        if (extraMemories != null) w.memories.addAll(extraMemories);

        final double perColonist = (self.colonists == 0) ? 0
            : self.output / self.colonists;
        List<Player> met = new ArrayList<>();
        final Tile origin = Sightings.origin(me);
        for (Player p : game.getLivePlayerList(me)) {
            if (p.isREF() || p.isUnknownEnemy() || p.isUndead()) continue;
            if (!me.hasContacted(p) && !p.hasContacted(me)) continue;
            met.add(p);
            NationView v = (p.isEuropean())
                ? european(p, me, honest, perColonist, sp, fp, spec, turn, cm)
                : tribe(p, me, honest, sp, cm, now);
            v.stance = me.getStance(p);
            if (v.stance == Stance.UNCONTACTED) v.stance = Stance.PEACE;
            v.tension = tension(me, p);
            v.opinion = Relations.opinion(v.tension, w.memories, v.id, now);
            v.trust = Relations.trust(w.memories, v.id, now);
            v.near = nearness(self.places, v.places, p.isEuropean());
            if (bearings != null && origin != null) {
                v.bearing = bearings.bearing(v.id, origin.getX(), origin.getY(), now);
                // Colonies not found yet, but seen around: guess how near.
                if (v.places.isEmpty() && v.bearing != null && v.european) {
                    v.near = (v.bearing.distance <= NEAR_TILES * 2) ? 0.5 : 0.2;
                }
            }
            v.fear = Relations.fear(self.army, v.army, v.near);
            w.others.put(v.id, v);
        }
        for (Player p : met) {
            for (Player q : met) {
                if (p == q) continue;
                w.setStanceBetween(p.getNationId(), q.getNationId(),
                                   p.getStance(q));
            }
        }
        // Enemy units near my colonies (within sight).
        for (Colony c : me.getColonyList()) {
            for (Tile t : c.getTile().getSurroundingTiles(2)) {
                for (Unit u : t.getUnitList()) {
                    if (u.isOffensiveUnit()
                        && me.getStance(u.getOwner()) == Stance.WAR) {
                        w.enemiesAtGates.add(u.getOwner().getNationId());
                    }
                }
            }
        }
        return w;
    }

    /**
     * The ship for an army on sale in Europe: the cheapest that carries
     * at least {@link #ARMY_SHIP_SPACE} units, else the cheapest.
     */
    private static void shipOnSale(WorldView w, Player me, Specification spec) {
        if (!me.isEuropean() || me.getEurope() == null || me.getMarket() == null
            || me.getPlayerType() != Player.PlayerType.COLONIAL) return;
        int bestPrice = 0, bestSpace = 0;
        boolean bestBig = false;
        for (UnitType ut : spec.getUnitTypeList()) {
            if (!ut.isNaval() || ut.getSpace() <= 0 || !ut.hasPrice()
                || !ut.isAvailableTo(me)) continue;
            final int price = me.getEuropeanPurchasePrice(new AbstractUnit(ut,
                Specification.DEFAULT_ROLE_ID, 1));
            if (price <= 0 || price >= Integer.MAX_VALUE / 2) continue;
            final boolean big = ut.getSpace() >= ARMY_SHIP_SPACE;
            if (bestPrice == 0 || (big && !bestBig)
                || (big == bestBig && price < bestPrice)) {
                bestPrice = price;
                bestSpace = ut.getSpace();
                bestBig = big;
            }
        }
        w.shipPrice = bestPrice;
        w.shipSpace = bestSpace;
    }

    /** Room a ship should have to be worth buying for an army. */
    public static final int ARMY_SHIP_SPACE = 4;

    private static void fillSelf(WorldView w, Player me, Specification spec,
                                 CombatModel cm) {
        List<Double> att = new ArrayList<>();
        for (Unit u : (Iterable<Unit>)me.getUnits()::iterator) {
            if (u.isNaval()) {
                if (!u.isDamaged()) w.carrierSpace += u.getType().getSpace();
            } else if (u.isOffensiveUnit() && u.hasTile()) {
                att.add(cm.getOffencePower(u, null));
            }
        }
        w.attackers = att.stream().mapToDouble(Double::doubleValue)
            .sorted().toArray();
        reverse(w.attackers);

        final Market market = me.getMarket();
        final GoodsType musketsType = spec.getGoodsType("model.goods.muskets");
        final GoodsType horsesType = spec.getGoodsType("model.goods.horses");
        int spare = 0;
        for (Colony c : me.getColonyList()) {
            if (musketsType != null) w.muskets += c.getGoodsCount(musketsType);
            if (horsesType != null) w.horses += c.getGoodsCount(horsesType);
            spare += Math.max(0, c.getUnitCount() - 2);
        }
        w.armable = Math.min(spare, w.muskets / 50);
        shipOnSale(w, me, spec);
        if (market != null && musketsType != null) {
            w.musketPrice = market.getPaidForSale(musketsType);
        }
        if (market == null) return;
        for (GoodsType gt : spec.getStorableGoodsTypeList()) {
            int stock = 0, net = 0, overflow = 0;
            int turnsLeft = Integer.MAX_VALUE;
            for (Colony c : me.getColonyList()) {
                final int s = c.getGoodsCount(gt), n = c.getNetProductionOf(gt);
                stock += s;
                net += n;
                if (!gt.isFoodType()) {
                    overflow = Math.max(overflow,
                        s - (int)(0.8 * c.getWarehouseCapacity()));
                }
                if (n < 0) turnsLeft = Math.min(turnsLeft, s / -n);
            }
            if (stock == 0 && net == 0) continue;
            w.goods.put(gt.getId(), new GoodsView(gt.getId(), stock, net,
                Math.max(0, overflow), turnsLeft, market.getPaidForSale(gt),
                market.getCostToBuy(gt), market.getArrears(gt) > 0));
        }
        w.tax = me.getTax();
    }

    private static NationView european(Player p, Player me, boolean honest,
                                       double perColonist, double sp,
                                       double fp, Specification spec,
                                       Turn turn, CombatModel cm) {
        NationView v = new NationView(p.getNationId(), true, !honest);
        if (honest) {
            NationSummary ns = new NationSummary(p, me);
            v.army = Math.max(0, ns.getMilitaryStrength()) / sp;
            v.navy = Math.max(0, ns.getNavalStrength()) / fp;
            v.gold = ns.getGold();
            v.settlements = ns.getNumberOfSettlements();
            for (Colony c : p.getColonyList()) {
                if (!c.getTile().isExploredBy(me)) continue;
                v.colonists += c.getUnitCount();
            }
            // Colonies in the report but not seen: guess their size
            // from my own colonies.
            int seen = 0;
            for (Colony c : p.getColonyList()) {
                if (c.getTile().isExploredBy(me)) seen++;
            }
            final int myColonies = me.getColonyList().size();
            if (v.settlements > seen && myColonies > 0) {
                v.colonists += (v.settlements - seen)
                    * me.getColonyList().stream()
                        .mapToInt(Colony::getUnitCount).sum() / myColonies;
            }
            v.output = v.colonists * perColonist;
            final UnitType colonist = spec.getDefaultUnitType(p);
            final Role soldier = spec.getRole("model.role.soldier");
            for (Colony c : p.getColonyList()) {
                if (!c.getTile().isExploredBy(me)) continue;
                int n = (int)Math.round(v.army * c.getUnitCount()
                                        / Math.max(1, v.colonists));
                n = Math.max(1, Math.min(n, c.getUnitCount() + 2));
                double[] d = new double[n];
                Arrays.fill(d, CombatMath.defence(spec, turn, p, colonist,
                                                  soldier, c, true));
                PlaceView pv = place(c, d, colonistDefence(spec, turn, p, c));
                pv.freed = freed(c, me, spec);
                v.places.add(pv);
            }
        } else {
            v.army = p.calculateStrength(false) / sp;
            v.navy = p.calculateStrength(true) / fp;
            v.gold = p.getGold();
            v.output = output(p, p.getMarket());
            for (Colony c : p.getColonyList()) {
                v.colonists += c.getUnitCount();
                PlaceView pv = exactPlace(c, spec, turn, cm);
                pv.freed = freed(c, me, spec);
                v.places.add(pv);
            }
            v.settlements = v.places.size();
        }
        return v;
    }

    private static NationView tribe(Player p, Player me, boolean honest,
                                    double sp, CombatModel cm, int now) {
        NationView v = new NationView(p.getNationId(), false, !honest);
        double braves = 0;
        for (IndianSettlement is : p.getIndianSettlementList()) {
            if (honest && !is.getTile().isExploredBy(me)) continue;
            final int size;
            final double[] d;
            if (honest) {
                size = (is.getType().getMinimumSize()
                        + is.getType().getMaximumSize()) / 2;
                d = new double[size];
                Arrays.fill(d, (is.isCapital()) ? 2.0 : 1.5);
            } else {
                List<Unit> defenders = new ArrayList<>(is.getUnitList());
                defenders.addAll(is.getTile().getUnitList());
                size = defenders.size();
                d = new double[size];
                for (int i = 0; i < size; i++) {
                    d[i] = cm.getDefencePower(null, defenders.get(i));
                }
            }
            braves += size;
            v.colonists += size;
            PlaceView pv = new PlaceView(is.getId(), is.getName(),
                p.getNationId(), is.getTile().getX(), is.getTile().getY(),
                is.getTile().getContiguity(), is.getTile().isCoastland(),
                size, true, d, 0);
            pv.freed = freed(is, me, me.getSpecification());
            v.places.add(pv);
            // My own trade with it (my records, so honest).
            for (GoodsType gt : me.getSpecification().getStorableGoodsTypeList()) {
                LastSale ls = me.getLastSale(is, gt);
                if (ls != null && ls.getWhen() != null
                    && now - ls.getWhen().getNumber() <= TRADE_MEMORY) {
                    v.tradeWithMe += ls.getPrice() * SALE_UNITS;
                }
            }
        }
        v.settlements = v.places.size();
        v.army = (honest) ? braves * BRAVE_SOLDIERS
            : p.calculateStrength(false) / sp;
        return v;
    }

    /**
     * What the land a settlement holds next to my colonies would be
     * worth to me, in points over the horizon: for each tile it owns
     * within {@link #FREED_RANGE} of one of my colonies (and that I
     * have explored), the best a colonist could make there at my
     * Europe prices (double for goods my colonies are short of), half
     * of them worked, over {@link Effects#LAND_HORIZON} turns as land
     * stays; tiles two steps away count half.
     *
     * @param s The {@code Settlement}.
     * @param me The observer.
     * @param spec The {@code Specification}.
     * @return The points.
     */
    static double freed(Settlement s, Player me, Specification spec) {
        final Market market = me.getMarket();
        if (market == null || me.getColonyList().isEmpty()) return 0;
        final UnitType worker = spec.getDefaultUnitType(me);
        double gold = 0;
        for (Tile t : s.getOwnedTiles()) {
            if (t == s.getTile() || !t.isExploredBy(me)) continue;
            int dist = Integer.MAX_VALUE;
            for (Colony c : me.getColonyList()) {
                dist = Math.min(dist, c.getTile().getDistanceTo(t));
            }
            if (dist > FREED_RANGE) continue;
            double best = 0;
            for (GoodsType gt : spec.getStorableGoodsTypeList()) {
                int amount = t.getPotentialProduction(gt, worker);
                if (amount > 0) {
                    // What my colonies are short of counts double.
                    best = Math.max(best, amount
                        * (double)Math.max(1, market.getPaidForSale(gt))
                        * ((shortOf(me, gt)) ? 2 : 1));
                }
            }
            gold += (dist <= 1) ? best : best / 2;
        }
        return gold * WORKED_SHARE * Effects.LAND_HORIZON / 100;
    }

    /** Does one of my colonies use more of a good than it makes? */
    private static boolean shortOf(Player me, GoodsType gt) {
        for (Colony c : me.getColonyList()) {
            if (c.getNetProductionOf(gt) < 0) return true;
        }
        return false;
    }

    private static PlaceView exactPlace(Colony c, Specification spec,
                                        Turn turn, CombatModel cm) {
        List<Double> d = new ArrayList<>();
        for (Unit u : c.getTile().getUnitList()) {
            if (u.isNaval() || !u.isDefensiveUnit()) continue;
            d.add(cm.getDefencePower(null, u));
        }
        double[] arr = d.stream().mapToDouble(Double::doubleValue).toArray();
        return place(c, arr, colonistDefence(spec, turn, c.getOwner(), c));
    }

    private static double colonistDefence(Specification spec, Turn turn,
                                          Player owner, Colony c) {
        return CombatMath.defence(spec, turn, owner,
            spec.getDefaultUnitType(owner), spec.getDefaultRole(), c, false);
    }

    private static PlaceView place(Colony c, double[] defence, double last) {
        final Tile t = c.getTile();
        return new PlaceView(c.getId(), c.getName(),
            c.getOwner().getNationId(), t.getX(), t.getY(),
            t.getContiguity(), t.isCoastland(), c.getUnitCount(), false,
            defence, last);
    }

    /**
     * What a nation's colonies make, in gold a turn at its prices.
     */
    private static double output(Player p, Market market) {
        double sum = 0;
        for (Colony c : p.getColonyList()) {
            sum += Estimates.colonyValue(c, market);
        }
        return sum;
    }

    /**
     * Tension of me towards p; for a human player (no tension kept)
     * the other side's tension towards me, mirrored.
     */
    private static int tension(Player me, Player p) {
        if (me.isAI() || !me.isEuropean()) {
            return me.getTension(p).getValue();
        }
        if (!p.isEuropean() || p.isAI()) {
            return p.getTension(me).getValue();
        }
        return -1;
    }

    /**
     * How near another nation is: 1 if one of its places is within
     * NEAR_TILES of one of mine on the same land, 0.5 if both reach the
     * sea (Europeans only; tribes do not sail), 0.2 otherwise.
     */
    static double nearness(List<PlaceView> mine, List<PlaceView> theirs,
                           boolean european) {
        if (mine.isEmpty() || theirs.isEmpty()) return 0.2;
        boolean coastMine = false, coastTheirs = false;
        for (PlaceView a : mine) {
            coastMine |= a.coastal;
            for (PlaceView b : theirs) {
                if (a.land == b.land && a.distance(b) <= NEAR_TILES) return 1.0;
            }
        }
        for (PlaceView b : theirs) coastTheirs |= b.coastal;
        return (european && coastMine && coastTheirs) ? 0.5 : 0.2;
    }

    private static Memory.Kind memoryKind(HistoryEvent h) {
        switch (h.getEventType()) {
        case DECLARE_WAR: return Memory.Kind.WAR;
        case MAKE_PEACE: case CEASE_FIRE: return Memory.Kind.PEACE;
        case COLONY_CONQUERED: return Memory.Kind.COLONY_TAKEN;
        default: return null;
        }
    }

    private static void reverse(double[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            double t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }
}
