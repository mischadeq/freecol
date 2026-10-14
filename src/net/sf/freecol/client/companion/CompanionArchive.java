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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.server.FreeColServer;


/**
 * The game turn by turn, as the player saw it: for every turn the
 * empire figures, the settlements and who owned which land.  Recorded
 * each turn while the companion page runs, and filled in once, in the
 * background, from the game's old autosaves, so the charts and the
 * expansion replay go back to the start.
 *
 * Honest: from a save only what the player had seen then (his view of
 * each tile, the Foreign Affairs report).
 */
final class CompanionArchive {

    private static final Logger logger = Logger.getLogger(CompanionArchive.class.getName());

    /** One turn. */
    static final class Frame {
        final int turn;
        /** Settlements: x, y, owner index, size, colony (1) or not (0). */
        final List<int[]> settlements = new ArrayList<>();
        /** Owner index per tile (row-major), -1 none; null if not kept. */
        int[] owners;
        /** Nation id to figures (CompanionNations.MEASURES). */
        final Map<String, int[]> measures = new LinkedHashMap<>();

        Frame(int turn) {
            this.turn = turn;
        }
    }

    /** Nations by index (ids), shared by all frames of a game. */
    private final List<String> nations = new ArrayList<>();
    private final TreeMap<Integer, Frame> frames = new TreeMap<>();
    private int width, height;
    private final String key;
    private final File file;

    /** Saves read so far and to read, for the page. */
    private volatile int done, todo;
    private volatile boolean busy;

    private static CompanionArchive current;


    CompanionArchive(String key, File file) {
        this.key = key;
        this.file = file;
    }

    /**
     * The archive of a game, loaded from its file the first time.
     *
     * @param game The client {@code Game}.
     * @param me The client {@code Player}.
     * @return The {@code CompanionArchive}.
     */
    static synchronized CompanionArchive of(Game game, Player me) {
        final String key = game.getUUID() + "/" + me.getId();
        if (current == null || !current.key.equals(key)) {
            current = new CompanionArchive(key, file(game, me));
            current.load();
        }
        return current;
    }

    private static File file(Game game, Player me) {
        final File base = FreeColDirectories.getUserDataDirectory();
        if (base == null) return null;
        final File dir = new File(base, "companion");
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        return new File(dir, "expansion-" + game.getUUID() + "-"
            + me.getId().replace(':', '_') + ".txt");
    }


    // Taking frames

    /**
     * A frame of a game as a player sees it.
     *
     * @param game The {@code Game}.
     * @param me The player.
     * @param saved True for a game read from a save (use the player's
     *     view of each tile), false for the client's game.
     * @param summaries Nation id to its Foreign Affairs summary (others
     *     are made from the game, as the server does).
     * @return The {@code Frame}.
     */
    Frame frame(Game game, Player me, boolean saved,
                             Map<String, NationSummary> summaries) {
        final net.sf.freecol.common.model.Map map = game.getMap();
        final int width = map.getWidth(), height = map.getHeight();
        synchronized (this) {
            this.width = width;
            this.height = height;
        }
        Frame f = new Frame(game.getTurn().getNumber());
        f.owners = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final Tile t = map.getTile(x, y);
                final Tile v = (t == null) ? null
                    : (saved) ? t.getPlayerView(me) : t;
                int o = -1;
                if (v != null && v.getOwner() != null
                    && (v.getOwner().isEuropean() || v.getOwner().isIndian())
                    && !v.getOwner().isREF()) {
                    o = index(v.getOwner().getNationId());
                }
                f.owners[y * width + x] = o;
            }
        }
        final List<Settlement> seen = CompanionNations.seen(game, me, saved);
        for (Settlement s : seen) {
            if (s.getOwner() == null || s.getTile() == null) continue;
            f.settlements.add(new int[] { s.getTile().getX(),
                s.getTile().getY(), index(s.getOwner().getNationId()),
                (s instanceof Colony) ? ((Colony)s).getApparentUnitCount()
                                      : 0,
                (s instanceof Colony) ? 1 : 0 });
        }
        for (Player p : game.getLiveEuropeanPlayerList()) {
            if (p.isREF()) continue;
            NationSummary ns = (summaries == null) ? null
                : summaries.get(p.getNationId());
            if (ns == null && (saved || p == me)) ns = new NationSummary(p, me);
            f.measures.put(p.getNationId(), CompanionNations.measures(me, p,
                ns, seen, new ArrayList<>()));
        }
        return f;
    }

    private synchronized int index(String nationId) {
        int i = nations.indexOf(nationId);
        if (i < 0) {
            nations.add(nationId);
            i = nations.size() - 1;
        }
        return i;
    }

    /**
     * Keep a frame (replacing one of the same turn) and save.
     *
     * @param f The {@code Frame}.
     */
    synchronized void put(Frame f) {
        frames.put(f.turn, f);
        save();
    }

    /** Reading old saves now, and how far: {done, todo}, or null. */
    int[] progress() {
        return (busy) ? new int[] { done, todo } : null;
    }

    synchronized boolean has(int turn) {
        return frames.containsKey(turn);
    }

    synchronized int size() {
        return frames.size();
    }

    /** Turn to nation id to figures, for the empire charts. */
    synchronized Map<Integer, Map<String, int[]>> measures() {
        Map<Integer, Map<String, int[]>> ret = new TreeMap<>();
        for (Frame f : frames.values()) {
            if (!f.measures.isEmpty()) ret.put(f.turn, f.measures);
        }
        return ret;
    }


    // Reading old saves

    private static final Pattern TURN = Pattern.compile("<game [^>]*?turn=\"(\\d+)\"");
    private static final Pattern UUID = Pattern.compile("<game [^>]*?UUID=\"([^\"]+)\"");

    /**
     * Read the game's autosaves the archive does not have yet, in a
     * background thread (once per game).
     *
     * @param game The client {@code Game}.
     * @param me The client {@code Player}.
     * @param prefix The autosave file prefix ("Autosave").
     */
    void fillFromSaves(Game game, Player me, String prefix) {
        if (busy) return;
        final File dir = FreeColDirectories.getAutosaveDirectory();
        if (dir == null) return;
        final String uuid = game.getUUID().toString();
        final String start = prefix + "-"
            + Integer.toHexString(game.getUUID().hashCode()) + "_";
        final String nation = me.getNationId();
        busy = true;
        Thread t = new Thread(() -> {
            try {
                readSaves(dir, start, uuid, nation);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Reading old saves failed", e);
            } finally {
                busy = false;
            }
        }, "CompanionArchive");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    void readSaves(File dir, String start, String uuid,
                           String nation) {
        File[] files = dir.listFiles((d, n) -> n.startsWith(start)
                                     && n.endsWith(".fsg"));
        if (files == null) return;
        Map<Integer, File> byTurn = new TreeMap<>();
        for (File f : files) {
            try {
                final String head = head(f);
                Matcher m = TURN.matcher(head), u = UUID.matcher(head);
                if (!m.find() || !u.find() || !uuid.equals(u.group(1))) continue;
                final int turn = Integer.parseInt(m.group(1));
                if (!has(turn)) byTurn.putIfAbsent(turn, f);
            } catch (IOException | RuntimeException e) {
                logger.log(Level.FINE, "Skipping " + f, e);
            }
        }
        todo = byTurn.size();
        done = 0;
        for (Map.Entry<Integer, File> e : byTurn.entrySet()) {
            try {
                final Game g = FreeColServer.readGame(
                    new FreeColSavegameFile(e.getValue()), null, null);
                final Player me = g.getPlayerByNationId(nation);
                if (me != null && g.getMap() != null) put(frame(g, me, true, null));
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Could not read " + e.getValue(), ex);
            }
            done++;
        }
    }

    private static String head(File f) throws IOException {
        try (ZipFile z = new ZipFile(f)) {
            ZipEntry ze = z.getEntry("savegame.xml");
            if (ze == null) return "";
            try (InputStream in = z.getInputStream(ze)) {
                return new String(in.readNBytes(4000), StandardCharsets.UTF_8);
            }
        }
    }


    // The page

    /**
     * The archive as JSON for the expansion replay.
     *
     * @param game The client {@code Game} (names and colours).
     * @return The JSON.
     */
    synchronized String json(Game game) {
        Json j = new Json().obj().put("w", width).put("h", height)
            .put("reading", busy).put("done", done).put("todo", todo);
        j.key("nations").arr();
        for (String id : nations) {
            final Player p = game.getPlayerByNationId(id);
            j.obj().put("id", id)
                .put("name", (p == null) ? id : Messages.message(p.getNationLabel()))
                .put("color", (p == null) ? java.awt.Color.GRAY : p.getNationColor())
                .put("european", p != null && p.isEuropean())
                .end();
        }
        j.endArr();
        j.key("frames").arr();
        int[] last = null;
        for (Frame f : frames.values()) {
            j.obj().put("turn", f.turn)
                .put("label", Messages.message(new net.sf.freecol.common.model.Turn(f.turn).getLabel()));
            j.key("s").arr();
            for (int[] s : f.settlements) {
                j.arr();
                for (int x : s) j.val(x);
                j.endArr();
            }
            j.endArr();
            // Land as changes from the frame before: [index, owner].
            j.key("d").arr();
            if (f.owners != null && f.owners.length == width * height) {
                for (int i = 0; i < f.owners.length; i++) {
                    final int before = (last == null) ? -1 : last[i];
                    if (f.owners[i] != before) {
                        j.val(i).val(f.owners[i]);
                    }
                }
                last = f.owners;
            }
            j.endArr();
            j.key("c").obj();
            for (Map.Entry<String, int[]> m : f.measures.entrySet()) {
                j.put(m.getKey(), Math.max(0, m.getValue()[0]));
            }
            j.end();
            j.end();
        }
        j.endArr();
        return j.end().toString();
    }


    // Saving: one line per item.
    // N id                     a nation (index = order)
    // W width height
    // T turn                   a frame starts
    // S x y owner size colony
    // O owner start length     a run of owned tiles
    // M nation v0,v1,...       figures

    private void save() {
        if (file == null) return;
        try {
            Files.write(file.toPath(), text().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Could not write " + file, ioe);
        }
    }

    /** The archive as the lines of its file. */
    synchronized String text() {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("W ").append(width).append(' ').append(height).append('\n');
        for (String n : nations) sb.append("N ").append(n).append('\n');
        for (Frame f : frames.values()) {
            sb.append("T ").append(f.turn).append('\n');
            for (int[] s : f.settlements) {
                sb.append('S');
                for (int x : s) sb.append(' ').append(x);
                sb.append('\n');
            }
            if (f.owners != null) {
                int i = 0;
                while (i < f.owners.length) {
                    final int o = f.owners[i];
                    int k = i + 1;
                    while (k < f.owners.length && f.owners[k] == o) k++;
                    if (o >= 0) {
                        sb.append("O ").append(o).append(' ').append(i)
                            .append(' ').append(k - i).append('\n');
                    }
                    i = k;
                }
            }
            for (Map.Entry<String, int[]> m : f.measures.entrySet()) {
                sb.append("M ").append(m.getKey()).append(' ');
                int[] v = m.getValue();
                for (int k = 0; k < v.length; k++) {
                    if (k > 0) sb.append(',');
                    sb.append(v[k]);
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private synchronized void load() {
        if (file == null || !file.isFile()) return;
        try {
            parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Could not read " + file, ioe);
        }
    }

    /** Read the lines written by {@link #save}; bad lines are skipped. */
    synchronized void parse(List<String> lines) {
        Frame f = null;
        for (String line : lines) {
            final String[] p = line.split(" ");
            try {
                switch (p[0]) {
                case "W":
                    width = Integer.parseInt(p[1]);
                    height = Integer.parseInt(p[2]);
                    break;
                case "N":
                    nations.add(p[1]);
                    break;
                case "T":
                    f = new Frame(Integer.parseInt(p[1]));
                    f.owners = new int[width * height];
                    java.util.Arrays.fill(f.owners, -1);
                    frames.put(f.turn, f);
                    break;
                case "S":
                    if (f != null && p.length == 6) {
                        f.settlements.add(new int[] { Integer.parseInt(p[1]),
                            Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                            Integer.parseInt(p[4]), Integer.parseInt(p[5]) });
                    }
                    break;
                case "O":
                    if (f != null && p.length == 4) {
                        final int o = Integer.parseInt(p[1]);
                        final int s = Integer.parseInt(p[2]);
                        final int n = Integer.parseInt(p[3]);
                        for (int i = s; i < s + n && i < f.owners.length; i++) {
                            f.owners[i] = o;
                        }
                    }
                    break;
                case "M":
                    if (f != null && p.length == 3) {
                        String[] v = p[2].split(",");
                        int[] m = new int[CompanionNations.MEASURES.length];
                        java.util.Arrays.fill(m, -1);
                        for (int i = 0; i < v.length && i < m.length; i++) {
                            m[i] = Integer.parseInt(v[i]);
                        }
                        f.measures.put(p[1], m);
                    }
                    break;
                default:
                    break;
                }
            } catch (RuntimeException e) {} // skip a bad line
        }
    }
}
