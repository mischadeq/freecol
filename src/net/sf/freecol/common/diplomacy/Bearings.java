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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * Where a nation thinks the others are, from what it has seen (Mischa's
 * idea): every colony, soldier or ship of another nation spotted near
 * us points from our biggest colony towards where that nation lives.
 * A ship seen on two turns in a row gives a course: sailing in from the
 * open sea it points at its colonies, sailing out it came from them.
 *
 * The guess is the weighted average of those directions: colonies count
 * most, then land units, then ships; old sightings fade.  How much the
 * directions agree, and how many there are, gives the confidence.
 *
 * Plain data, so it can be kept, saved and tested without a game.
 * Distances are in tiles, with north-south steps halved (FreeCol's map
 * rows are half a tile high).
 */
public final class Bearings {

    /** What was seen, and how much it counts. */
    public enum Kind {
        COLONY(3), LAND(2), SHIP(1), COURSE(1.5);

        public final double weight;

        Kind(double weight) {
            this.weight = weight;
        }
    }

    /** One sighting. */
    public static final class Sighting {
        public final String nation;
        public final int x, y, turn;
        public final Kind kind;

        public Sighting(String nation, int x, int y, int turn, Kind kind) {
            this.nation = nation;
            this.x = x;
            this.y = y;
            this.turn = turn;
            this.kind = kind;
        }
    }

    /** The guess about one nation. */
    public static final class Bearing {
        public final String nation;
        /** Direction (unit vector; x east, y south). */
        public final double dx, dy;
        /** 0..1: how much the sightings agree, times how many there are. */
        public final double confidence;
        /** Average distance of the sightings, in tiles. */
        public final double distance;
        public final int sightings;

        Bearing(String nation, double dx, double dy, double confidence,
                double distance, int sightings) {
            this.nation = nation;
            this.dx = dx;
            this.dy = dy;
            this.confidence = confidence;
            this.distance = distance;
            this.sightings = sightings;
        }

        /** "north-east" and so on. */
        public String compass() {
            return Bearings.compass(dx, dy);
        }

        /**
         * The map tile the guess points at, from an origin.
         *
         * @param ox Origin x.
         * @param oy Origin y.
         * @return {x, y}.
         */
        public int[] target(int ox, int oy) {
            return new int[] { (int)Math.round(ox + dx * distance),
                               (int)Math.round(oy + 2 * dy * distance) };
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s %s ~%.0f tiles (%d seen, %.0f%%)",
                Action.shortId(nation), compass(), distance, sightings,
                100 * confidence);
        }
    }

    /** Sightings fade to nothing over this many turns. */
    public static final int FADE = 30;
    /** Sightings kept per nation (the oldest go first). */
    public static final int KEEP = 120;
    /** How far a ship's course is followed, in tiles. */
    public static final int COURSE_LENGTH = 15;
    /** Weight of sightings at which confidence is no longer held back. */
    public static final double FULL_WEIGHT = 6;
    /**
     * Sightings nearer than this (tiles) to our colony count less: a
     * visitor at our door says little about where he lives.
     */
    public static final double NEAR = 6;
    /** Most turns between two sightings of a unit to give a course. */
    public static final int COURSE_GAP = 3;

    private final Map<String, List<Sighting>> byNation = new LinkedHashMap<>();


    public void add(Sighting s) {
        List<Sighting> l = byNation.computeIfAbsent(s.nation, k -> new ArrayList<>());
        l.add(s);
        while (l.size() > KEEP) l.remove(0);
    }

    /**
     * Add a ship's course: seen at (x0, y0) last turn and (x1, y1) now.
     *
     * @param nation The ship's nation.
     * @param x0 Previous x.
     * @param y0 Previous y.
     * @param x1 Current x.
     * @param y1 Current y.
     * @param turn The turn.
     * @param fromHome True if it comes from home (home lies behind it),
     *     false if it goes home (home lies ahead).
     */
    public void addCourse(String nation, int x0, int y0, int x1, int y1,
                          int turn, boolean fromHome) {
        double vx = x1 - x0, vy = (y1 - y0) / 2.0;
        double len = Math.hypot(vx, vy);
        if (len == 0) return;
        vx /= len;
        vy /= len;
        int px, py;
        if (fromHome) { // behind it
            px = (int)Math.round(x0 - vx * COURSE_LENGTH);
            py = (int)Math.round(y0 - 2 * vy * COURSE_LENGTH);
        } else { // ahead of it
            px = (int)Math.round(x1 + vx * COURSE_LENGTH);
            py = (int)Math.round(y1 + 2 * vy * COURSE_LENGTH);
        }
        add(new Sighting(nation, px, py, turn, Kind.COURSE));
    }

    /**
     * Add a ship's course, kept on a map of the given size.
     *
     * @see #addCourse(String, int, int, int, int, int, boolean)
     * @param width The map width.
     * @param height The map height.
     */
    public void addCourse(String nation, int x0, int y0, int x1, int y1,
                          int turn, boolean fromHome, int width, int height) {
        addCourse(nation, x0, y0, x1, y1, turn, fromHome);
        List<Sighting> l = byNation.get(nation);
        if (l == null || l.isEmpty()) return;
        Sighting s = l.get(l.size() - 1);
        if (s.kind != Kind.COURSE || s.turn != turn) return;
        final int cx = Math.max(0, Math.min(width - 1, s.x));
        final int cy = Math.max(0, Math.min(height - 1, s.y));
        if (cx != s.x || cy != s.y) {
            l.set(l.size() - 1, new Sighting(nation, cx, cy, turn, Kind.COURSE));
        }
    }

    /**
     * The guess about a nation, seen from an origin.
     *
     * @param nation The nation id.
     * @param ox Origin x (our biggest colony).
     * @param oy Origin y.
     * @param now The current turn.
     * @return The {@code Bearing}, or null if nothing was seen lately.
     */
    public Bearing bearing(String nation, int ox, int oy, int now) {
        List<Sighting> l = byNation.get(nation);
        if (l == null) return null;
        double sx = 0, sy = 0, sw = 0, sd = 0;
        int n = 0;
        for (Sighting s : l) {
            final int age = now - s.turn;
            if (age < 0 || age >= FADE) continue;
            final double vx = s.x - ox, vy = (s.y - oy) / 2.0;
            final double d = Math.hypot(vx, vy);
            if (d < 1) continue; // on top of us: no direction
            final double w = s.kind.weight * (1 - age / (double)FADE)
                * Math.min(1, (d / NEAR) * (d / NEAR));
            sx += w * vx / d;
            sy += w * vy / d;
            sd += w * d;
            sw += w;
            n++;
        }
        if (sw <= 0) return null;
        final double mx = sx / sw, my = sy / sw;
        final double agree = Math.hypot(mx, my);
        if (agree == 0) return null;
        return new Bearing(nation, mx / agree, my / agree,
                           agree * Math.min(1, sw / FULL_WEIGHT), sd / sw, n);
    }

    /**
     * All guesses, from an origin.
     *
     * @param ox Origin x.
     * @param oy Origin y.
     * @param now The current turn.
     * @return Nation id to {@code Bearing}.
     */
    public Map<String, Bearing> all(int ox, int oy, int now) {
        Map<String, Bearing> ret = new LinkedHashMap<>();
        for (String n : byNation.keySet()) {
            Bearing b = bearing(n, ox, oy, now);
            if (b != null) ret.put(n, b);
        }
        return ret;
    }

    /**
     * Forget sightings from a turn on (a reloaded game plays them again).
     *
     * @param turn The first turn to forget.
     */
    public void forget(int turn) {
        for (List<Sighting> l : byNation.values()) l.removeIf(s -> s.turn >= turn);
    }

    public int size() {
        int n = 0;
        for (List<Sighting> l : byNation.values()) n += l.size();
        return n;
    }

    /**
     * A direction in words, on the map (y grows southwards).
     *
     * @param dx East component.
     * @param dy South component.
     * @return "north", "north-east", ...
     */
    public static String compass(double dx, double dy) {
        final String[] names = { "east", "south-east", "south", "south-west",
                                 "west", "north-west", "north", "north-east" };
        double a = Math.atan2(dy, dx); // 0 = east, pi/2 = south
        int i = (int)Math.round(a / (Math.PI / 4));
        return names[((i % 8) + 8) % 8];
    }

    /** Write as lines "nation,x,y,turn,kind". */
    public String serialize() {
        return serialize('\n');
    }

    /**
     * Write as records "nation,x,y,turn,kind", ending each with a
     * separator (';' fits in an XML attribute).
     *
     * @param sep The separator.
     * @return The text.
     */
    public String serialize(char sep) {
        StringBuilder sb = new StringBuilder();
        for (List<Sighting> l : byNation.values()) {
            for (Sighting s : l) {
                sb.append(s.nation).append(',').append(s.x).append(',')
                    .append(s.y).append(',').append(s.turn).append(',')
                    .append(s.kind.name()).append(sep);
            }
        }
        return sb.toString();
    }

    /** Read what {@link #serialize} wrote; bad records are skipped. */
    public static Bearings parse(String text) {
        Bearings b = new Bearings();
        b.read(text);
        return b;
    }

    /**
     * Replace the sightings with those in a text from
     * {@link #serialize}; bad records are skipped.
     *
     * @param text The text, may be null.
     */
    public void read(String text) {
        byNation.clear();
        lastSeen.clear();
        if (text == null) return;
        for (String line : text.split("[\n;]")) {
            String[] f = line.trim().split(",");
            if (f.length != 5) continue;
            try {
                add(new Sighting(f[0], Integer.parseInt(f[1]),
                    Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                    Kind.valueOf(f[4])));
            } catch (IllegalArgumentException e) {} // skip
        }
    }

    /** Last positions of units, to work out courses (unit id to {x, y, turn}). */
    final Map<String, int[]> lastSeen = new HashMap<>();

    /** Forget unit positions too old to give a course. */
    void forgetOld(int now) {
        lastSeen.values().removeIf(v -> now - v[2] > COURSE_GAP);
    }
}
