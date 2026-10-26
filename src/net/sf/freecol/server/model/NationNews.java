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
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.sf.freecol.common.model.Game;


/**
 * News about the European nations' alliances that everyone hears
 * (mod): who stayed out of a war between two allies, whose alliance
 * lapsed.  Alliances are public.  Kept for a while, not saved, like
 * {@link TribeNews}.
 */
public final class NationNews {

    /** Turns news is kept. */
    public static final int KEEP = 30;

    public enum Kind {
        /** Allied to both sides of a war: stayed out, peace with the attacker. */
        STAYED_OUT,
        /** An alliance with no reason left ended in peace. */
        LAPSED
    }

    /** One piece of news. */
    public static final class News {
        public final int turn;
        public final Kind kind;
        /** The nation that acted, the one it is about (the attacker,
            the former ally), and the victim (stayed out) or null. */
        public final String nation, other, victim;

        public News(int turn, Kind kind, String nation, String other,
                    String victim) {
            this.turn = turn;
            this.kind = kind;
            this.nation = nation;
            this.other = other;
            this.victim = victim;
        }
    }

    private static final Map<Game, List<News>> byGame = new WeakHashMap<>();

    private NationNews() {}

    /**
     * Add news.
     *
     * @param game The {@code Game}.
     * @param n The {@code News}.
     */
    public static synchronized void add(Game game, News n) {
        final List<News> l = byGame.computeIfAbsent(game, g -> new ArrayList<>());
        l.removeIf(x -> n.turn - x.turn > KEEP);
        l.add(n);
    }

    /**
     * The recent news of a game, oldest first.
     *
     * @param game The {@code Game}.
     * @return A copy of the list.
     */
    public static synchronized List<News> recent(Game game) {
        final List<News> l = byGame.get(game);
        return (l == null) ? new ArrayList<>() : new ArrayList<>(l);
    }
}
