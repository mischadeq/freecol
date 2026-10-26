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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.Unit;


/**
 * The event log shown in the sidebar of the companion page.
 *
 * Entries come from two places: the game code posts some events
 * directly (a carrier loading or unloading at a trade route stop),
 * and {@link CompanionState} posts the rest when it notices a change
 * between two snapshots (a new turn, a message, a unit arriving).
 *
 * The log lives in memory only and keeps the latest entries.
 */
public final class CompanionLog {

    /** How many entries to keep. */
    private static final int MAX = 500;

    /** One entry of the log. */
    public static final class Entry {
        final long id;
        final int turn;
        /** What kind of event: turn, message, load, unload, arrive... */
        final String kind;
        final String text;
        /** Where it happened, or -1. */
        final int x, y;
        /** Goods involved, by goods type id. */
        final Map<String, Integer> goods;
        /** What to highlight: runs {start, end, flags} (mod). */
        final java.util.List<int[]> runs;
        /** Facts for the page's chips (mod): "group", "chip", "detail",
            "repeat", "route", "place", "who", "icon"; may be empty. */
        final Map<String, String> facts;

        Entry(long id, int turn, String kind, String text, Tile tile,
              Map<String, Integer> goods,
              java.util.Collection<String> names,
              Map<String, String> facts) {
            this.facts = new LinkedHashMap<>();
            if (facts != null) this.facts.putAll(facts);
            this.facts.putIfAbsent("group", LogDigest.group(kind, null));
            this.id = id;
            this.turn = turn;
            this.kind = kind;
            this.text = text;
            this.x = (tile == null) ? -1 : tile.getX();
            this.y = (tile == null) ? -1 : tile.getY();
            this.goods = (goods == null) ? Collections.emptyMap() : goods;
            this.runs = CompanionHighlight.runs(text, names);
        }
    }

    private static final Deque<Entry> entries = new ArrayDeque<>();

    private static long nextId = 1;

    /** A token that changes when the log starts afresh. */
    private static final String session
        = Long.toString(System.currentTimeMillis(), 36);


    private CompanionLog() {}


    /**
     * Add an entry.
     *
     * @param turn The turn number.
     * @param kind The kind of event.
     * @param text The text to show.
     * @param tile Where it happened, or null.
     * @param goods Goods involved, or null.
     */
    public static synchronized void post(int turn, String kind, String text,
                                         Tile tile, Map<String, Integer> goods) {
        post(turn, kind, text, tile, goods, null, null);
    }

    /**
     * Add an entry, with the names filled into its text, which are
     * shown in bold (mod).
     *
     * @param turn The turn number.
     * @param kind The kind of event.
     * @param text The text to show.
     * @param tile Where it happened, or null.
     * @param goods Goods involved, or null.
     * @param names The names in the text (nations, colonies, goods...),
     *     or null.
     */
    public static synchronized void post(int turn, String kind, String text,
                                         Tile tile, Map<String, Integer> goods,
                                         java.util.Collection<String> names) {
        post(turn, kind, text, tile, goods, names, null);
    }

    /**
     * Add an entry with facts for the page's chips (mod).
     *
     * @param turn The turn number.
     * @param kind The kind of event.
     * @param text The full text (shown on hover when a chip is short).
     * @param tile Where it happened, or null.
     * @param goods Goods involved, or null.
     * @param names The names in the text, or null.
     * @param facts The facts ({@link Entry#facts}), or null.
     */
    public static synchronized void post(int turn, String kind, String text,
                                         Tile tile, Map<String, Integer> goods,
                                         java.util.Collection<String> names,
                                         Map<String, String> facts) {
        entries.addLast(new Entry(nextId++, turn, kind, text, tile, goods,
                                  names, facts));
        while (entries.size() > MAX) entries.removeFirst();
    }

    /**
     * Get the entries after a given one.
     *
     * @param after The id of the last entry already seen.
     * @return The newer entries, oldest first.
     */
    static synchronized List<Entry> since(long after) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : entries) if (e.id > after) result.add(e);
        return result;
    }

    static String getSession() {
        return session;
    }


    // Hooks for the game code.

    /**
     * Note what a unit is carrying, to compare with later.
     *
     * @param unit The carrier {@code Unit}.
     * @return Its goods, by type.
     */
    public static Map<GoodsType, Integer> cargoOf(Unit unit) {
        Map<GoodsType, Integer> result = new LinkedHashMap<>();
        for (Goods g : unit.getCompactGoodsList()) {
            result.merge(g.getType(), g.getAmount(), Integer::sum);
        }
        return result;
    }

    /**
     * A carrier on a trade route has done its work at a stop.  Log
     * what it unloaded and loaded there.
     *
     * Never throws: the log must not get in the way of the game.
     *
     * @param unit The carrier {@code Unit}.
     * @param route The {@code TradeRoute} it follows.
     * @param before The cargo when it arrived.
     * @param unloaded The cargo after unloading.
     * @param after The cargo after loading.
     */
    public static void tradeStop(Unit unit, TradeRoute route,
                                 Map<GoodsType, Integer> before,
                                 Map<GoodsType, Integer> unloaded,
                                 Map<GoodsType, Integer> after) {
        try {
            final Player player = unit.getOwner();
            final int turn = unit.getGame().getTurn().getNumber();
            final String where = (unit.getTile() == null) ? "?"
                : Messages.message(unit.getTile()
                    .getLocationLabelFor(player));
            final String who = Messages.message(unit
                .getLabel(Unit.UnitLabelType.PLAIN));
            final String routeName = (route == null) ? "" : route.getName();

            Map<String, Integer> off = diff(before, unloaded);
            Map<String, Integer> on = diff(after, unloaded);
            if (!off.isEmpty()) {
                for (Map.Entry<String, Integer> e : off.entrySet()) {
                    e.setValue(-e.getValue());
                }
                post(turn, "unload", Messages.message(StringTemplate
                        .template("companion.log.unload")
                        .addName("%unit%", who)
                        .addName("%route%", routeName)
                        .addName("%location%", where)),
                     unit.getTile(), off, null, stopFacts(routeName, where, who));
            }
            if (!on.isEmpty()) {
                post(turn, "load", Messages.message(StringTemplate
                        .template("companion.log.load")
                        .addName("%unit%", who)
                        .addName("%route%", routeName)
                        .addName("%location%", where)),
                     unit.getTile(), on, null, stopFacts(routeName, where, who));
            }
        } catch (RuntimeException re) {
            // Ignore, the log is a convenience.
        }
    }

    /** The facts of a trade route stop, for the page's route chips. */
    private static Map<String, String> stopFacts(String route, String place,
                                                 String who) {
        final Map<String, String> f = new LinkedHashMap<>();
        f.put("group", LogDigest.ROUTE);
        f.put("route", route);
        f.put("place", place);
        f.put("who", who);
        return f;
    }

    /** How much more of each goods a holds than b, by goods type id. */
    private static Map<String, Integer> diff(Map<GoodsType, Integer> a,
                                             Map<GoodsType, Integer> b) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<GoodsType, Integer> e : a.entrySet()) {
            int less = e.getValue() - b.getOrDefault(e.getKey(), 0);
            if (less > 0) result.put(e.getKey().getId(), less);
        }
        return result;
    }
}
