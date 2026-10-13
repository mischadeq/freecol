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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;


/**
 * Sea fights between the player and other European nations, turn by
 * turn, taken from the game's own combat messages: their ships the
 * player damaged or sank, and the player's ships they damaged or sank.
 * This is what the player knows of a blockade.  Kept in
 * {@code companion/raids-<game>-<player>.csv}.
 */
public final class CompanionRaids {

    private static final Logger logger = Logger.getLogger(CompanionRaids.class.getName());

    /** Kinds of fight, in the order they are stored. */
    public static final int THEIRS_DAMAGED = 0, THEIRS_SUNK = 1,
        OURS_DAMAGED = 2, OURS_SUNK = 3;

    /** Turn -> nation id -> counts. */
    private static final TreeMap<Integer, Map<String, int[]>> history
        = new TreeMap<>();

    /** Messages already counted (text and turn). */
    private static final Set<String> counted = new HashSet<>();

    /** The game the history belongs to; a reload gives a new object. */
    private static Game gameObj = null;
    private static Player playerObj = null;
    private static File file = null;


    private CompanionRaids() {}

    /**
     * Which kind of fight a message reports, or -1.
     *
     * @param messageId The message id.
     * @return The kind, or -1 if not a sea fight with a nation.
     */
    public static int kindOf(String messageId) {
        if (messageId == null) return -1;
        switch (messageId) {
        case "combat.shipDamaged.enemy":
        case "combat.shipDamagedByBombardment.enemy":
            return THEIRS_DAMAGED;
        case "combat.shipSunk.enemy":
        case "combat.shipSunkByBombardment.enemy":
            return THEIRS_SUNK;
        case "combat.shipDamaged.ours":
        case "combat.shipDamagedByBombardment.ours":
            return OURS_DAMAGED;
        case "combat.shipSunk.ours":
        case "combat.shipSunkByBombardment.ours":
            return OURS_SUNK;
        default:
            return -1;
        }
    }

    /**
     * Count a fight.
     *
     * @param turn The turn.
     * @param nationId The other nation.
     * @param kind The kind of fight.
     * @param key Something unique to the message, so it counts once.
     * @return True if it was new.
     */
    static synchronized boolean count(int turn, String nationId, int kind,
                                      String key) {
        if (kind < 0 || nationId == null) return false;
        if (!counted.add(turn + "|" + key)) return false;
        history.computeIfAbsent(turn, t -> new TreeMap<>())
            .computeIfAbsent(nationId, n -> new int[4])[kind]++;
        return true;
    }

    /**
     * Look through the player's messages for sea fights not yet
     * counted.  Cheap; call it often.
     *
     * @param game The {@code Game}.
     * @param me The client {@code Player}.
     */
    public static synchronized void scan(Game game, Player me) {
        if (game == null || me == null || game.getTurn() == null) return;
        attach(game, me);
        final int turn = game.getTurn().getNumber();
        boolean changed = false;
        for (ModelMessage m : me.getModelMessages()) {
            final int kind = kindOf(m.getId());
            if (kind < 0) continue;
            final StringTemplate nt = m.getReplacement("%enemyNation%");
            if (nt == null) continue;
            final String nationName = Messages.message(nt);
            String nationId = null;
            for (Player p : game.getLiveEuropeanPlayerList()) {
                if (p == me) continue;
                if (Messages.message(p.getNationLabel()).equals(nationName)) {
                    nationId = p.getNationId();
                    break;
                }
            }
            String text;
            try {
                text = Messages.message(m);
            } catch (RuntimeException re) {
                text = m.getId() + nationName;
            }
            changed |= count(turn, nationId, kind, text);
        }
        if (changed) save();
    }

    /**
     * Totals per nation over the last turns.
     *
     * @param lastTurn The current turn.
     * @param turns How many turns.
     * @return Nation id -> counts.
     */
    public static synchronized Map<String, int[]> totals(int lastTurn,
                                                         int turns) {
        Map<String, int[]> ret = new TreeMap<>();
        for (Map<String, int[]> m
                 : history.subMap(lastTurn - turns, false, lastTurn, true).values()) {
            for (Map.Entry<String, int[]> e : m.entrySet()) {
                int[] t = ret.computeIfAbsent(e.getKey(), k -> new int[4]);
                for (int i = 0; i < 4; i++) t[i] += e.getValue()[i];
            }
        }
        return ret;
    }

    /** Forget everything (for tests and a new game). */
    static synchronized void clear() {
        history.clear();
        counted.clear();
    }

    private static void attach(Game game, Player me) {
        if (game == gameObj && me == playerObj) return;
        gameObj = game;
        playerObj = me;
        clear();
        file = null;
        final File base = FreeColDirectories.getUserDataDirectory();
        if (base == null) return;
        final File dir = new File(base, "companion");
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        file = new File(dir, "raids-" + game.getUUID() + "-"
            + me.getId().replace(':', '_') + ".csv");
        if (!file.isFile()) return;
        try {
            for (String line : Files.readAllLines(file.toPath(),
                                                  StandardCharsets.UTF_8)) {
                String[] p = line.split(",");
                if (p.length != 6) continue;
                try {
                    int[] v = new int[4];
                    for (int i = 0; i < 4; i++) v[i] = Integer.parseInt(p[2 + i]);
                    history.computeIfAbsent(Integer.parseInt(p[0]),
                                            t -> new TreeMap<>()).put(p[1], v);
                } catch (NumberFormatException nfe) {} // Skip
            }
        } catch (IOException ioe) {
            logger.warning("Could not read " + file + ": " + ioe);
        }
        // A (re)load replays this turn: forget it, its messages are
        // counted again as they come.
        history.tailMap(game.getTurn().getNumber(), true).clear();
    }

    private static void save() {
        if (file == null) return;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Map<String, int[]>> e : history.entrySet()) {
            for (Map.Entry<String, int[]> n : e.getValue().entrySet()) {
                sb.append(e.getKey()).append(',').append(n.getKey());
                for (int v : n.getValue()) sb.append(',').append(v);
                sb.append('\n');
            }
        }
        try {
            Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            logger.warning("Could not write " + file + ": " + ioe);
        }
    }
}
