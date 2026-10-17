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

import junit.framework.TestCase;


/**
 * How a nation judges the player's envoy (step 8).
 */
public class EnvoyRulesTest extends TestCase {

    private static EnvoyRules.Input peace(int value, int gold) {
        EnvoyRules.Input in = new EnvoyRules.Input();
        in.kind = Offer.Kind.ENVOY_PEACE;
        in.value = value;
        in.gold = gold;
        in.trust = 50;
        in.opinion = 0;
        return in;
    }

    public void testWait() {
        assertEquals(0, EnvoyRules.waitLeft(-1, 100));
        assertEquals(5, EnvoyRules.waitLeft(100, 100));
        assertEquals(1, EnvoyRules.waitLeft(100, 104));
        assertEquals(0, EnvoyRules.waitLeft(100, 105));
    }

    public void testYesWhenWorthIt() {
        assertEquals(EnvoyRules.Verdict.ACCEPT,
                     EnvoyRules.judge(peace(200, 0)).verdict);
        // Gold makes up the difference.
        assertEquals(EnvoyRules.Verdict.ACCEPT,
                     EnvoyRules.judge(peace(-300, 300)).verdict);
    }

    public void testPriceWhenTrusted() {
        EnvoyRules.Answer a = EnvoyRules.judge(peace(-420, 100));
        assertEquals(EnvoyRules.Verdict.COUNTER, a.verdict);
        assertEquals("the whole price, rounded up", 450, a.price);
    }

    public void testNoWhenDistrusted() {
        EnvoyRules.Input in = peace(-420, 0);
        in.trust = 20;
        EnvoyRules.Answer a = EnvoyRules.judge(in);
        assertEquals(EnvoyRules.Verdict.REFUSE, a.verdict);
        assertEquals(EnvoyRules.Reason.TRUST, a.reason);
        // An enemy is disliked: for peace that alone does not stop
        // bargaining, for an alliance it does.
        in.trust = 60;
        in.opinion = -80;
        assertEquals(EnvoyRules.Verdict.COUNTER, EnvoyRules.judge(in).verdict);
        in.kind = Offer.Kind.ENVOY_ALLIANCE;
        assertEquals(EnvoyRules.Reason.TRUST, EnvoyRules.judge(in).reason);
    }

    public void testNoWhenTooDear() {
        assertEquals(EnvoyRules.Reason.NO_INTEREST,
                     EnvoyRules.judge(peace(-9000, 0)).reason);
    }

    public void testNotYetAfterOwnWar() {
        EnvoyRules.Input in = peace(500, 1000);
        in.committed = true;
        assertEquals(EnvoyRules.Reason.NOT_YET, EnvoyRules.judge(in).reason);
    }

    public void testAllianceNever() {
        EnvoyRules.Input in = peace(DealValue.NEVER, 5000);
        in.kind = Offer.Kind.ENVOY_ALLIANCE;
        assertEquals(EnvoyRules.Reason.NEVER, EnvoyRules.judge(in).reason);
        in.trust = 10;
        assertEquals(EnvoyRules.Reason.TRUST, EnvoyRules.judge(in).reason);
    }

    public void testGiftAlwaysAndFits() {
        EnvoyRules.Input in = peace(DealValue.NEVER, 50);
        in.kind = Offer.Kind.ENVOY_GIFT;
        in.trust = 0;
        assertEquals(EnvoyRules.Verdict.ACCEPT, EnvoyRules.judge(in).verdict);
        assertEquals(10, EnvoyRules.giftTension(100));
        EnvoyRules.Input off = peace(500, 0);
        off.fits = false;
        assertEquals(EnvoyRules.Reason.NOT_NOW, EnvoyRules.judge(off).reason);
    }

    public void testRoundUp() {
        assertEquals(0, EnvoyRules.roundUp(0));
        assertEquals(50, EnvoyRules.roundUp(1));
        assertEquals(100, EnvoyRules.roundUp(100));
        assertEquals(150, EnvoyRules.roundUp(101));
    }
}
