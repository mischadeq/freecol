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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;


/**
 * Land a European nation got from a tribe (step 8.3, mod): bought (or
 * given freely) or taken without paying.  Only land taken counts as
 * encroaching; a colony that just shares a border with a tribe does
 * not.  Kept per game, per tribe; the tribe's computer player saves it
 * with the game.
 *
 * Land got before the record began is not in it: such a tile counts as
 * taken when the colony uses it and it lies within a village's own
 * land (its radius).
 */
public final class LandClaims {

    /** A claim: taken without paying, or bought (or free). */
    public static final char TAKEN = 'S', BOUGHT = 'B';

    /** Game to tribe nation to tile id to "S:nation" or "B:nation". */
    private static final Map<Game, Map<String, Map<String, String>>> byGame
        = new WeakHashMap<>();

    private LandClaims() {}


    private static synchronized Map<String, String> of(Game game,
                                                       String tribe) {
        return byGame.computeIfAbsent(game, g -> new HashMap<>())
            .computeIfAbsent(tribe, t -> new LinkedHashMap<>());
    }

    /**
     * Note that a European got a tile from a tribe.
     *
     * @param tribe The tribe that owned it.
     * @param tile The {@code Tile}.
     * @param claimant The European.
     * @param taken True if taken without paying.
     */
    public static synchronized void record(Player tribe, Tile tile,
                                           Player claimant, boolean taken) {
        if (tribe == null || tile == null || claimant == null
            || !tribe.isIndian() || !claimant.isEuropean()) return;
        of(tribe.getGame(), tribe.getNationId()).put(tile.getId(),
            ((taken) ? TAKEN : BOUGHT) + ":" + claimant.getNationId());
    }

    /**
     * Forget a tile (given back, or the record is no longer true).
     *
     * @param tribe The tribe.
     * @param tile The {@code Tile}.
     */
    public static synchronized void forget(Player tribe, Tile tile) {
        if (tribe == null || tile == null) return;
        of(tribe.getGame(), tribe.getNationId()).remove(tile.getId());
    }

    /**
     * Was a tile taken from a tribe by a nation, as far as the tribe
     * knows?  Recorded as taken, or (from before the record) used by
     * the nation and within one of the tribe's villages' own land.
     *
     * @param tribe The tribe.
     * @param tile The {@code Tile}.
     * @param by The European.
     * @return True if so.
     */
    public static synchronized boolean taken(Player tribe, Tile tile,
                                             Player by) {
        if (tribe == null || tile == null || by == null) return false;
        if (tile.getOwner() != by) return false; // not theirs (any more)
        final String rec = of(tribe.getGame(), tribe.getNationId())
            .get(tile.getId());
        if (rec != null) {
            return rec.charAt(0) == TAKEN
                && rec.substring(2).equals(by.getNationId());
        }
        // From before the record: inside a village's own land.
        for (IndianSettlement is : tribe.getIndianSettlementList()) {
            if (is.getTile() != null
                && is.getTile().getDistanceTo(tile) <= is.getRadius()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The tiles a nation took from a tribe and still holds.
     *
     * @param tribe The tribe.
     * @param by The European.
     * @return The tiles.
     */
    public static List<Tile> takenTiles(Player tribe, Player by) {
        final List<Tile> ret = new ArrayList<>();
        if (tribe == null || by == null) return ret;
        final Game game = tribe.getGame();
        final java.util.Set<Tile> seen = new java.util.HashSet<>();
        // Recorded tiles.
        final List<String> ids;
        synchronized (LandClaims.class) {
            ids = new ArrayList<>(of(game, tribe.getNationId()).keySet());
        }
        for (String id : ids) {
            final Tile t = game.getFreeColGameObject(id, Tile.class);
            if (t != null && seen.add(t) && taken(tribe, t, by)) ret.add(t);
        }
        // From before the record: around the villages.
        for (IndianSettlement is : tribe.getIndianSettlementList()) {
            if (is.getTile() == null) continue;
            for (Tile t : is.getTile().getSurroundingTiles(0, is.getRadius())) {
                if (seen.add(t) && taken(tribe, t, by)) ret.add(t);
            }
        }
        return ret;
    }


    // Saved with the tribe's computer player: "tile=S:nation;..."

    /**
     * The record of one tribe, as text.
     *
     * @param tribe The tribe.
     * @return The text (empty if nothing is recorded).
     */
    public static synchronized String serialize(Player tribe) {
        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e
                 : of(tribe.getGame(), tribe.getNationId()).entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * Read back the record of one tribe.
     *
     * @param tribe The tribe.
     * @param s The text from {@link #serialize}, may be null.
     */
    public static synchronized void load(Player tribe, String s) {
        if (tribe == null) return;
        final Map<String, String> m = of(tribe.getGame(), tribe.getNationId());
        m.clear();
        if (s == null || s.isEmpty()) return;
        for (String rec : s.split(";")) {
            final int i = rec.indexOf('=');
            if (i <= 0 || rec.length() < i + 4) continue;
            final String v = rec.substring(i + 1);
            if ((v.charAt(0) == TAKEN || v.charAt(0) == BOUGHT)
                && v.charAt(1) == ':') {
                m.put(rec.substring(0, i), v);
            }
        }
    }
}
