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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.Action;


/**
 * Step 0 from the command line:
 * <pre>
 * java -cp FreeCol.jar net.sf.freecol.tools.replay.ReplayMain \
 *     SAVE_FOLDER FILE_PREFIX OUT_FOLDER [declared=iroquois,french]
 * </pre>
 * Reads the saves (never writes them), writes replay.csv and
 * replay.json to OUT_FOLDER and prints the summary.  "declared" names
 * the nations the human player declared war on himself; his other
 * wars are counted as the King's.
 */
public final class ReplayMain {

    private ReplayMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: ReplayMain SAVE_FOLDER FILE_PREFIX"
                + " OUT_FOLDER [declared=nation,nation]");
            System.exit(1);
        }
        Logger.getLogger("").setLevel(Level.WARNING);
        for (var h : Logger.getLogger("").getHandlers()) h.setLevel(Level.WARNING);
        Set<String> declared = new HashSet<>();
        for (int i = 3; i < args.length; i++) {
            if (args[i].startsWith("declared=")) {
                for (String n : args[i].substring(9).split(",")) {
                    if (!n.isEmpty()) {
                        declared.add(n.startsWith("model.") ? n
                                     : "model.nation." + n);
                    }
                }
            }
        }
        List<SaveSeries.Entry> saves = SaveSeries.find(new File(args[0]),
                                                       args[1]);
        System.out.println("saves: " + saves.size() + ", turns "
            + saves.get(0).turn + " to " + saves.get(saves.size() - 1).turn);
        long t0 = System.currentTimeMillis();
        Replay r = new Replay();
        r.run(saves, declared, t -> {
            if (t % 25 == 0) System.out.println("  turn " + t);
        });
        File out = new File(args[2]);
        out.mkdirs();
        ReplayReport.csv(r, new File(out, "replay.csv"));
        ReplayReport.json(r, new File(out, "replay.json"));
        File template = new File("data/replay/report.html");
        if (template.isFile()) {
            ReplayReport.html(r, template, new File(out, "replay.html"));
        }
        System.out.println("done in "
            + (System.currentTimeMillis() - t0) / 1000 + " s");
        for (Map.Entry<String, ReplayReport.Summary> e
                 : ReplayReport.summarise(r).entrySet()) {
            ReplayReport.Summary s = e.getValue();
            System.out.printf("%-10s moves %3d, in top 3: %3d, worked out %3d"
                + " (of which in top 3: %3d), unprompted %3d of %3d turns,"
                + " top move changed %3d times, own wars %d (with a reason %d)%n",
                Action.shortId(e.getKey()), s.moves, s.agreed, s.workedOut,
                s.agreedWorkedOut, s.unprompted, s.turns, s.flips, s.wars,
                s.warsWithReason);
        }
        for (Replay.War w : r.wars) {
            System.out.printf("war %s %s -> %s %s best score %s%n", w.label,
                Action.shortId(w.seen.actor), Action.shortId(w.seen.other),
                (w.seen.forced) ? "[forced: " + w.seen.detail + "]"
                                : "(" + w.seen.detail + ")",
                Double.isInfinite(w.bestScore) ? "-"
                    : String.format("%.1f", w.bestScore));
        }
    }
}
