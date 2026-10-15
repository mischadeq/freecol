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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.model.Stance;


/**
 * How a computer nation feels, as of its last turn: per nation it has
 * met its opinion, trust and memories, plus its ambition and goals.
 * Plain data made on the server, read by the companion page from
 * another thread, so it never changes after it is made.
 */
public final class Mood {

    /** The reason shown for a local truce in a King's war. */
    public static final String TRUCE = "at war by the King's order, keeps the peace here";
    /** Opinion at or above which a nation is friendly. */
    public static final double FRIENDLY = 30;
    /** Opinion above which it is neutral (else wary). */
    public static final double NEUTRAL = -10;
    /** Opinion above which it is wary (else hostile). */
    public static final double WARY = -40;
    /** Trust at or above which it trusts. */
    public static final double TRUSTS = 65;
    /** Trust at or above which it is careful (else distrusts). */
    public static final double CAREFUL = 35;

    /** Its feelings about one other nation. */
    public static final class Feeling {
        public final String about;
        public final double opinion, trust;
        /** Our stance towards it. */
        public final Stance stance;
        /** Why we chose that stance, in words ("" if not our choice). */
        public final String why;
        /** Memories that still count, strongest first. */
        public final List<Memory> memories;

        Feeling(String about, double opinion, double trust, Stance stance,
                String why, List<Memory> memories) {
            this.about = about;
            this.stance = stance;
            this.why = (why == null) ? "" : why;
            this.opinion = opinion;
            this.trust = trust;
            this.memories = Collections.unmodifiableList(memories);
        }

        public String mood() {
            return moodWord(opinion);
        }

        public String trustWord() {
            return Mood.trustWord(trust);
        }
    }

    public final String nation;
    public final int turn;
    public final NationCharacter character;
    public final List<Goal> goals;
    /** Nation id to feeling. */
    public final Map<String, Feeling> feelings;
    /** The nations our coalitions stand against (public). */
    public final List<String> coalitionTargets = new ArrayList<>();
    /** Target to the gold we ask to leave that coalition (members who
        did not found it; the asking price is let known to the target). */
    public final Map<String, Integer> buyoutPrices = new LinkedHashMap<>();


    private Mood(String nation, int turn, NationCharacter character,
                 List<Goal> goals, Map<String, Feeling> feelings) {
        this.nation = nation;
        this.turn = turn;
        this.character = character;
        this.goals = Collections.unmodifiableList(goals);
        this.feelings = Collections.unmodifiableMap(feelings);
    }

    /**
     * The mood of a nation from its outlook.
     *
     * @param o The {@code Outlook}.
     * @return The {@code Mood}.
     */
    public static Mood of(Outlook o) {
        return of(o, null);
    }

    /**
     * The mood of a nation from its outlook and its war and peace
     * choices.
     *
     * @param o The {@code Outlook}.
     * @param c Its {@code Commitments}, may be null.
     * @return The {@code Mood}.
     */
    public static Mood of(Outlook o, Commitments c) {
        final WorldView w = o.view;
        Map<String, Feeling> f = new LinkedHashMap<>();
        for (NationView v : w.others.values()) {
            final Commitments.Choice ch = (c == null) ? null : c.get(v.id);
            final boolean truce = c != null && c.inTruce(v.id);
            final String why = (truce) ? TRUCE
                : (ch != null && stanceOf(ch.kind) == v.stance) ? ch.why
                : null;
            f.put(v.id, new Feeling(v.id, v.opinion, v.trust, v.stance, why,
                Memories.about(v.id, w.turn, w.memories)));
        }
        return new Mood(w.me.id, w.turn, w.character,
                        new ArrayList<>(o.situation.goals), f);
    }

    private static Stance stanceOf(Action.Kind k) {
        return (k == Action.Kind.DECLARE_WAR) ? Stance.WAR
            : (k == Action.Kind.MAKE_PEACE) ? Stance.PEACE
            : Stance.ALLIANCE;
    }

    public Feeling about(String id) {
        return feelings.get(id);
    }

    /** "friendly", "neutral", "wary" or "hostile". */
    public static String moodWord(double opinion) {
        return (opinion >= FRIENDLY) ? "friendly"
            : (opinion > NEUTRAL) ? "neutral"
            : (opinion > WARY) ? "wary"
            : "hostile";
    }

    /** "trusts", "careful" or "distrusts". */
    public static String trustWord(double trust) {
        return (trust >= TRUSTS) ? "trusts"
            : (trust >= CAREFUL) ? "careful"
            : "distrusts";
    }
}
