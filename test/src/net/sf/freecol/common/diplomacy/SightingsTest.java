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

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/** Noting what a nation can see, on a small map. */
public class SightingsTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");
    private static final UnitType colonist = spec().getUnitType("model.unit.freeColonist");


    private static Map map(Game game) {
        MapBuilder mb = new MapBuilder(game);
        mb.setDimensions(40, 60).setBaseTileType(plains);
        Map map = mb.build();
        game.changeMap(map);
        return map;
    }

    public void testWhereAUnitComesFrom() {
        Game game = getStandardGame();
        Map map = map(game);
        Tile us = map.getTile(20, 30);
        // A ship sailing for the open sea at the east edge, far from us,
        // comes from home; one sailing in from it goes home.
        assertEquals(Boolean.TRUE, Sightings.fromHome(map, us, true, 30, 10, 31, 10));
        assertEquals(Boolean.FALSE, Sightings.fromHome(map, us, true, 31, 10, 30, 10));
        // A ship sailing straight at us, or seen at our door, is a visitor.
        assertNull(Sightings.fromHome(map, us, true, 30, 30, 29, 30));
        assertNull(Sightings.fromHome(map, us, true, 21, 30, 22, 30));
        // On land, or along the coast: coming nearer means from home.
        assertEquals(Boolean.TRUE, Sightings.fromHome(map, us, false, 26, 30, 25, 30));
        assertEquals(Boolean.FALSE, Sightings.fromHome(map, us, false, 25, 30, 26, 30));
        assertEquals(Boolean.TRUE, Sightings.fromHome(map, us, true, 20, 44, 20, 42));
        // A ship sailing away along the coast may be going anywhere.
        assertNull(Sightings.fromHome(map, us, true, 20, 40, 20, 42));
        // Without a colony there is no "towards us".
        assertNull(Sightings.fromHome(map, null, false, 25, 30, 26, 30));
    }

    public void testOnlyWhatIsInSight() {
        Game game = getStandardGame();
        Map map = map(game);
        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        Player french = game.getPlayerByNationId("model.nation.french");
        Colony home = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(10, 20)).build();
        assertEquals(home.getTile(), Sightings.origin(dutch));

        // A French colonist two tiles east of us, and one far away.
        Unit near = new ServerUnit(game, map.getTile(12, 20), french, colonist);
        new ServerUnit(game, map.getTile(35, 50), french, colonist);
        dutch.invalidateCanSeeTiles();
        assertTrue(dutch.canSeeUnit(near));

        game.setTurn(new Turn(10));
        Bearings b = new Bearings();
        Sightings.look(game, dutch, b);
        assertEquals("only the colonist in sight", 1, b.size());
        Bearings.Bearing g = b.bearing("model.nation.french", 10, 20, 10);
        assertEquals("east", g.compass());

        // Next turn it walks nearer: it came from the east.
        near.setLocation(map.getTile(11, 20));
        dutch.invalidateCanSeeTiles();
        game.setTurn(new Turn(11));
        Sightings.look(game, dutch, b);
        assertTrue(b.serialize().contains(",COURSE"));
        assertEquals("east", b.bearing("model.nation.french", 10, 20, 11).compass());
    }
}
