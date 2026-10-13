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

package net.sf.freecol.client.control;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;


/**
 * What each trade route actually moved, turn by turn.
 *
 * Every time a carrier on a trade route unloads goods at a stop, the
 * amount is noted here under the route and the turn.  The history is
 * kept for the last {@link #KEEP_TURNS} turns and written to a small
 * file next to the companion page's price history
 * ({@code companion/routes-<game>-<player>.csv}), so it survives
 * quitting the game.
 *
 * Going back in time (loading an older save, Restart This Turn) drops
 * what was noted for that turn and later, as it will happen again.
 */
public final class RouteHistory {

    private static final Logger logger = Logger.getLogger(RouteHistory.class.getName());

    /** How many turns of history to keep. */
    public static final int KEEP_TURNS = 20;

    /** Route id -> turn -> goods type id -> amount unloaded. */
    private final Map<String, TreeMap<Integer, Map<String, Integer>>> moved
        = new HashMap<>();

    /** The file the history is kept in, or null to keep it in memory. */
    private File file = null;

    /** The game and player the history belongs to (a reload of the
        same game gives a new {@code Game} object). */
    private Game game = null;
    private Player player = null;

    /** The highest turn seen. */
    private int lastTurn = -1;


    // The history itself (unit tested)

    /**
     * Note goods unloaded by a carrier on a route.
     *
     * @param routeId The trade route id.
     * @param turn The turn number.
     * @param goodsId The goods type id.
     * @param amount The amount unloaded.
     */
    public synchronized void record(String routeId, int turn, String goodsId,
                                    int amount) {
        if (routeId == null || goodsId == null || amount <= 0) return;
        this.moved.computeIfAbsent(routeId, k -> new TreeMap<>())
            .computeIfAbsent(turn, k -> new LinkedHashMap<>())
            .merge(goodsId, amount, Integer::sum);
        if (turn > this.lastTurn) this.lastTurn = turn;
        prune(turn);
    }

    /**
     * A game was loaded at a turn: forget that turn and later, as they
     * will be played again.
     *
     * @param turn The turn the game was loaded at.
     * @return True if anything was forgotten.
     */
    public synchronized boolean loaded(int turn) {
        this.lastTurn = turn;
        return forgetFrom(turn);
    }

    /**
     * Forget everything from a turn on.
     *
     * @param turn The first turn to forget.
     * @return True if anything was forgotten.
     */
    public synchronized boolean forgetFrom(int turn) {
        boolean changed = false;
        for (TreeMap<Integer, Map<String, Integer>> byTurn : this.moved.values()) {
            Map<Integer, Map<String, Integer>> tail = byTurn.tailMap(turn, true);
            if (!tail.isEmpty()) {
                tail.clear();
                changed = true;
            }
        }
        this.moved.values().removeIf(TreeMap::isEmpty);
        return changed;
    }

    /** Drop turns that are too old to show. */
    private void prune(int turn) {
        for (TreeMap<Integer, Map<String, Integer>> byTurn : this.moved.values()) {
            byTurn.headMap(turn - KEEP_TURNS, true).clear();
        }
        this.moved.values().removeIf(TreeMap::isEmpty);
    }

    /**
     * The total amount a route moved per turn.
     *
     * @param routeId The trade route id.
     * @param lastTurn The last turn to include.
     * @param turns How many turns to return.
     * @return The totals, oldest turn first.
     */
    public synchronized int[] totals(String routeId, int lastTurn, int turns) {
        int[] ret = new int[turns];
        TreeMap<Integer, Map<String, Integer>> byTurn = this.moved.get(routeId);
        if (byTurn == null) return ret;
        for (int i = 0; i < turns; i++) {
            Map<String, Integer> m = byTurn.get(lastTurn - turns + 1 + i);
            if (m == null) continue;
            for (int a : m.values()) ret[i] += a;
        }
        return ret;
    }

    /**
     * What a route moved in one turn, by goods type.
     *
     * @param routeId The trade route id.
     * @param turn The turn.
     * @return Goods type id -> amount, empty if nothing.
     */
    public synchronized Map<String, Integer> byGoods(String routeId, int turn) {
        TreeMap<Integer, Map<String, Integer>> byTurn = this.moved.get(routeId);
        Map<String, Integer> m = (byTurn == null) ? null : byTurn.get(turn);
        return (m == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
    }

    /**
     * What a route moved over several turns, by goods type.
     *
     * @param routeId The trade route id.
     * @param lastTurn The last turn to include.
     * @param turns How many turns to include.
     * @return Goods type id -> amount, largest first.
     */
    public synchronized Map<String, Integer> sumByGoods(String routeId,
                                                       int lastTurn, int turns) {
        Map<String, Integer> sum = new HashMap<>();
        TreeMap<Integer, Map<String, Integer>> byTurn = this.moved.get(routeId);
        if (byTurn != null) {
            for (Map<String, Integer> m
                     : byTurn.subMap(lastTurn - turns, false, lastTurn, true).values()) {
                for (Map.Entry<String, Integer> e : m.entrySet()) {
                    sum.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
        }
        Map<String, Integer> ret = new LinkedHashMap<>();
        sum.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> ret.put(e.getKey(), e.getValue()));
        return ret;
    }

    /**
     * The history as lines of "route,turn,goods,amount".
     *
     * @return The text.
     */
    public synchronized String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, TreeMap<Integer, Map<String, Integer>>> r
                 : new TreeMap<>(this.moved).entrySet()) {
            for (Map.Entry<Integer, Map<String, Integer>> t : r.getValue().entrySet()) {
                for (Map.Entry<String, Integer> g : t.getValue().entrySet()) {
                    sb.append(r.getKey()).append(',').append(t.getKey())
                        .append(',').append(g.getKey()).append(',')
                        .append(g.getValue()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /**
     * Read lines written by {@link #serialize}, adding to this history.
     *
     * @param text The text.
     */
    public synchronized void parse(String text) {
        for (String line : text.split("\n")) {
            String[] p = line.trim().split(",");
            if (p.length != 4) continue;
            try {
                record(p[0], Integer.parseInt(p[1]), p[2], Integer.parseInt(p[3]));
            } catch (NumberFormatException nfe) {} // Skip a bad line
        }
    }


    // Keeping it on disk

    /**
     * Make sure the history belongs to this game and player, loading
     * it from disk when the game changed, and note the turn.
     *
     * @param game The {@code Game}.
     * @param player The client {@code Player}.
     */
    public synchronized void attach(Game game, Player player) {
        if (game == null || player == null || game.getTurn() == null) return;
        final int turn = game.getTurn().getNumber();
        if (game == this.game && player == this.player) {
            this.lastTurn = Math.max(this.lastTurn, turn);
            return;
        }
        // A game was (re)loaded: read what is on disk and forget this
        // turn and later, as those turns will be played again.
        this.game = game;
        this.player = player;
        this.moved.clear();
        this.lastTurn = -1;
        this.file = file(game, player);
        if (this.file != null && this.file.isFile()) {
            try {
                parse(Files.readString(this.file.toPath(),
                                       StandardCharsets.UTF_8));
            } catch (IOException ioe) {
                logger.warning("Could not read " + this.file + ": " + ioe);
            }
        }
        if (loaded(turn)) save();
    }

    /** Write the history to its file, if it has one. */
    public synchronized void save() {
        if (this.file == null) return;
        try {
            Files.write(this.file.toPath(),
                        serialize().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            logger.warning("Could not write " + this.file + ": " + ioe);
        }
    }

    private static File file(Game game, Player player) {
        final File base = FreeColDirectories.getUserDataDirectory();
        if (base == null) return null;
        final File dir = new File(base, "companion");
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        return new File(dir, "routes-" + game.getUUID() + "-"
            + player.getId().replace(':', '_') + ".csv");
    }
}
