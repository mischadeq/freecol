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
import java.util.List;

import junit.framework.TestCase;
import net.sf.freecol.common.model.Stance;


public class DiplomacyTest extends TestCase {

    private static final String DUTCH = "model.nation.dutch",
        FRENCH = "model.nation.french", ENGLISH = "model.nation.english",
        SPANISH = "model.nation.spanish";

    // ---- Threat

    public void testThreatWorkedExample() {
        // The design's example: Spring 1662.
        double armies = 4 + 15 + 14 + 29, outputs = 418 + 314 + 290 + 290;
        assertEquals(0.17, Threat.threat(4, armies, 418, outputs), 0.005);
        assertEquals(0.37, Threat.threat(29, armies, 290, outputs), 0.005);
        assertTrue(Threat.threat(29, armies, 290, outputs)
                   > Threat.COALITION_LINE);
        assertEquals(0.0, Threat.threat(0, 0, 0, 0), 1e-9);
    }

    public void testLeaderToContain() {
        WorldView w = world(DUTCH, NationCharacter.TRADING_EMPIRE, 4, 418);
        other(w, FRENCH, 15, 314, Stance.PEACE, 0.5);
        other(w, ENGLISH, 14, 290, Stance.PEACE, 0.5);
        other(w, SPANISH, 29, 290, Stance.PEACE, 0.5);
        assertEquals(SPANISH, Threat.leaderToContain(w));
        w.others.get(SPANISH).army = 10; // no longer far ahead
        assertNull(Threat.leaderToContain(w));
    }

    // ---- Relations and memories

    public void testOpinionFromTension() {
        assertEquals(100.0, Relations.opinion(0, null, FRENCH, 1), 1e-9);
        assertEquals(0.0, Relations.opinion(550, null, FRENCH, 1), 1e-9);
        assertEquals(-100.0, Relations.opinion(1100, null, FRENCH, 1), 1e-9);
        assertEquals(0.0, Relations.opinion(-1, null, FRENCH, 1), 1e-9);
    }

    public void testMemoriesFade() {
        List<Memory> m = new ArrayList<>();
        m.add(new Memory(Memory.Kind.WAR, FRENCH, 100, 1));
        m.add(new Memory(Memory.Kind.WAR, ENGLISH, 100, 1)); // other nation
        assertEquals(-30.0, Relations.opinion(-1, m, FRENCH, 100), 1e-9);
        assertEquals(-15.0, Relations.opinion(-1, m, FRENCH, 115), 1e-9);
        assertEquals(0.0, Relations.opinion(-1, m, FRENCH, 130), 1e-9);
        assertEquals(0.0, Relations.opinion(-1, m, FRENCH, 99), 1e-9);
        // Counted memories and the floor.
        m.add(new Memory(Memory.Kind.UNIT_LOST, FRENCH, 100, 50));
        assertEquals(-100.0, Relations.opinion(100, m, FRENCH, 100), 1e-9);
    }

    public void testFear() {
        assertEquals(0.0, Relations.fear(10, 0, 1), 1e-9);
        assertEquals(50.0, Relations.fear(10, 10, 1), 1e-9);
        assertEquals(25.0, Relations.fear(10, 10, 0.5), 1e-9);
    }

    // ---- Capture simulation

    public void testOneFightMatchesTheGameRule() {
        PlaceView camp = place("p", FRENCH, true, new double[] { 2 }, 0);
        CaptureSim.Result r = CaptureSim.attack(new double[] { 6 }, camp, 7);
        assertEquals(6.0 / 8.0, r.chance, 0.05);
        assertEquals(2.0 / 8.0, r.lost, 0.05);
        // A colony with no armed defender: one fight against a colonist.
        PlaceView colony = place("c", FRENCH, false, new double[0], 1);
        r = CaptureSim.attack(new double[] { 3 }, colony, 7);
        assertEquals(0.75, r.chance, 0.05);
    }

    public void testMoreDefendersHarder() {
        PlaceView one = place("a", FRENCH, false, new double[] { 3 }, 1);
        PlaceView three = place("b", FRENCH, false, new double[] { 3, 3, 3 }, 1);
        double[] force = CaptureSim.force(3, 4.5);
        assertTrue(CaptureSim.attack(force, one, 1).chance
                   > CaptureSim.attack(force, three, 1).chance);
        assertEquals(0.0, CaptureSim.attack(new double[0], one, 1).chance,
                     1e-9);
        // Same question, same answer.
        assertEquals(CaptureSim.attack(force, three, 5).chance,
                     CaptureSim.attack(force, three, 5).chance, 1e-12);
    }

    // ---- Characters

    public void testEveryNationTypeHasACharacter() {
        assertEquals(NationCharacter.TRADING_EMPIRE,
            NationCharacter.forNationType("model.nationType.trade", true));
        assertEquals(NationCharacter.CONQUEROR,
            NationCharacter.forNationType("model.nationType.conquest", true));
        assertEquals(NationCharacter.BALANCE_KEEPER,
            NationCharacter.forNationType("model.nationType.cooperation", true));
        assertEquals(NationCharacter.SETTLER,
            NationCharacter.forNationType("model.nationType.immigration", true));
        for (String t : new String[] { "naval", "building", "agriculture",
                                       "furTrapping", "default" }) {
            assertNotNull(NationCharacter.forNationType("model.nationType."
                                                        + t, true));
        }
        assertEquals(NationCharacter.TRIBE,
            NationCharacter.forNationType("model.nationType.iroquois", false));
    }

    // ---- The score

    public void testPartsAddUp() {
        WorldView w = world(DUTCH, NationCharacter.TRADING_EMPIRE, 10, 500);
        other(w, FRENCH, 20, 300, Stance.WAR, 1.0);
        for (ScoredAction sa : Diplomat.rank(w)) {
            NationCharacter c = w.character;
            double sum = c.gold * sa.gold + c.trade * sa.trade
                + c.land * sa.land + c.security * sa.security
                + c.relations * sa.relations + sa.goal + sa.opinion
                - c.rho * sa.risk;
            assertEquals(sa.action.label(), sum, sa.total, 1e-9);
        }
    }

    public void testNothingScoresZeroAndIsAlwaysListed() {
        WorldView w = world(DUTCH, NationCharacter.SETTLER, 5, 100);
        List<ScoredAction> r = Diplomat.rank(w);
        assertEquals(1, r.size());
        assertEquals(Action.Kind.NOTHING, r.get(0).action.kind);
        assertEquals(0.0, r.get(0).total, 1e-9);
        assertEquals(Action.Kind.NOTHING, Diplomat.choose(r).action.kind);
    }

    public void testPeaceWhenAStrongEnemyIsNear() {
        WorldView w = world(DUTCH, NationCharacter.TRADING_EMPIRE, 4, 400);
        NationView fr = other(w, FRENCH, 30, 300, Stance.WAR, 1.0);
        fr.opinion = 0;
        List<ScoredAction> r = Diplomat.rank(w);
        assertEquals(Action.Kind.MAKE_PEACE, r.get(0).action.kind);
        assertTrue(r.get(0).total >= Diplomat.THRESHOLD);
    }

    public void testNoWarOnAStrongNeighbour() {
        WorldView w = world(DUTCH, NationCharacter.CONQUEROR, 4, 400);
        other(w, FRENCH, 30, 300, Stance.PEACE, 1.0);
        for (ScoredAction sa : Diplomat.rank(w)) {
            if (sa.action.kind == Action.Kind.DECLARE_WAR) {
                assertTrue(sa.total < 0);
            }
        }
    }

    public void testConquerorAttacksAWeakColonyInReach() {
        WorldView w = world(SPANISH, NationCharacter.CONQUEROR, 20, 300);
        w.attackers = CaptureSim.force(10, 4.5);
        NationView fr = other(w, FRENCH, 1, 100, Stance.WAR, 1.0);
        fr.places.add(place("colony:1", FRENCH, false, new double[] { 2 }, 1));
        fr.opinion = -50;
        List<ScoredAction> r = Diplomat.rank(w);
        assertEquals(Action.Kind.ATTACK_COLONY, r.get(0).action.kind);
        assertEquals(Action.Kind.ATTACK_COLONY,
                     Diplomat.choose(r).action.kind);
    }

    public void testAcrossTheSeaNeedsShips() {
        WorldView w = world(SPANISH, NationCharacter.CONQUEROR, 20, 300);
        w.attackers = CaptureSim.force(10, 4.5);
        NationView fr = other(w, FRENCH, 1, 100, Stance.WAR, 0.5);
        PlaceView far = new PlaceView("colony:9", "Far", FRENCH, 50, 50, 99,
                                      true, 3, false, new double[] { 2 }, 1);
        fr.places.add(far);
        assertNull(new Situation(w).attacks.get(FRENCH));
        w.carrierSpace = 4;
        Situation s = new Situation(w);
        assertEquals(4, s.attacks.get(FRENCH).force);
    }

    public void testSellingABoycottedSurplus() {
        WorldView w = world(DUTCH, NationCharacter.TRADING_EMPIRE, 5, 300);
        NationView en = other(w, ENGLISH, 5, 300, Stance.PEACE, 0.5);
        en.opinion = 50;
        w.goods.put("model.goods.furs", new GoodsView("model.goods.furs",
            400, 20, 0, Integer.MAX_VALUE, 5, 7, true));
        boolean found = false;
        for (ScoredAction sa : Diplomat.rank(w)) {
            if (sa.action.kind == Action.Kind.SELL_GOODS) {
                found = true;
                assertTrue(sa.gold > 0); // nothing else to do with them
                assertEquals(0.0, sa.opinion, 1e-9); // trade: no opinion term
            }
        }
        assertTrue(found);
    }

    public void testGoalsOnlyAmplify() {
        // A surplus goal does not make a one-gold sale worth making.
        WorldView w = world(DUTCH, NationCharacter.TRADING_EMPIRE, 5, 300);
        other(w, ENGLISH, 5, 300, Stance.PEACE, 0.5);
        w.goods.put("model.goods.ore", new GoodsView("model.goods.ore",
            90, 1, 10, Integer.MAX_VALUE, 1, 2, false));
        for (ScoredAction sa : Diplomat.rank(w)) {
            if (sa.action.kind == Action.Kind.SELL_GOODS) {
                assertTrue(sa.total < Diplomat.SMALL_THRESHOLD);
            }
        }
    }

    // ---- Tuning round 1 (2026-10-14)

    public void testFreedLandCounts() {
        WorldView w = world(DUTCH, NationCharacter.TRADING_EMPIRE, 20, 300);
        w.attackers = CaptureSim.force(10, 4.5);
        NationView tribe = new NationView("model.nation.iroquois", false, false);
        tribe.stance = Stance.PEACE;
        tribe.near = 1;
        tribe.army = 2;
        w.others.put(tribe.id, tribe);
        PlaceView village = place("is:1", tribe.id, true, new double[] { 1.5 }, 0);
        tribe.places.add(village);
        double before = score(w, Action.Kind.DECLARE_WAR);
        village.freed = 40;
        double after = score(w, Action.Kind.DECLARE_WAR);
        assertTrue(after > before + 0.6 * 0.9 * 40 * 0.9);
        // Trade lost with the tribe counts against.
        tribe.tradeWithMe = 4000;
        assertTrue(score(w, Action.Kind.DECLARE_WAR) < after);
    }

    public void testTribesRaidRatherThanConquer() {
        NationView tribe = new NationView("model.nation.iroquois", false, false);
        tribe.army = 8;
        tribe.near = 1;
        NationView fr = new NationView(FRENCH, true, false);
        fr.army = 8;
        fr.near = 1;
        assertEquals(8.0, Effects.threatOf(fr), 1e-9);
        assertEquals(2.0, Effects.threatOf(tribe), 1e-9);
    }

    public void testTheKingsArmyIsAReasonToArm() {
        // 12 gold a colonist, as in Mischa's game.
        WorldView w = world(DUTCH, NationCharacter.TRADING_EMPIRE, 4, 120);
        w.armable = 5;
        w.muskets = 250;
        w.kingArmy = 100;
        w.rebels = 10;
        double calm = score(w, Action.Kind.ARM);
        w.rebels = 50;
        double near = score(w, Action.Kind.ARM);
        assertTrue(near > calm);
        assertTrue(near >= Diplomat.SMALL_THRESHOLD);
        w.kingArmy = 1; // my army is bigger: no reason
        assertEquals(calm, score(w, Action.Kind.ARM), 1e-9);
    }

    public void testSmallMovesHaveALowerBar() {
        assertEquals(Diplomat.SMALL_THRESHOLD,
                     Diplomat.threshold(Action.Kind.SELL_GOODS), 1e-9);
        assertEquals(Diplomat.SMALL_THRESHOLD,
                     Diplomat.threshold(Action.Kind.ARM), 1e-9);
        assertEquals(Diplomat.THRESHOLD,
                     Diplomat.threshold(Action.Kind.DECLARE_WAR), 1e-9);
        List<ScoredAction> r = new ArrayList<>();
        ScoredAction sale = new ScoredAction(Action.sell(FRENCH, "g", 1, 1));
        sale.total = 8;
        ScoredAction none = new ScoredAction(Action.nothing());
        r.add(sale);
        r.add(none);
        assertSame(sale, Diplomat.choose(r));
    }

    public void testArmATribeNextToARival() {
        WorldView w = world(DUTCH, NationCharacter.TRADING_EMPIRE, 5, 300);
        NationView fr = other(w, FRENCH, 20, 300, Stance.PEACE, 0.5);
        fr.fear = 60;
        fr.places.add(place("colony:f", FRENCH, false, new double[] { 3 }, 1));
        NationView tribe = new NationView("model.nation.arawak", false, false);
        tribe.stance = Stance.PEACE;
        tribe.opinion = 50;
        tribe.near = 0.2;
        tribe.places.add(place("is:a", tribe.id, true, new double[] { 1.5 }, 0));
        w.others.put(tribe.id, tribe);
        w.goods.put("model.goods.muskets", new GoodsView("model.goods.muskets",
            300, 0, 0, Integer.MAX_VALUE, 3, 4, false));
        ScoredAction best = null;
        for (ScoredAction sa : Diplomat.rank(w)) {
            if (sa.action.kind == Action.Kind.ARM_TRIBE) best = sa;
        }
        assertNotNull(best);
        assertEquals(FRENCH, best.action.against);
        assertTrue(best.security > 0);
        assertEquals(0.0, best.opinion, 1e-9); // a sale: no opinion term
        // Never against an ally.
        fr.stance = Stance.ALLIANCE;
        for (ScoredAction sa : Diplomat.rank(w)) {
            assertFalse(sa.action.kind == Action.Kind.ARM_TRIBE);
        }
    }

    public void testStayingOutOfAKingsWar() {
        WorldView w = world(DUTCH, NationCharacter.TRADING_EMPIRE, 5, 300);
        NationView fr = other(w, FRENCH, 15, 300, Stance.WAR, 0.5);
        Object[] price = Effects.kingsWarPrice(w, fr);
        assertEquals(1500.0, (Double)price[0], 1e-9);
        assertEquals("pay 1500 gold", price[1]);
        // A cheap boycott is preferred.
        w.goods.put("model.goods.furs", new GoodsView("model.goods.furs",
            100, 2, 0, Integer.MAX_VALUE, 3, 4, false));
        price = Effects.kingsWarPrice(w, fr);
        assertEquals("boycott of furs", price[1]);
        assertTrue((Double)price[0] < 1500);
        fr.army = 1; // at least the minimum
        w.goods.clear();
        assertEquals((double)Effects.KING_MIN_PRICE,
                     (Double)Effects.kingsWarPrice(w, fr)[0], 1e-9);
    }

    public void testScoutForAnEnemyWeCannotPlace() {
        WorldView w = world(DUTCH, NationCharacter.CONQUEROR, 10, 300);
        NationView fr = other(w, FRENCH, 5, 300, Stance.WAR, 0.2);
        other(w, ENGLISH, 10, 300, Stance.PEACE, 0.2);
        other(w, SPANISH, 10, 300, Stance.PEACE, 0.2);
        w.me.navy = 1;
        // No sightings yet: nothing to look for.
        assertFalse(has(w, Action.Kind.SCOUT));
        fr.bearing = new Bearings.Bearing(FRENCH, 0, -1, 0.6, 20, 5);
        assertTrue(has(w, Action.Kind.SCOUT));
        assertTrue(Action.Kind.SCOUT.small());
        final double war = score(w, Action.Kind.SCOUT);
        assertTrue(war >= Diplomat.SMALL_THRESHOLD);
        // Once we know a colony there is no need.
        fr.places.add(place("colony:fr", FRENCH, false, new double[] { 2 }, 1));
        assertFalse(has(w, Action.Kind.SCOUT));
        // At peace and not the leader: not worth it.
        fr.places.clear();
        fr.stance = Stance.PEACE;
        assertFalse(has(w, Action.Kind.SCOUT));
        // No ships: we can not look.
        fr.stance = Stance.WAR;
        w.me.navy = 0;
        w.carrierSpace = 0;
        assertFalse(has(w, Action.Kind.SCOUT));
    }

    private static boolean has(WorldView w, Action.Kind kind) {
        for (ScoredAction sa : Diplomat.rank(w)) {
            if (sa.action.kind == kind) return true;
        }
        return false;
    }

    private static double score(WorldView w, Action.Kind kind) {
        for (ScoredAction sa : Diplomat.rank(w)) {
            if (sa.action.kind == kind) return sa.total;
        }
        fail("no " + kind);
        return 0;
    }

    // ---- Helpers

    private static WorldView world(String id, NationCharacter c, double army,
                                   double output) {
        NationView me = new NationView(id, true, true);
        me.army = army;
        me.output = output;
        me.colonists = 10;
        me.places.add(new PlaceView("colony:me", "Home", id, 0, 0, 1, true,
                                    5, false, new double[] { 3 }, 1));
        return new WorldView(100, me, c);
    }

    private static NationView other(WorldView w, String id, double army,
                                    double output, Stance stance,
                                    double near) {
        NationView v = new NationView(id, true, false);
        v.army = army;
        v.output = output;
        v.stance = stance;
        v.near = near;
        w.others.put(id, v);
        return v;
    }

    private static PlaceView place(String id, String owner, boolean tribe,
                                   double[] defence, double last) {
        return new PlaceView(id, id, owner, 1, 1, 1, true, 4, tribe,
                             defence, last);
    }
}
