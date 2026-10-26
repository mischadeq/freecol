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

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.sf.freecol.common.diplomacy.CreditRecord;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;


/**
 * Every nation's public credit record (mod, ship loans), per game.
 * Public on purpose, like the tribes' news: every nation and the
 * player see it.  Each computer nation saves its own record with its
 * AI player (only computer nations borrow).
 */
public final class CreditRecords {

    private static final Map<Game, Map<String, CreditRecord>> byGame
        = new WeakHashMap<>();

    private CreditRecords() {}

    /**
     * A nation's record (made empty if there is none yet).
     *
     * @param game The {@code Game}.
     * @param nation The nation id.
     * @return The live {@code CreditRecord}.
     */
    public static synchronized CreditRecord of(Game game, String nation) {
        return byGame.computeIfAbsent(game, g -> new HashMap<>())
            .computeIfAbsent(nation, n -> new CreditRecord());
    }

    /** A player's record. */
    public static CreditRecord of(Player p) {
        return of(p.getGame(), p.getNationId());
    }

    /** A player's standing now. */
    public static CreditRecord.Standing standing(Player p) {
        final CreditRecord r = of(p);
        synchronized (CreditRecords.class) {
            return r.standing(p.getGame().getTurn().getNumber());
        }
    }

    /** A player's record, as text (for the save). */
    public static synchronized String serialize(Player p) {
        return of(p).serialize();
    }

    /**
     * Read a player's record back (from the save).
     *
     * @param p The {@code Player}.
     * @param s The text, may be null.
     */
    public static synchronized void load(Player p, String s) {
        if (p == null || p.getGame() == null) return;
        byGame.computeIfAbsent(p.getGame(), g -> new HashMap<>())
            .put(p.getNationId(), CreditRecord.parse(s));
    }
}
