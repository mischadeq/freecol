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

import java.util.HashMap;
import java.util.Map;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * Notes, once a turn, what one nation can see of the others: their
 * colonies and settlements in sight, their units on land and their
 * ships (with a course when the same ship was seen the turn before).
 * Only what is in sight now, so it is honest; the same code serves the
 * computer players, the replay and the companion page (it reads the
 * tiles in sight, so it also works on a client, which only knows the
 * foreign units it can see).
 */
public final class Sightings {

    /** Most sightings per nation and kind noted in one turn. */
    public static final int PER_TURN = 4;

    private Sightings() {}


    /**
     * Note what a nation can see this turn.
     *
     * @param game The {@code Game}.
     * @param me The nation looking.
     * @param into Where to note it.
     */
    public static void look(Game game, Player me, Bearings into) {
        final int turn = game.getTurn().getNumber();
        final net.sf.freecol.common.model.Map map = game.getMap();
        if (map == null) return;
        final Map<String, int[]> counts = new HashMap<>();
        final Tile origin = origin(me);
        for (Tile t : map.getTileList(me::canSee)) {
            final Settlement s = t.getSettlement();
            if (s != null && counts(s.getOwner(), me)
                && take(counts, s.getOwner().getNationId(), 0)) {
                into.add(new Bearings.Sighting(s.getOwner().getNationId(),
                    t.getX(), t.getY(), turn, Bearings.Kind.COLONY));
            }
            for (Unit u : t.getUnitList()) {
                final Player p = u.getOwner();
                if (!counts(p, me) || !me.canSeeUnit(u)) continue;
                final String id = p.getNationId();
                final boolean naval = u.isNaval();
                if (take(counts, id, (naval) ? 1 : 3)) {
                    into.add(new Bearings.Sighting(id, t.getX(), t.getY(),
                        turn, (naval) ? Bearings.Kind.SHIP : Bearings.Kind.LAND));
                }
                int[] last = into.lastSeen.put(u.getId(),
                    new int[] { t.getX(), t.getY(), turn });
                if (last == null || turn - last[2] > Bearings.COURSE_GAP
                    || last[2] >= turn
                    || (last[0] == t.getX() && last[1] == t.getY())
                    || !take(counts, id, 2)) continue;
                final Boolean home = fromHome(map, origin, naval,
                    last[0], last[1], t.getX(), t.getY());
                if (home != null) {
                    into.addCourse(id, last[0], last[1], t.getX(), t.getY(),
                        turn, home, map.getWidth(), map.getHeight());
                }
            }
        }
        into.forgetOld(turn);
    }

    /**
     * Does a unit seen moving from (x0, y0) to (x1, y1) come from home
     * (true), go home (false), or tell us nothing (null)?
     *
     * A ship sailing mostly east or west, towards the open sea at the
     * side of the map where ships leave for Europe, comes from home; one
     * sailing in from it goes home.  But a ship sailing straight at us,
     * straight away from us, or seen at our door, is visiting us (to trade or to fight),
     * which says nothing about where it lives.
     * Anything else (a ship along the coast, a unit on land) that comes
     * nearer to us has come from home to visit us.  A unit on land that
     * moves away is going back; a ship moving away along the coast may
     * be sailing on to anyone.
     *
     * @param map The {@code Map}.
     * @param origin Our colony, or null.
     * @param naval True for a ship.
     * @param x0 Previous x.
     * @param y0 Previous y.
     * @param x1 Current x.
     * @param y1 Current y.
     * @return True if home lies behind the unit, false if ahead, null
     *     if the move says nothing.
     */
    static Boolean fromHome(net.sf.freecol.common.model.Map map, Tile origin,
                            boolean naval, int x0, int y0, int x1, int y1) {
        final double vx = Math.abs(x1 - x0), vy = Math.abs(y1 - y0) / 2.0;
        if (naval && vx >= vy) {
            if (origin != null
                && (near(origin, x0, y0) || near(origin, x1, y1)
                    || passesNear(origin, x1, y1, x1 - x0, y1 - y0)
                    || passesNear(origin, x0, y0, x0 - x1, y0 - y1))) {
                return null;
            }
            return outward(map, x0, y0, x1, y1);
        }
        if (origin == null) return null;
        final int ox = origin.getX(), oy = origin.getY();
        final double before = Math.hypot(x0 - ox, (y0 - oy) / 2.0);
        final double after = Math.hypot(x1 - ox, (y1 - oy) / 2.0);
        if (after < before) return true;
        // A ship moving away along the coast may be going anywhere.
        return (naval) ? null : Boolean.FALSE;
    }

    /** Is (x, y) within {@link Bearings#NEAR} tiles of our colony? */
    static boolean near(Tile origin, int x, int y) {
        return Math.hypot(x - origin.getX(), (y - origin.getY()) / 2.0)
            <= Bearings.NEAR;
    }

    /**
     * Does a line from (x, y) going (dx, dy) pass near our colony
     * (ahead, within {@link Bearings#NEAR} tiles)?
     */
    static boolean passesNear(Tile origin, int x, int y, int dx, int dy) {
        final double px = origin.getX() - x, py = (origin.getY() - y) / 2.0;
        final double vx = dx, vy = dy / 2.0;
        final double len = Math.hypot(vx, vy);
        if (len == 0) return false;
        final double along = (px * vx + py * vy) / len;
        if (along < 0) return false; // behind
        final double off = Math.abs(px * vy - py * vx) / len;
        return off <= Bearings.NEAR;
    }

    /**
     * Does a move go out towards the open sea?  The open sea (where ships
     * sail for Europe) lies along the east and west edges of the map,
     * so a ship getting nearer to its closest side edge is going out.
     */
    static boolean outward(net.sf.freecol.common.model.Map map,
                           int x0, int y0, int x1, int y1) {
        final int w = map.getWidth();
        final int before = Math.min(x0, w - 1 - x0);
        final int after = Math.min(x1, w - 1 - x1);
        return after < before;
    }

    /** Is this a nation worth guessing about? */
    private static boolean counts(Player p, Player me) {
        return p != null && p != me && !p.isREF() && !p.isUnknownEnemy()
            && !p.isUndead();
    }

    private static boolean take(Map<String, int[]> counts, String id, int kind) {
        int[] c = counts.computeIfAbsent(id, k -> new int[4]);
        return c[kind]++ < PER_TURN;
    }

    /**
     * Where to draw bearings from: the nation's biggest colony.
     *
     * @param me The nation.
     * @return The colony's tile, or null if it has none.
     */
    public static Tile origin(Player me) {
        Colony best = null;
        for (Colony c : me.getColonyList()) {
            if (best == null || c.getUnitCount() > best.getUnitCount()) best = c;
        }
        return (best == null) ? null : best.getTile();
    }
}
