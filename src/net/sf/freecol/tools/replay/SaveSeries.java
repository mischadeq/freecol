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

package net.sf.freecol.tools.replay;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.model.ServerGame;


/**
 * The autosaves of one game, in turn order.  Each file is only read:
 * the game is built the same read-only way the map editor loads a map
 * (no server, no computer players), and nothing is ever written.
 */
public final class SaveSeries {

    /** One save: its file and the turn inside it. */
    public static final class Entry {
        public final File file;
        public final int turn;

        Entry(File file, int turn) {
            this.file = file;
            this.turn = turn;
        }
    }

    private static final Pattern TURN = Pattern.compile("<game [^>]*?turn=\"(\\d+)\"");
    private static final Pattern UUID = Pattern.compile("<game [^>]*?UUID=\"([^\"]+)\"");

    private SaveSeries() {}


    /**
     * Find the saves of one game in a folder, in turn order.  Two saves
     * of the same turn: the first found is kept.
     *
     * @param dir The folder.
     * @param prefix Only files whose name starts with this (e.g.
     *     "Autosave-6c7a41f5_"), or null for all .fsg files.
     * @return The saves.
     * @exception IOException if a file can not be read.
     */
    public static List<Entry> find(File dir, String prefix)
        throws IOException {
        List<Entry> ret = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return ret;
        String uuid = null;
        for (File f : files) {
            String n = f.getName();
            if (!n.endsWith(".fsg")) continue;
            if (prefix != null && !n.startsWith(prefix)) continue;
            String head = head(f);
            Matcher m = TURN.matcher(head), u = UUID.matcher(head);
            if (!m.find()) continue;
            String id = (u.find()) ? u.group(1) : null;
            if (uuid == null) uuid = id;
            else if (id != null && !id.equals(uuid)) continue; // other game
            ret.add(new Entry(f, Integer.parseInt(m.group(1))));
        }
        ret.sort(Comparator.comparingInt((Entry e) -> e.turn)
                 .thenComparing(e -> e.file.getName()));
        List<Entry> unique = new ArrayList<>();
        for (Entry e : ret) {
            if (unique.isEmpty() || unique.get(unique.size() - 1).turn != e.turn) {
                unique.add(e);
            }
        }
        return unique;
    }

    /**
     * Turn number from a save file name: "1493" is turn 2, "1600_1_Spring"
     * turn 109, "1669_1_Spring" turn 247 (one turn a year until 1600,
     * then spring and autumn).
     *
     * @param name The file name.
     * @return The turn, or -1 if the name has no year.
     */
    public static int turnFromName(String name) {
        Matcher m = Pattern.compile("_(\\d{4})(?:_(\\d)_[A-Za-z]+)?\\.fsg$")
            .matcher(name);
        if (!m.find()) return -1;
        int year = Integer.parseInt(m.group(1));
        if (year < 1600) return year - 1491;
        int season = (m.group(2) == null) ? 1 : Integer.parseInt(m.group(2));
        return 109 + 2 * (year - 1600) + (season - 1);
    }

    /**
     * Load a save.
     *
     * @param e The {@code Entry}.
     * @return The game.
     * @exception Exception if it can not be read.
     */
    public static ServerGame load(Entry e) throws Exception {
        return FreeColServer.readGame(new FreeColSavegameFile(e.file),
                                      null, null);
    }

    private static String head(File f) throws IOException {
        try (ZipFile z = new ZipFile(f)) {
            ZipEntry ze = z.getEntry("savegame.xml");
            if (ze == null) return "";
            try (InputStream in = z.getInputStream(ze)) {
                byte[] b = in.readNBytes(4000);
                return new String(b, StandardCharsets.UTF_8);
            }
        }
    }
}
