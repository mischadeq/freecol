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
 * The treasury: gold kept back from shopping for deals.
 */
public class TreasuryTest extends TestCase {

    public void testReserve() {
        assertEquals(500, Treasury.reserve(4, NationCharacter.SETTLER, 0, false));
        assertEquals(750, Treasury.reserve(4, NationCharacter.TRADING_EMPIRE, 0, false));
        assertEquals(350, Treasury.reserve(4, NationCharacter.CONQUEROR, 0, false));
        assertEquals("capped", Treasury.MAX_BASE,
                     Treasury.reserve(100, NationCharacter.SETTLER, 0, false));
        assertEquals("promises on top", 1500,
                     Treasury.reserve(4, NationCharacter.SETTLER, 1000, false));
        assertEquals("losing a war: only promises", 1000,
                     Treasury.reserve(4, NationCharacter.SETTLER, 1000, true));
        assertEquals(1800, Treasury.forAgreement(9));
        assertEquals(200, Treasury.spendable(700, 500));
        assertEquals(0, Treasury.spendable(300, 500));
    }
}
