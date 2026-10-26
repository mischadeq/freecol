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

package net.sf.freecol.client.gui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The game and map options a player chose are saved and a new game
 * starts from them (mod, StartGamePanel.applySavedOptions): the files
 * the option dialogs write load back into the game's options, and an
 * older file without a new option leaves that option at its default.
 */
public class SavedOptionsTest extends FreeColTestCase {

    public void testMapOptionsRoundTrip() throws Exception {
        final Specification spec = spec();
        final OptionGroup mgo = spec.getMapGeneratorOptions();
        final int w = mgo.getInteger(MapGeneratorOptions.MAP_WIDTH);
        final int h = mgo.getInteger(MapGeneratorOptions.MAP_HEIGHT);
        final File f = File.createTempFile("mgo", ".xml");
        try {
            mgo.setInteger(MapGeneratorOptions.MAP_WIDTH, 75);
            mgo.setInteger(MapGeneratorOptions.MAP_HEIGHT, 150);
            assertTrue(mgo.save(f, null, true));
            mgo.setInteger(MapGeneratorOptions.MAP_WIDTH, w);
            mgo.setInteger(MapGeneratorOptions.MAP_HEIGHT, h);
            assertTrue(mgo.load(f));
            assertEquals(75, mgo.getInteger(MapGeneratorOptions.MAP_WIDTH));
            assertEquals(150, mgo.getInteger(MapGeneratorOptions.MAP_HEIGHT));
        } finally {
            mgo.setInteger(MapGeneratorOptions.MAP_WIDTH, w);
            mgo.setInteger(MapGeneratorOptions.MAP_HEIGHT, h);
            f.delete();
        }
    }

    public void testOldGameOptionsFileKeepsNewDefaults() throws Exception {
        final OptionGroup go = spec().getGameOptions();
        final File f = File.createTempFile("gameopts", ".xml");
        try {
            assertTrue(go.save(f, null, true));
            // An older file: without the Spanish Succession option.
            String xml = new String(Files.readAllBytes(f.toPath()),
                                    StandardCharsets.UTF_8);
            xml = xml.replaceAll("<booleanOption id=.model.option.spanishSuccession.[^>]*/>", "");
            assertFalse(xml.contains("spanishSuccession"));
            Files.write(f.toPath(), xml.getBytes(StandardCharsets.UTF_8));
            assertTrue(go.load(f));
            assertTrue(go.getBoolean(GameOptions.SPANISH_SUCCESSION));
        } finally {
            f.delete();
        }
    }
}
