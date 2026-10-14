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
import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.Bearings;
import net.sf.freecol.common.diplomacy.Sightings;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;


/**
 * Where the other nations live, as the player could work it out (mod):
 * once a turn the colonies, soldiers and ships of others in sight are
 * noted, the same way the computer players do it (see
 * {@link Sightings}).  For a nation whose colonies the player has seen,
 * the page points at those instead.  Kept in
 * {@code companion/bearings-<game>-<player>.txt}.
 */
public final class CompanionBearings {

    private static final Logger logger = Logger.getLogger(CompanionBearings.class.getName());

    /** What one nation's card shows. */
    public static final class Where {
        /** True if we have seen their colonies. */
        public final boolean known;
        public final double dx, dy, distance, confidence;
        public final int sightings;
        public final String compass;

        Where(boolean known, double dx, double dy, double distance,
              double confidence, int sightings) {
            this.known = known;
            this.dx = dx;
            this.dy = dy;
            this.distance = distance;
            this.confidence = confidence;
            this.sightings = sightings;
            this.compass = Bearings.compass(dx, dy);
        }
    }

    private static Bearings bearings = new Bearings();
    private static int lastTurn = -1;
    private static Game gameObj = null;
    private static Player playerObj = null;
    private static File file = null;


    private CompanionBearings() {}

    /**
     * Note what is in sight, once a turn.  Cheap to call often.
     *
     * @param game The {@code Game}.
     * @param me The client {@code Player}.
     */
    public static synchronized void scan(Game game, Player me) {
        if (game == null || me == null || game.getTurn() == null
            || game.getMap() == null) return;
        attach(game, me);
        final int turn = game.getTurn().getNumber();
        if (turn == lastTurn) return;
        lastTurn = turn;
        Sightings.look(game, me, bearings);
        save();
    }

    /**
     * Where a nation lives, as far as the player knows.
     *
     * @param me The client {@code Player}.
     * @param p The other {@code Player}.
     * @return A {@code Where}, or null if there is no idea at all.
     */
    public static synchronized Where where(Player me, Player p) {
        final Tile o = Sightings.origin(me);
        if (o == null) return null;
        double sx = 0, sy = 0;
        int n = 0;
        for (Settlement s : p.getSettlementList()) {
            final Tile t = s.getTile();
            if (t == null || !t.isExploredBy(me)) continue;
            sx += t.getX();
            sy += t.getY();
            n++;
        }
        if (n > 0) {
            final double vx = sx / n - o.getX(), vy = (sy / n - o.getY()) / 2.0;
            final double d = Math.hypot(vx, vy);
            if (d >= 1) return new Where(true, vx / d, vy / d, d, 1, n);
        }
        if (game() == null) return null;
        final Bearings.Bearing b = bearings.bearing(p.getNationId(),
            o.getX(), o.getY(), game().getTurn().getNumber());
        return (b == null) ? null
            : new Where(false, b.dx, b.dy, b.distance, b.confidence, b.sightings);
    }

    private static Game game() {
        return gameObj;
    }

    /** Forget everything (for tests and a new game). */
    static synchronized void clear() {
        bearings = new Bearings();
        lastTurn = -1;
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
        file = new File(dir, "bearings-" + game.getUUID() + "-"
            + me.getId().replace(':', '_') + ".txt");
        if (!file.isFile()) return;
        try {
            bearings = Bearings.parse(new String(Files.readAllBytes(file.toPath()),
                                                 StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            logger.warning("Could not read " + file + ": " + ioe);
        }
        // A (re)load replays this turn: note it again.
        bearings.forget(game.getTurn().getNumber());
    }

    private static void save() {
        if (file == null) return;
        try {
            Files.write(file.toPath(),
                        bearings.serialize().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            logger.warning("Could not write " + file + ": " + ioe);
        }
    }
}
