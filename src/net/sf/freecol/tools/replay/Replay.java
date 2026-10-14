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

package net.sf.freecol.tools.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.Action;
import net.sf.freecol.common.diplomacy.Diplomat;
import net.sf.freecol.common.diplomacy.Memories;
import net.sf.freecol.common.diplomacy.NationCharacter;
import net.sf.freecol.common.diplomacy.Observed;
import net.sf.freecol.common.diplomacy.ScoredAction;
import net.sf.freecol.common.diplomacy.Situation;
import net.sf.freecol.common.diplomacy.Snapshot;
import net.sf.freecol.common.diplomacy.TurnDiff;
import net.sf.freecol.common.diplomacy.WorldView;
import net.sf.freecol.common.diplomacy.WorldViewBuilder;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.server.model.ServerGame;


/**
 * Step 0: replay a game from its saves.  For each turn, every European
 * nation's diplomat scores its moves from that nation's honest view;
 * the next save shows what the nation really did; the saves after that
 * show how it turned out.
 */
public final class Replay {

    private static final Logger logger = Logger.getLogger(Replay.class.getName());

    /** How many ranked moves are kept per row. */
    public static final int KEEP = 5;
    /** Top N counts as agreeing. */
    public static final int AGREE_TOP = 3;
    /** Turns before a war searched for a reason. */
    public static final int REASON_TURNS = 5;

    /** One nation in one turn. */
    public static final class Row {
        public final int index, turn;
        public final String label, nation;
        public final boolean human;
        public final NationCharacter character;
        public final List<ScoredAction> top = new ArrayList<>();
        public ScoredAction chosen;
        public final List<String> goals = new ArrayList<>();
        /** Everything seen for this nation from this save to the next. */
        public final List<Observed> observed = new ArrayList<>();
        /** Its own moves comparable with the diplomat's. */
        public final List<Action> moves = new ArrayList<>();
        /** Rank (1-based) of each move in the diplomat's list, 0 if absent. */
        public final List<Integer> ranks = new ArrayList<>();
        /** The diplomat's score of each move, null if it did not list it. */
        public final List<Double> moveScores = new ArrayList<>();
        /** War and peace scores per other nation. */
        public final Map<String, Double> warScore = new HashMap<>(),
            peaceScore = new HashMap<>();
        public Double outcome;
        /** Staying out of each war: score and how ("pay 900 gold"). */
        public final Map<String, Object[]> stayOut = new HashMap<>();

        Row(int index, int turn, String label, String nation, boolean human,
            NationCharacter character) {
            this.index = index;
            this.turn = turn;
            this.label = label;
            this.nation = nation;
            this.human = human;
            this.character = character;
        }

        /**
         * Would the diplomat have made one of the nation's moves too: in
         * its top 3, or scoring enough to be made alongside the top one
         * (arming, a sale)?
         */
        public boolean agrees() {
            for (int k = 0; k < ranks.size(); k++) {
                if (agrees(k)) return true;
            }
            return false;
        }

        public boolean agrees(int k) {
            int r = ranks.get(k);
            Double s = moveScores.get(k);
            return (r > 0 && r <= AGREE_TOP)
                || (s != null && s >= Diplomat.threshold(moves.get(k).kind));
        }

        public boolean unprompted() {
            return moves.isEmpty() && chosen != null
                && chosen.action.kind != Action.Kind.NOTHING
                && chosen.action.kind.visible;
        }
    }

    /** A war that broke out, and whether the diplomat saw a reason. */
    public static final class War {
        public final int turn;
        public final String label;
        public final Observed seen;
        /** Best war score of the declarer for this enemy in the turns before. */
        public double bestScore = Double.NEGATIVE_INFINITY;
        public String why = "";
        /** For a King's war: the score of paying to stay out, and how. */
        public Double stayOut;
        public String stayOutHow = "";

        War(int turn, String label, Observed seen) {
            this.turn = turn;
            this.label = label;
            this.seen = seen;
        }
    }

    /** How good one nation's guess about another was, at one turn. */
    public static final class BearingCheck {
        public final int turn;
        public final String observer, target;
        /** The guess: direction, distance, confidence. */
        public final net.sf.freecol.common.diplomacy.Bearings.Bearing guess;
        /** True direction to the target's colonies (degrees off). */
        public final double error;
        public final double trueDistance;
        /** True direction (unit vector, x east, y south). */
        public final double trueDx, trueDy;
        /** Had the observer explored one of the target's colonies yet? */
        public final boolean known;

        BearingCheck(int turn, String observer, String target,
                     net.sf.freecol.common.diplomacy.Bearings.Bearing guess,
                     double error, double trueDistance, double trueDx,
                     double trueDy, boolean known) {
            this.turn = turn;
            this.trueDx = trueDx;
            this.trueDy = trueDy;
            this.observer = observer;
            this.target = target;
            this.guess = guess;
            this.error = error;
            this.trueDistance = trueDistance;
            this.known = known;
        }
    }

    public final List<BearingCheck> bearingChecks = new ArrayList<>();
    public final List<Snapshot> snaps = new ArrayList<>();
    public final List<Row> rows = new ArrayList<>();
    public final List<War> wars = new ArrayList<>();
    public final List<List<Observed>> diffs = new ArrayList<>();


    /**
     * Run the replay.
     *
     * @param saves The saves in turn order.
     * @param declaredByHuman Nations the human player declared war on.
     * @param progress Called with each turn done, may be null.
     * @exception Exception if a save can not be read.
     */
    public void run(List<SaveSeries.Entry> saves, Set<String> declaredByHuman,
                    java.util.function.IntConsumer progress)
        throws Exception {
        Map<String, Memories> memories = new HashMap<>();
        Map<String, net.sf.freecol.common.diplomacy.Bearings> bearings = new HashMap<>();
        for (int i = 0; i < saves.size(); i++) {
            final ServerGame game = SaveSeries.load(saves.get(i));
            final Snapshot snap = Snapshot.of(game);
            snaps.add(snap);
            if (i > 0) {
                List<Observed> d = TurnDiff.diff(snaps.get(i - 1), snap,
                                                 declaredByHuman);
                diffs.add(d);
                for (String n : snap.nations.keySet()) {
                    Memories m = memories.computeIfAbsent(n, k -> new Memories());
                    m.remember(n, d, snaps.get(i - 1), snaps.get(i - 1).turn);
                    m.fade(snap.turn);
                }
            }
            for (Player p : game.getLiveEuropeanPlayerList()) {
                if (p.isREF()) continue;
                net.sf.freecol.common.diplomacy.Bearings b = bearings
                    .computeIfAbsent(p.getNationId(),
                                     k -> new net.sf.freecol.common.diplomacy.Bearings());
                net.sf.freecol.common.diplomacy.Sightings.look(game, p, b);
                checkBearings(game, p, b);
                Memories m = memories.get(p.getNationId());
                WorldView w = WorldViewBuilder.build(game, p, true,
                    (m == null) ? null : m.list(), b);
                Situation s = new Situation(w);
                List<ScoredAction> ranked = Diplomat.rank(s);
                Row row = new Row(i, snap.turn, snap.label, p.getNationId(),
                                  !p.isAI(), w.character);
                for (int k = 0; k < ranked.size() && k < KEEP; k++) {
                    row.top.add(ranked.get(k));
                }
                row.chosen = Diplomat.choose(ranked);
                s.goals.forEach(g -> row.goals.add(g.toString()));
                for (ScoredAction sa : ranked) {
                    if (sa.action.target == null) continue;
                    if (sa.action.kind == Action.Kind.DECLARE_WAR) {
                        row.warScore.put(sa.action.target, sa.total);
                    } else if (sa.action.kind == Action.Kind.MAKE_PEACE) {
                        row.peaceScore.put(sa.action.target, sa.total);
                    }
                }
                for (net.sf.freecol.common.diplomacy.NationView v : w.enemies()) {
                    Object[] price = net.sf.freecol.common.diplomacy.Effects
                        .kingsWarPrice(w, v);
                    ScoredAction peace = Diplomat.score(Action.peace(v.id), s);
                    double score = peace.total
                        - w.character.gold * (Double)price[0] / 100;
                    row.stayOut.put(v.id, new Object[] { score, price[1] });
                }
                rankedByRow.put(row, ranked);
                rows.add(row);
            }
            if (progress != null) progress.accept(snap.turn);
        }
        diffs.add(new ArrayList<>()); // nothing after the last save
        compare();
        rankedByRow.clear();
    }

    /** The full ranking of each row, only while comparing. */
    private final Map<Row, List<ScoredAction>> rankedByRow = new HashMap<>();

    /** Compare a nation's guesses with where the others really are. */
    private void checkBearings(ServerGame game, Player me,
                               net.sf.freecol.common.diplomacy.Bearings b) {
        final net.sf.freecol.common.model.Tile o
            = net.sf.freecol.common.diplomacy.Sightings.origin(me);
        if (o == null) return;
        final int now = game.getTurn().getNumber();
        for (Player x : game.getLiveEuropeanPlayerList()) {
            if (x == me || x.isREF() || x.getColonyList().isEmpty()) continue;
            var guess = b.bearing(x.getNationId(), o.getX(), o.getY(), now);
            if (guess == null) continue;
            double cx = 0, cy = 0;
            boolean known = false;
            for (net.sf.freecol.common.model.Colony c : x.getColonyList()) {
                cx += c.getTile().getX();
                cy += c.getTile().getY();
                known |= c.getTile().isExploredBy(me);
            }
            cx /= x.getColonyList().size();
            cy /= x.getColonyList().size();
            final double tx = cx - o.getX(), ty = (cy - o.getY()) / 2.0;
            final double a = Math.atan2(ty, tx), g = Math.atan2(guess.dy, guess.dx);
            double err = Math.abs(Math.toDegrees(a - g)) % 360;
            if (err > 180) err = 360 - err;
            final double td = Math.hypot(tx, ty);
            bearingChecks.add(new BearingCheck(now, me.getNationId(),
                x.getNationId(), guess, err, td,
                (td == 0) ? 0 : tx / td, (td == 0) ? 0 : ty / td, known));
        }
    }

    /** The turn of the history event for a war, or the later save's. */
    private static int historyTurn(Snapshot a, Snapshot b, Observed o) {
        for (String ev : b.events) {
            if (a.events.contains(ev)) continue;
            String[] f = ev.split("\\|", -1);
            if (f.length < 4 || !"DECLARE_WAR".equals(f[1])) continue;
            if ((f[0].equals(o.actor) && f[2].equals(o.other))
                || (f[0].equals(o.other) && f[2].equals(o.actor))) {
                return Integer.parseInt(f[3]);
            }
        }
        return b.turn;
    }

    private void compare() {
        Map<String, Double> usual = new HashMap<>();
        Map<String, Integer> lastRow = new HashMap<>();
        for (Row row : rows) {
            final List<Observed> d = diffs.get(row.index);
            final List<ScoredAction> ranked = rankedByRow.get(row);
            for (Observed o : d) {
                if (!row.nation.equals(o.actor)) continue;
                row.observed.add(o);
                Action a = o.asAction();
                if (a == null) continue;
                row.moves.add(a);
                int rank = 0;
                Double score = null;
                for (int k = 0; k < ranked.size(); k++) {
                    if (ranked.get(k).action.matches(a)) {
                        rank = k + 1;
                        score = ranked.get(k).total;
                        break;
                    }
                }
                row.ranks.add(rank);
                row.moveScores.add(score);
            }
            for (Observed o : d) { // things that happened to it
                if (row.nation.equals(o.other) && !row.nation.equals(o.actor)
                    && (o.what == Observed.What.WAR
                        || o.what == Observed.What.PEACE
                        || o.what == Observed.What.CAPTURE)) {
                    row.observed.add(o);
                }
            }
            double u = usual.computeIfAbsent(row.nation,
                n -> Hindsight.usual(snaps, n, row.character));
            row.outcome = Hindsight.outcome(snaps, row.index, row.nation,
                                            row.character, u);
            lastRow.put(row.nation, row.index);
        }
        // Wars: did the declarer's diplomat see a reason before?
        for (int i = 0; i < diffs.size(); i++) {
            for (Observed o : diffs.get(i)) {
                if (o.what != Observed.What.WAR) continue;
                // Labelled with the turn of the game's own history
                // event, or else the turn it shows up in.
                Snapshot s = snaps.get(Math.min(i + 1, snaps.size() - 1));
                int turn = historyTurn(snaps.get(i), s, o);
                String label = s.label;
                for (Snapshot t : snaps) {
                    if (t.turn == turn) label = t.label;
                }
                War war = new War(turn, label, o);
                for (Row row : rows) {
                    if (!row.nation.equals(o.actor)
                        || row.index > i || row.index < i - REASON_TURNS) {
                        continue;
                    }
                    Double sc = row.warScore.get(o.other);
                    if (sc != null && sc > war.bestScore) {
                        war.bestScore = sc;
                        for (ScoredAction sa : rankedByRow.get(row)) {
                            if (sa.action.kind == Action.Kind.DECLARE_WAR
                                && o.other.equals(sa.action.target)) {
                                war.why = sa.parts()
                                    + (sa.note.isEmpty() ? "" : "; " + sa.note);
                            }
                        }
                    }
                }
                if (o.forced) {
                    for (Row row : rows) {
                        if (row.index != i + 1 || !row.nation.equals(o.actor)) continue;
                        Object[] so = row.stayOut.get(o.other);
                        if (so != null) {
                            war.stayOut = (Double)so[0];
                            war.stayOutHow = (String)so[1];
                        }
                    }
                }
                wars.add(war);
            }
        }
    }
}
