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

package net.sf.freecol.client.companion;

import java.util.Arrays;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * The archive of turns for the long charts and the expansion replay:
 * only what the player saw, kept and read back.
 */
public class ArchiveTest extends FreeColTestCase {

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    public void testFrameIsHonestAndSurvivesTheFile() {
        Game game = ServerTestHelper.startServerGame(getTestMap(false));
        Map map = game.getMap();
        ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        ServerPlayer french = getServerPlayer(game, "model.nation.french");
        Colony ours = FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(3, 3)).initialColonists(2).build();
        Colony theirs = FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(map.getTile(12, 8)).initialColonists(3).build();
        dutch.exploreTile(ours.getTile());

        CompanionArchive a = new CompanionArchive("test", null);
        // A saved game keeps every player's view: the French colony
        // is not there until the Dutch have seen its tile.
        CompanionArchive.Frame f = a.frame(game, dutch, true, null);
        assertEquals(1, f.settlements.size());
        assertEquals(3, f.settlements.get(0)[0]);
        assertEquals(1, f.settlements.get(0)[4]); // a colony
        final int w = map.getWidth();
        assertEquals(-1, f.owners[8 * w + 12]);
        assertTrue(f.owners[3 * w + 3] >= 0);
        assertEquals(1, f.measures.get("model.nation.dutch")[0]);

        dutch.exploreTile(theirs.getTile());
        f = a.frame(game, dutch, true, null);
        assertEquals(2, f.settlements.size());
        a.put(f);

        // Written and read back the same.
        CompanionArchive b = new CompanionArchive("test", null);
        b.parse(Arrays.asList(a.text().split("\n")));
        assertEquals(1, b.size());
        assertEquals(a.text(), b.text());
        assertTrue(b.has(f.turn));
        assertEquals(1, b.measures().size());
        // The page gets the land as changes and the colonies per nation.
        String json = b.json(game);
        assertTrue(json, json.contains("\"frames\":[{\"turn\":" + f.turn));
        assertTrue(json, json.contains("\"model.nation.french\":1"));
        // Only the turns after one, for a page that has the rest.
        CompanionArchive.Frame later = a.frame(game, dutch, true, null);
        game.setTurn(new net.sf.freecol.common.model.Turn(f.turn + 1));
        later = a.frame(game, dutch, true, null);
        a.put(later);
        String part = a.json(game, f.turn);
        assertTrue(part, part.contains("\"count\":2"));
        assertTrue(part, part.contains("\"frames\":[{\"turn\":" + (f.turn + 1)));
        assertFalse(part, part.contains("\"turn\":" + f.turn + ","));
        // Same land as before: no changes to send.
        assertTrue(part, part.contains("\"d\":[]"));
    }

    public void testReadsOldSaves() throws Exception {
        Game game = ServerTestHelper.startServerGame(getTestMap(true));
        ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(game.getMap().getTile(5, 8)).initialColonists(2).build();
        java.io.File dir = java.nio.file.Files.createTempDirectory("autosave").toFile();
        dir.deleteOnExit();
        final String start = "Autosave-" + Integer.toHexString(game.getUUID().hashCode()) + "_";
        java.io.File save = new java.io.File(dir, start + "Dutch_1500.fsg");
        save.deleteOnExit();
        ServerTestHelper.getServer().saveGame(save, null, null);
        // Another game's save in the same folder is left alone.
        java.io.File other = new java.io.File(dir, "Autosave-0_Dutch_1500.fsg");
        other.deleteOnExit();
        java.nio.file.Files.copy(save.toPath(), other.toPath());

        CompanionArchive a = new CompanionArchive("test", null);
        a.readSaves(dir, start, game.getUUID().toString(), "model.nation.dutch");
        assertEquals(1, a.size());
        assertTrue(a.has(game.getTurn().getNumber()));
        assertEquals(1, a.measures().get(game.getTurn().getNumber())
                     .get("model.nation.dutch")[0]);
        // Read once: a second pass finds nothing new.
        a.readSaves(dir, start, game.getUUID().toString(), "model.nation.dutch");
        assertEquals(1, a.size());
    }
}
