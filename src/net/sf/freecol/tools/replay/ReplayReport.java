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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import net.sf.freecol.common.diplomacy.Action;
import net.sf.freecol.common.diplomacy.Diplomat;
import net.sf.freecol.common.diplomacy.ScoredAction;


/**
 * Writes the replay out: {@code replay.csv} (one row per turn per
 * nation), {@code replay.json} (everything, for the page) and the
 * summary figures.
 */
public final class ReplayReport {

    /** Figures per nation. */
    public static final class Summary {
        public int turns, moves, agreed, workedOut, agreedWorkedOut,
            unprompted, flips, wars, warsWithReason;
    }

    private ReplayReport() {}


    /**
     * Work out the summary figures per nation.
     *
     * @param r The {@code Replay}.
     * @return Nation id to {@code Summary}.
     */
    public static Map<String, Summary> summarise(Replay r) {
        Map<String, Summary> ret = new LinkedHashMap<>();
        Map<String, String> lastTop = new LinkedHashMap<>();
        for (Replay.Row row : r.rows) {
            Summary s = ret.computeIfAbsent(row.nation, n -> new Summary());
            s.turns++;
            s.moves += row.moves.size();
            for (int k = 0; k < row.moves.size(); k++) {
                boolean ok = row.outcome != null && row.outcome > 0;
                if (ok) s.workedOut++;
                if (row.agrees(k)) {
                    s.agreed++;
                    if (ok) s.agreedWorkedOut++;
                }
            }
            if (row.unprompted()) s.unprompted++;
            String top = (row.chosen == null) ? "" : key(row.chosen.action);
            String prev = lastTop.put(row.nation, top);
            if (prev != null && !prev.equals(top)) s.flips++;
        }
        for (Replay.War w : r.wars) {
            if (w.seen.forced) continue;
            Summary s = ret.get(w.seen.actor);
            if (s == null) continue;
            s.wars++;
            if (w.bestScore >= Diplomat.THRESHOLD) s.warsWithReason++;
        }
        return ret;
    }

    private static String key(Action a) {
        return a.kind + "|" + a.target;
    }

    /**
     * Write the csv.
     *
     * @param r The {@code Replay}.
     * @param out The file.
     * @exception IOException if it can not be written.
     */
    public static void csv(Replay r, File out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("turn,year,nation,human,did,diplomat_top3,chosen,chosen_score,"
                  + "agree,unprompted,outcome,goals\n");
        for (Replay.Row row : r.rows) {
            sb.append(row.turn).append(',')
                .append(q(row.label)).append(',')
                .append(Action.shortId(row.nation)).append(',')
                .append(row.human).append(',')
                .append(q(row.observed.stream().map(Observed::label)
                          .collect(Collectors.joining("; ")))).append(',')
                .append(q(row.top.stream().limit(Replay.AGREE_TOP)
                          .map(ScoredAction::toString)
                          .collect(Collectors.joining("; ")))).append(',')
                .append(q((row.chosen == null) ? "" : row.chosen.action.label()))
                .append(',')
                .append(fmt((row.chosen == null) ? 0 : row.chosen.total))
                .append(',')
                .append((row.moves.isEmpty()) ? "" : String.valueOf(row.agrees()))
                .append(',')
                .append(row.unprompted()).append(',')
                .append((row.outcome == null) ? "" : fmt(row.outcome))
                .append(',')
                .append(q(String.join("; ", row.goals))).append('\n');
        }
        Files.write(out.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Write everything as json, for the report page.
     *
     * @param r The {@code Replay}.
     * @param out The file.
     * @exception IOException if it can not be written.
     */
    public static void json(Replay r, File out) throws IOException {
        Files.write(out.toPath(),
                    json(r).getBytes(StandardCharsets.UTF_8));
    }

    public static String json(Replay r) {
        StringBuilder sb = new StringBuilder("{\"summary\":{");
        boolean first = true;
        for (Map.Entry<String, Summary> e : summarise(r).entrySet()) {
            Summary s = e.getValue();
            if (!first) sb.append(',');
            first = false;
            sb.append(str(Action.shortId(e.getKey()))).append(":{")
                .append("\"turns\":").append(s.turns)
                .append(",\"moves\":").append(s.moves)
                .append(",\"agreed\":").append(s.agreed)
                .append(",\"workedOut\":").append(s.workedOut)
                .append(",\"agreedWorkedOut\":").append(s.agreedWorkedOut)
                .append(",\"unprompted\":").append(s.unprompted)
                .append(",\"flips\":").append(s.flips)
                .append(",\"wars\":").append(s.wars)
                .append(",\"warsWithReason\":").append(s.warsWithReason)
                .append('}');
        }
        sb.append("},\"threshold\":").append(fmt(Diplomat.THRESHOLD));
        sb.append(",\"wars\":[");
        first = true;
        for (Replay.War w : r.wars) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"turn\":").append(w.turn)
                .append(",\"year\":").append(str(w.label))
                .append(",\"by\":").append(str(Action.shortId(w.seen.actor)))
                .append(",\"on\":").append(str(Action.shortId(w.seen.other)))
                .append(",\"forced\":").append(w.seen.forced)
                .append(",\"how\":").append(str(w.seen.detail))
                .append(",\"score\":").append((Double.isInfinite(w.bestScore))
                    ? "null" : fmt(w.bestScore))
                .append(",\"why\":").append(str(w.why))
                .append(",\"stay\":").append((w.stayOut == null) ? "null"
                                               : fmt(w.stayOut))
                .append(",\"stayHow\":").append(str(w.stayOutHow))
                .append('}');
        }
        sb.append("],\"rows\":[");
        first = true;
        for (Replay.Row row : r.rows) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"t\":").append(row.turn)
                .append(",\"y\":").append(str(row.label))
                .append(",\"n\":").append(str(Action.shortId(row.nation)))
                .append(",\"h\":").append(row.human)
                .append(",\"c\":").append(str(row.character.name().toLowerCase()))
                .append(",\"did\":[");
            boolean f2 = true;
            for (Observed o : row.observed) {
                if (!f2) sb.append(',');
                f2 = false;
                boolean mine = row.nation.equals(o.actor);
                sb.append("{\"l\":").append(str(o.label()))
                    .append(",\"w\":").append(str(o.what.name().toLowerCase()))
                    .append(",\"by\":").append(str(Action.shortId(o.actor)))
                    .append(",\"o\":").append(str((o.other == null) ? null
                                                  : Action.shortId(o.other)))
                    .append(",\"a\":").append(o.amount)
                    .append(",\"d\":").append(str(o.detail))
                    .append(",\"f\":").append(o.forced)
                    .append(",\"mine\":").append(mine).append('}');
            }
            sb.append("],\"ranks\":").append(row.ranks)
                .append(",\"top\":[");
            f2 = true;
            for (ScoredAction sa : row.top) {
                if (!f2) sb.append(',');
                f2 = false;
                final Action a = sa.action;
                sb.append("{\"l\":").append(str(a.label()))
                    .append(",\"k\":").append(str(a.kind.name().toLowerCase()))
                    .append(",\"to\":").append(str((a.target == null) ? null
                                                   : Action.shortId(a.target)))
                    .append(",\"vs\":").append(str((a.against == null) ? null
                                                   : Action.shortId(a.against)))
                    .append(",\"g\":").append(str((a.goods == null) ? null
                                                  : Action.shortId(a.goods)))
                    .append(",\"a\":").append(a.amount)
                    .append(",\"pr\":").append(a.price)
                    .append(",\"at\":").append(str(a.place))
                    .append(",\"s\":").append(fmt(sa.total))
                    .append(",\"p\":").append(str(sa.parts()))
                    .append(",\"note\":").append(str(sa.note)).append('}');
            }
            sb.append("],\"chosen\":").append(str((row.chosen == null) ? ""
                                                  : row.chosen.action.label()))
                .append(",\"ck\":").append(str((row.chosen == null) ? "nothing"
                    : row.chosen.action.kind.name().toLowerCase()))
                .append(",\"agree\":").append((row.moves.isEmpty()) ? "null"
                                              : String.valueOf(row.agrees()))
                .append(",\"unprompted\":").append(row.unprompted())
                .append(",\"out\":").append((row.outcome == null) ? "null"
                                            : fmt(row.outcome))
                .append(",\"goals\":").append(str(String.join(", ", row.goals)))
                .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Write the report page: the template {@code data/replay/report.html}
     * with the json put in.
     *
     * @param r The {@code Replay}.
     * @param template The template file.
     * @param out The page to write.
     * @exception IOException if a file can not be read or written.
     */
    public static void html(Replay r, File template, File out)
        throws IOException {
        String page = new String(Files.readAllBytes(template.toPath()),
                                 StandardCharsets.UTF_8);
        page = page.replace("/*REPLAY_DATA*/null", json(r));
        Files.write(out.toPath(), page.getBytes(StandardCharsets.UTF_8));
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    private static String q(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    static String str(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
            case '"': sb.append("\\\""); break;
            case '\\': sb.append("\\\\"); break;
            case '\n': sb.append("\\n"); break;
            case '<': sb.append("\\u003c"); break;
            default:
                if (c < 0x20) sb.append(String.format("\\u%04x", (int)c));
                else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
