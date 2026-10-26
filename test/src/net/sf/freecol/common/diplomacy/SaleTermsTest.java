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

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;


/**
 * What a nation sells another at a colony, and for how much.
 */
public class SaleTermsTest extends TestCase {

    private static SaleTerms.Input input(int stock) {
        SaleTerms.Input in = new SaleTerms.Input();
        in.goodsId = "model.goods.cloth";
        in.stock = stock;
        in.europePrice = 10;
        in.tax = 20;
        in.tripTurns = 6;
        in.rebels = 0;
        in.opinion = 0;
        in.character = NationCharacter.SETTLER;
        return in;
    }

    /** Amounts come in tens, never above what can be spared. */
    public void testAmountInTens() {
        SaleTerms.Input in = input(237);
        SaleTerms.Line l = SaleTerms.line(in);
        assertEquals(SaleTerms.Status.FOR_SALE, l.status);
        assertEquals(0, l.max % SaleTerms.STEP);
        assertTrue(l.max > 0 && l.max <= 237);
    }

    /** Fewer than ten in store: out of stock. */
    public void testOutOfStock() {
        assertEquals(SaleTerms.Status.OUT_OF_STOCK,
                     SaleTerms.line(input(7)).status);
        assertEquals(0, SaleTerms.line(input(7)).max);
    }

    /** The colony keeps what it uses for a few turns. */
    public void testKeepsOwnNeeds() {
        SaleTerms.Input in = input(60);
        in.net = -12; // uses 12 a turn: keeps 60
        assertEquals(SaleTerms.Status.NOT_FOR_SALE, SaleTerms.line(in).status);
    }

    /** A trader lets more go than a conqueror. */
    public void testTradersSellMore() {
        SaleTerms.Input t = input(300), c = input(300);
        t.character = NationCharacter.TRADING_EMPIRE;
        c.character = NationCharacter.CONQUEROR;
        assertTrue(SaleTerms.line(t).max > SaleTerms.line(c).max);
    }

    /** Friends get more, a disliked buyer nothing. */
    public void testOpinionDecidesAmount() {
        SaleTerms.Input friend = input(300), cool = input(300),
            foe = input(300);
        friend.opinion = 60;
        cool.opinion = -20;
        foe.opinion = -50;
        assertTrue(SaleTerms.line(friend).max > SaleTerms.line(cool).max);
        assertEquals(SaleTerms.Status.NOT_FOR_SALE,
                     SaleTerms.line(foe).status);
    }

    /** Weapons: kept back, more at war, none to someone feared. */
    public void testWeapons() {
        SaleTerms.Input peace = input(200), war = input(200),
            feared = input(200);
        peace.military = war.military = feared.military = true;
        war.atWar = true;
        feared.fear = 70;
        assertTrue(SaleTerms.line(peace).max > SaleTerms.line(war).max);
        assertEquals(SaleTerms.Status.NOT_FOR_SALE,
                     SaleTerms.line(feared).status);
        // Weapons go less freely than cloth.
        assertTrue(SaleTerms.line(peace).max < SaleTerms.line(input(200)).max);
    }

    /** The trip to Europe and rebels make selling here worth less. */
    public void testPriceBelowEuropeForTripAndRebels() {
        SaleTerms.Input near = input(100), far = input(100),
            rebels = input(100);
        near.tripTurns = 0;
        far.tripTurns = 8;
        rebels.tripTurns = 0;
        rebels.rebels = 100;
        assertTrue(SaleTerms.unitPrice(far) < SaleTerms.unitPrice(near));
        assertTrue(SaleTerms.unitPrice(rebels) < SaleTerms.unitPrice(near));
        // With no trip: Europe after tax plus the margin.
        assertEquals(10 * 0.8 * 1.15, SaleTerms.unitPrice(near), 1e-9);
    }

    /** A friend pays less, a foe more; traders take a smaller margin. */
    public void testPriceByFeeling() {
        SaleTerms.Input friend = input(100), foe = input(100),
            trader = input(100);
        friend.opinion = 100;
        foe.opinion = -100;
        trader.character = NationCharacter.TRADING_EMPIRE;
        final double neutral = SaleTerms.unitPrice(input(100));
        assertTrue(SaleTerms.unitPrice(friend) < neutral);
        assertTrue(SaleTerms.unitPrice(foe) > neutral);
        assertTrue(SaleTerms.unitPrice(trader) < neutral);
    }

    /** A deal on the terms is accepted, more or cheaper is not. */
    public void testAccepts() {
        SaleTerms.Line l = new SaleTerms.Line("model.goods.cloth",
            SaleTerms.Status.FOR_SALE, 50, 2.5);
        assertEquals(75, l.priceFor(30));
        assertTrue(l.accepts(30, 75));
        assertFalse(l.accepts(30, 74));
        assertFalse(l.accepts(60, 1000));
        SaleTerms.Line no = new SaleTerms.Line("model.goods.cloth",
            SaleTerms.Status.NOT_FOR_SALE, 50, 2.5);
        assertFalse(no.accepts(10, 1000));
        assertEquals(0, no.max);
    }

    /** Haggling eats into the margin: small asks likely, past it never. */
    public void testHaggleChance() {
        SaleTerms.Input in = input(100);
        final double room = SaleTerms.room(in);
        assertEquals(100 * 0.15 / 1.15, room, 1e-9);
        final double p5 = SaleTerms.haggleChance(in, 0, 5);
        final double p10 = SaleTerms.haggleChance(in, 0, 10);
        assertTrue(p5 > p10 && p10 > 0);
        assertEquals("past the margin", 0.0,
                     SaleTerms.haggleChance(in, 0, 15), 1e-9);
        assertTrue("less after a first discount",
                   SaleTerms.haggleChance(in, 5, 5) < p5);
        SaleTerms.Input trader = input(100), friend = input(100),
            conqueror = input(100);
        trader.character = NationCharacter.TRADING_EMPIRE;
        conqueror.character = NationCharacter.CONQUEROR;
        friend.opinion = 100;
        assertTrue("traders bargain gladly",
                   SaleTerms.haggleChance(trader, 0, 5)
                   > SaleTerms.haggleChance(conqueror, 0, 5));
        assertTrue("a friend still has room", SaleTerms.room(friend) > 0);
        assertEquals(0.0, SaleTerms.haggleChance(in, 0, 0), 1e-9);
    }

    /** A haggled line is cheaper and remembers the discount. */
    public void testHaggledLine() {
        SaleTerms.Line l = new SaleTerms.Line("model.goods.cloth",
            SaleTerms.Status.FOR_SALE, 50, 2.0);
        SaleTerms.Line h = l.haggled(10, true);
        assertEquals(1.8, h.unitPrice, 1e-9);
        assertEquals(10, h.off);
        assertTrue(h.closed);
        SaleTerms.Line back = SaleTerms.parse(SaleTerms.serialize(
            java.util.Collections.singletonList(h))).get(0);
        assertEquals(10, back.off);
        assertTrue(back.closed);
        assertEquals(1.8, back.unitPrice, 1e-9);
    }

    /** The terms travel to the buyer as text and back. */
    public void testSerialize() {
        List<SaleTerms.Line> lines = Arrays.asList(
            new SaleTerms.Line("model.goods.cloth", SaleTerms.Status.FOR_SALE,
                               40, 2.345),
            new SaleTerms.Line("model.goods.muskets",
                               SaleTerms.Status.OUT_OF_STOCK, 0, 3));
        List<SaleTerms.Line> back
            = SaleTerms.parse(SaleTerms.serialize(lines));
        assertEquals(2, back.size());
        SaleTerms.Line c = SaleTerms.find(back, "model.goods.cloth");
        assertEquals(40, c.max);
        assertEquals(2.345, c.unitPrice, 1e-9);
        assertEquals(SaleTerms.Status.OUT_OF_STOCK,
                     SaleTerms.find(back, "model.goods.muskets").status);
        assertTrue(SaleTerms.parse("").isEmpty());
        assertTrue(SaleTerms.parse("bad;x,y").isEmpty());
    }
}
