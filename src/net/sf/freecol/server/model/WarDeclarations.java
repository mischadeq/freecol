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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;


/**
 * Who declared the recent wars, and whether the King ordered it (mod).
 * A save does not say; the server knows when the stance changes.  Kept
 * for a few turns, not saved: after a load the wars of the last round
 * are guessed from tension again.
 */
public final class WarDeclarations {

    /** Turns a declaration is kept. */
    public static final int KEEP = 3;

    /** One declaration. */
    public static final class Declaration {
        public final String declarer, target;
        public final int turn;
        public final boolean byKing;

        Declaration(String declarer, String target, int turn, boolean byKing) {
            this.declarer = declarer;
            this.target = target;
            this.turn = turn;
            this.byKing = byKing;
        }
    }

    private static final Map<Game, List<Declaration>> byGame
        = new WeakHashMap<>();

    /** Set while the King's order is carried out (one server thread). */
    private static final ThreadLocal<Boolean> kingsOrder
        = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private WarDeclarations() {}


    /**
     * Run something as the King's order: wars declared meanwhile are
     * his.
     *
     * @param r The {@code Runnable}.
     */
    public static void asKing(Runnable r) {
        final Boolean old = kingsOrder.get();
        kingsOrder.set(Boolean.TRUE);
        try {
            r.run();
        } finally {
            kingsOrder.set(old);
        }
    }

    /**
     * Note a war declaration.
     *
     * @param declarer The declaring {@code Player}.
     * @param target The other {@code Player}.
     */
    public static synchronized void record(Player declarer, Player target) {
        final Game game = declarer.getGame();
        final int now = game.getTurn().getNumber();
        List<Declaration> l = byGame.computeIfAbsent(game, g -> new ArrayList<>());
        for (Iterator<Declaration> it = l.iterator(); it.hasNext();) {
            if (now - it.next().turn > KEEP) it.remove();
        }
        l.add(new Declaration(declarer.getNationId(), target.getNationId(),
                              now, kingsOrder.get()));
    }

    /**
     * The recent declarations of a game.
     *
     * @param game The {@code Game}.
     * @return A copy of the list.
     */
    public static synchronized List<Declaration> recent(Game game) {
        final List<Declaration> l = byGame.get(game);
        return (l == null) ? new ArrayList<>() : new ArrayList<>(l);
    }
}
