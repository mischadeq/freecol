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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.model.Stance;


/**
 * A nation's last choice of war, peace or alliance towards each other
 * nation, so it does not flip-flop: a choice holds at least
 * {@link #HOLD} turns, and a different one must score {@link #BEAT}
 * times as much.  A shock (war declared on us, a colony lost, a deal
 * broken) lifts both.
 */
public final class Commitments {

    /** Turns a choice holds. */
    public static final int HOLD = 10;
    /** A new choice must score this times the old one. */
    public static final double BEAT = 1.3;

    /** One choice. */
    public static final class Choice {
        public final Action.Kind kind;
        public final int turn;
        public final double score;
        /** Why, in a few words ("revenge", "wants their colonies"). */
        public final String why;

        public Choice(Action.Kind kind, int turn, double score, String why) {
            this.kind = kind;
            this.turn = turn;
            this.score = score;
            this.why = (why == null) ? "" : why;
        }
    }

    private final Map<String, Choice> choices = new LinkedHashMap<>();
    /** The last turn something shocking happened, -1 never. */
    private int lastShock = -1;
    /**
     * Local truces: at war by the King's order with a New World ally,
     * we keep the peace here (nation to the turn it began).
     */
    private final Map<String, Integer> truces = new LinkedHashMap<>();


    public Commitments() {}


    public Choice get(String nation) {
        return choices.get(nation);
    }

    public int size() {
        return choices.size();
    }

    public int lastShock() {
        return lastShock;
    }

    /** Keep a local truce with a nation, from a turn. */
    public void truce(String nation, int turn) {
        truces.put(nation, turn);
    }

    /** End a local truce. */
    public void endTruce(String nation) {
        truces.remove(nation);
    }

    /** Do we keep a local truce with a nation? */
    public boolean inTruce(String nation) {
        return truces.containsKey(nation);
    }

    /** The nations we keep a local truce with. */
    public java.util.Set<String> truces() {
        return new java.util.HashSet<>(truces.keySet());
    }

    /**
     * Something shocking happened: the holds are lifted.
     *
     * @param turn The turn.
     */
    public void shock(int turn) {
        lastShock = Math.max(lastShock, turn);
    }

    /**
     * The war, peace or alliance move to make towards a nation now.
     *
     * @param nation The nation id.
     * @param current Our stance towards it.
     * @param ranked The diplomat's moves, best first.
     * @param now The current turn.
     * @return The move, or null to keep things as they are.
     */
    public ScoredAction decide(String nation, Stance current,
                               List<ScoredAction> ranked, int now) {
        ScoredAction best = null;
        for (ScoredAction sa : ranked) {
            if (!nation.equals(sa.action.target)
                || sa.total < Diplomat.THRESHOLD
                || !fits(sa.action.kind, current)) continue;
            best = sa;
            break;
        }
        if (best == null) return null;
        final Choice last = choices.get(nation);
        if (last == null || last.kind == best.action.kind
            || lastShock > last.turn) return best;
        if (now - last.turn < HOLD) return null;
        return (best.total >= BEAT * last.score) ? best : null;
    }

    /** Does a move make sense from a stance? */
    private static boolean fits(Action.Kind kind, Stance current) {
        switch (kind) {
        case DECLARE_WAR:
            return current != Stance.WAR && current != Stance.UNCONTACTED;
        case MAKE_PEACE:
            return current == Stance.WAR || current == Stance.CEASE_FIRE;
        case ALLIANCE:
            return current == Stance.PEACE;
        default:
            return false;
        }
    }

    /**
     * Remember a choice that was made.
     *
     * @param nation The nation id.
     * @param sa The move.
     * @param now The turn.
     */
    public void chose(String nation, ScoredAction sa, int now) {
        choices.put(nation, new Choice(sa.action.kind, now, sa.total,
                                       why(sa)));
    }

    /**
     * Is anything in a turn's changes a shock to a nation: war declared
     * on it, a colony of its lost, a deal broken by an ally?
     *
     * @param me The nation.
     * @param d What happened ({@link TurnDiff#diff}).
     * @param before The earlier {@code Snapshot}.
     * @return True if so.
     */
    public static boolean isShock(String me, List<Observed> d,
                                  Snapshot before) {
        for (Observed o : d) {
            switch (o.what) {
            case WAR:
                if (me.equals(o.other) && !o.forced) return true;
                break;
            case COLONY_LOST:
                if (me.equals(o.actor)) return true;
                break;
            case CAPTURE:
                if (me.equals(o.other)) return true;
                break;
            default:
                break;
            }
        }
        return false;
    }

    /**
     * Why a war, peace or alliance was chosen, in a few words: its
     * biggest reason.
     *
     * @param sa The scored move.
     * @return The words.
     */
    public static String why(ScoredAction sa) {
        if (sa.action.kind == Action.Kind.JOIN_WAR) return "an ally's call";
        if (sa.goal >= Goal.GOAL_BONUS / 2 && !sa.note.isEmpty()) {
            final String n = sa.note;
            if (n.contains("revenge")) return "revenge";
            if (n.contains("contain_leader")) return "to stop the leader";
            if (n.contains("make_peace")) return "tired of war";
            if (n.contains("take_colony")) return "wants their colonies";
            if (n.contains("defend")) return "needs help to defend";
        }
        final String[] words;
        switch (sa.action.kind) {
        case DECLARE_WAR:
            words = new String[] { "for gold", "for trade", "wants their colonies",
                                   "for safety", "for friends", "hates them" };
            break;
        case MAKE_PEACE:
            words = new String[] { "war costs too much", "to trade again",
                                   "keeps its colonies", "war is too dangerous",
                                   "for friends", "likes them" };
            break;
        default: // ALLIANCE
            words = new String[] { "for gold", "for trade", "for land",
                                   "against a common threat", "for friends",
                                   "likes them" };
            break;
        }
        final double[] parts = { sa.gold, sa.trade, sa.land, sa.security,
                                 sa.relations, sa.opinion };
        int best = -1;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] > 0 && (best < 0 || parts[i] > parts[best])) best = i;
        }
        return (best < 0) ? "" : words[best];
    }


    // Serialization: "nation:KIND:turn:score:why;...", then "!shock".

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Choice> e : choices.entrySet()) {
            final Choice c = e.getValue();
            sb.append(e.getKey()).append(':').append(c.kind.name())
                .append(':').append(c.turn).append(':')
                .append(Math.round(c.score * 10) / 10.0).append(':')
                .append(c.why.replace(':', ' ').replace(';', ' '))
                .append(';');
        }
        for (Map.Entry<String, Integer> e : truces.entrySet()) {
            sb.append('~').append(e.getKey()).append(':')
                .append(e.getValue()).append(';');
        }
        if (lastShock >= 0) sb.append('!').append(lastShock);
        return sb.toString();
    }

    public static Commitments parse(String s) {
        Commitments ret = new Commitments();
        if (s == null || s.isEmpty()) return ret;
        for (String rec : s.split(";")) {
            try {
                if (rec.startsWith("!")) {
                    ret.lastShock = Integer.parseInt(rec.substring(1));
                    continue;
                }
                if (rec.startsWith("~")) {
                    final int i = rec.lastIndexOf(':');
                    if (i <= 1) continue;
                    ret.truces.put(rec.substring(1, i),
                                   Integer.parseInt(rec.substring(i + 1)));
                    continue;
                }
                String[] f = rec.split(":", -1);
                if (f.length != 5) continue;
                ret.choices.put(f[0], new Choice(Action.Kind.valueOf(f[1]),
                    Integer.parseInt(f[2]), Double.parseDouble(f[3]), f[4]));
            } catch (IllegalArgumentException e) {} // skip
        }
        return ret;
    }
}
