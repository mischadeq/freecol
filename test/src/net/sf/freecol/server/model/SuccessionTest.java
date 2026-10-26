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

package net.sf.freecol.server.model;

import net.sf.freecol.common.diplomacy.SuccessionRules;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * The War of Spanish Succession, closer to Utrecht (mod): the loser
 * cedes only its colonies nearest the winner, small ones are
 * abandoned, a tenth of the people of the others sail home, the loser
 * stays in the game.
 */
public class SuccessionTest extends FreeColTestCase {

    /** A random source with fixed rolls. */
    private static final class Fixed extends java.util.Random {
        private final double[] v;
        private int i = 0;
        Fixed(double... v) { this.v = v; }
        @Override
        public double nextDouble() { return v[i++ % v.length]; }
    }

    private static final TileType plains = spec().getTileType("model.tile.plains");

    private ServerGame game;
    private ServerPlayer french, english;

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    private Colony colony(ServerPlayer p, int x, int y, int size) {
        return FreeColTestUtils.getColonyBuilder().player(p)
            .colonyTile(game.getMap().getTile(x, y)).initialColonists(size)
            .build();
    }

    private void setUpGame() {
        MapBuilder mb = new MapBuilder(getGame());
        mb.setDimensions(40, 40).setBaseTileType(plains);
        game = ServerTestHelper.startServerGame(mb.build());
        french = getServerPlayer(game, "model.nation.french");
        english = getServerPlayer(game, "model.nation.english");
        french.setStance(english, Stance.WAR);
        english.setStance(french, Stance.WAR);
        colony(english, 4, 10, 4);
    }

    public void testRules() {
        assertEquals(0, SuccessionRules.ceded(1));
        assertEquals(1, SuccessionRules.ceded(2));
        assertEquals(2, SuccessionRules.ceded(5));
        assertEquals(3, SuccessionRules.ceded(7));
        assertTrue(SuccessionRules.abandoned(2));
        assertFalse(SuccessionRules.abandoned(3));
        // Each colonist leaves with a one in ten chance; one stays.
        final java.util.Random r = new java.util.Random(7);
        int sum = 0, max = 0, zero = 0;
        for (int i = 0; i < 2000; i++) {
            final int n = SuccessionRules.leaving(4, r);
            sum += n;
            max = Math.max(max, n);
            if (n == 0) zero++;
        }
        assertEquals("about 0.4 of 4 on average", 0.4, sum / 2000.0, 0.06);
        assertTrue("sometimes several leave", max >= 2);
        assertTrue("never all", max <= 3);
        assertTrue("often nobody", zero > 1000);
        assertEquals(3, SuccessionRules.leaving(4, new Fixed(0.0)));
        assertEquals(2, SuccessionRules.leaving(2, r)); // abandoned
        // The stronger more likely wins, never sure.
        assertEquals(0.5, SuccessionRules.winChance(10, 10), 0.001);
        assertEquals(0.5, SuccessionRules.winChance(0, 0), 0.001);
        assertTrue(SuccessionRules.winChance(30, 10) > 0.7);
        assertTrue(SuccessionRules.winChance(30, 10) < 1.0);
        assertTrue(SuccessionRules.winChance(0, 100) > 0);
        String[] f = SuccessionRules.parse(SuccessionRules.pending(
            "model.nation.french", "model.nation.english", 12));
        assertEquals("model.nation.french", f[0]);
        assertEquals("12", f[2]);
        assertNull(SuccessionRules.parse("x|y"));
        assertNull(SuccessionRules.parse(null));
    }

    public void testTreaty() {
        setUpGame();
        final Colony near = colony(french, 8, 10, 6);
        final Colony small = colony(french, 8, 16, 2);
        colony(french, 26, 10, 4);
        colony(french, 28, 22, 4);
        colony(french, 30, 4, 4);
        near.modifyLiberty(400);
        final Unit guard = new ServerUnit(game, near.getTile(), french,
            spec().getUnitType("model.unit.veteranSoldier"));
        final int inEurope = french.getEurope().getUnitCount();
        final String nearName = near.getName(), smallName = small.getName();

        // The first colonist rolls under one in ten, the others not.
        final Succession s = new Succession(french, english,
                                            new Fixed(0.05, 0.5, 0.5, 0.5, 0.5, 0.5));
        s.csTreaty(new ChangeSet(), new LogBuilder(0));

        assertEquals("the nearest one ceded", 1, s.ceded.size());
        assertEquals(nearName, s.ceded.get(0));
        assertEquals("the small one abandoned", smallName, s.abandoned.get(0));
        assertEquals(english, near.getOwner());
        assertEquals("one sails home", 5, near.getUnitCount());
        assertEquals("their bells were for France", 0, near.getLiberty());
        assertEquals(3, french.getColonyList().size());
        assertFalse("France stays in the game", french.isDead());
        assertEquals(french, guard.getOwner());
        assertTrue("the garrison sails home", guard.isInEurope());
        assertEquals("the leaver, the garrison, the 2 settlers",
                     inEurope + 4, french.getEurope().getUnitCount());
        assertEquals(Stance.PEACE, french.getStance(english));
    }

    /** Announced first; the treaty comes later, either may win. */
    public void testWarThenTreaty() {
        setUpGame();
        colony(english, 4, 16, 4);
        colony(french, 8, 10, 6);
        colony(french, 26, 10, 4);
        final int now = game.getTurn().getNumber();
        game.setSuccessionPending(SuccessionRules.pending(
            french.getNationId(), english.getNationId(), now + 2));
        ServerTestHelper.newTurn();
        assertEquals("not yet", 2, french.getColonyList().size());
        assertEquals(2, english.getColonyList().size());
        assertFalse(game.getSpanishSuccession());
        game.setTurn(new net.sf.freecol.common.model.Turn(now + 2));
        ServerTestHelper.newTurn();
        assertTrue(game.getSpanishSuccession());
        assertNull(game.getSuccessionPending());
        final int f = french.getColonyList().size();
        final int e = english.getColonyList().size();
        assertEquals("one colony changed hands", 4, f + e);
        assertTrue(f + " / " + e, (f == 1 && e == 3) || (f == 3 && e == 1));
    }
}
