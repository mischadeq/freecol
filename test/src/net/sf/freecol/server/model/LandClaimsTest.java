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

import java.util.Random;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * Encroaching means taking a tribe's land (step 8.3): land taken
 * without paying angers the tribe, a colony that only shares a border
 * with it does not.
 */
public class LandClaimsTest extends FreeColTestCase {

    private Game game;
    private ServerPlayer dutch, tribe;
    private IndianSettlement camp;

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    private void setUpGame() {
        game = ServerTestHelper.startServerGame(getTestMap(true));
        dutch = getServerPlayer(game, "model.nation.dutch");
        camp = new FreeColTestCase.IndianSettlementBuilder(game).build();
        tribe = (ServerPlayer)camp.getOwner();
        tribe.setStance(dutch, Stance.PEACE);
        dutch.setStance(tribe, Stance.PEACE);
    }

    /** A land tile at an exact distance from the camp. */
    private Tile at(int distance) {
        for (Tile t : game.getMap().getTileList(x -> true)) {
            if (t.isLand() && t.getDistanceTo(camp.getTile()) == distance
                && t.getX() > 2 && t.getY() > 2
                && t.getX() < game.getMap().getWidth() - 3
                && t.getY() < game.getMap().getHeight() - 3) return t;
        }
        fail("no tile at " + distance);
        return null;
    }

    private Colony colonyAt(Tile t) {
        return FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(t).initialColonists(6).build();
    }

    private int alarmAfterTurn() {
        camp.setAlarm(dutch, new Tension(0));
        tribe.csStartTurn(new Random(1), new ChangeSet());
        final Tension t = camp.getAlarm(dutch);
        return (t == null) ? 0 : t.getValue();
    }

    public void testRecordAndSave() {
        setUpGame();
        final Tile a = at(4), b = at(5);
        a.changeOwnership(dutch, null);
        b.changeOwnership(dutch, null);
        LandClaims.record(tribe, a, dutch, true);
        LandClaims.record(tribe, b, dutch, false);
        assertTrue("taken without paying", LandClaims.taken(tribe, a, dutch));
        assertFalse("bought", LandClaims.taken(tribe, b, dutch));
        final String s = LandClaims.serialize(tribe);
        LandClaims.load(tribe, "");
        assertFalse(LandClaims.taken(tribe, a, dutch));
        LandClaims.load(tribe, s);
        assertTrue("saved and read back", LandClaims.taken(tribe, a, dutch));
        LandClaims.forget(tribe, a);
        assertFalse(LandClaims.taken(tribe, a, dutch));
    }

    public void testOldLandInsideAVillageCounts() {
        setUpGame();
        final Tile inside = at(1), outside = at(3);
        inside.changeOwnership(dutch, null);
        outside.changeOwnership(dutch, null);
        assertTrue("unrecorded, inside the village's land",
                   LandClaims.taken(tribe, inside, dutch));
        assertFalse("unrecorded, outside it",
                    LandClaims.taken(tribe, outside, dutch));
        assertTrue(LandClaims.takenTiles(tribe, dutch).contains(inside));
        assertFalse(LandClaims.takenTiles(tribe, dutch).contains(outside));
    }

    public void testBorderColonyDoesNotAnger() {
        setUpGame();
        colonyAt(at(3)); // shares a border, on land never theirs
        assertEquals("a neighbour is no encroacher", 0, alarmAfterTurn());
    }

    public void testTakenLandAngers() {
        setUpGame();
        final Colony c = colonyAt(at(3));
        // Its land was taken from the tribe without paying.
        for (Tile t : c.getOwnedTiles()) LandClaims.record(tribe, t, dutch, true);
        assertTrue(alarmAfterTurn() > 0);
    }

    public void testClaimIsRecorded() {
        setUpGame();
        final Tile t = at(2);
        t.changeOwnership(tribe, camp);
        dutch.csClaimLand(t, null, -1, new ChangeSet());
        assertTrue("stolen", LandClaims.taken(tribe, t, dutch));
        final Tile u = at(4);
        u.changeOwnership(tribe, camp);
        dutch.csClaimLand(u, null, 0, new ChangeSet());
        assertFalse("given freely", LandClaims.taken(tribe, u, dutch));
    }
}
