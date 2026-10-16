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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * What to highlight in a log line on the companion page (mod,
 * 2026-10-17), so the important words stand out:
 * <ul>
 * <li><b>bold</b>: who and where (the nations, tribes, colonies, places
 *     and goods the game filled into the message) and what went wrong
 *     (exceeded its capacity, shortage, missing, concerned...);</li>
 * <li><b>green</b>: good news (peace, alliance, joined, completed,
 *     arrived);</li>
 * <li><b>red</b>: bad news (war, attacked, destroyed, captured...).</li>
 * </ul>
 * The result is a list of runs {start, end, flags} over the text.
 */
public final class CompanionHighlight {

    public static final int BOLD = 1, GOOD = 2, BAD = 4;

    /** Trouble: bold. */
    private static final Pattern TROUBLE = Pattern.compile(
        "\\b(?:will )?exceed(?:ed)? its capacity\\b|\\bwasted\\b"
        + "|\\bshortage\\b|\\bmissing\\b|\\bstopped production\\b"
        + "|\\bconcerned\\b|\\bencroachments?\\b|\\bour lands\\b"
        + "|\\bstarv\\w*|\\bfamine\\b|\\brun(?:ning)? out\\b|\\bran out\\b"
        + "|\\bdemands?\\b|\\bunrest\\b|\\bboycott\\w*",
        Pattern.CASE_INSENSITIVE);

    /** Good news: green. */
    private static final Pattern GOOD_NEWS = Pattern.compile(
        "\\bat peace\\b|\\bpeace\\b|\\balliance\\b|\\ballied\\b"
        + "|\\bjoined\\b|\\bcompleted\\b|\\barrived\\b|\\bcooperation\\b"
        + "|\\bfriends\\b|\\bagreed\\b|\\baccepted\\b",
        Pattern.CASE_INSENSITIVE);

    /** Bad news: red. */
    private static final Pattern BAD_NEWS = Pattern.compile(
        "\\bdeclared war\\b|\\bwar\\b|\\battack(?:ed|s|ing)?\\b"
        + "|\\bdestroyed\\b|\\bcaptured\\b|\\bburn(?:ed|t)\\b|\\blost\\b"
        + "|\\bkilled\\b|\\bslaughtered\\b|\\bambush(?:ed)?\\b"
        + "|\\braid(?:ed|s)?\\b|\\bplunder(?:ed)?\\b|\\bpillag(?:ed|e)\\b"
        + "|\\bdefeated\\b|\\buprising\\b|\\brevolt\\b",
        Pattern.CASE_INSENSITIVE);

    private CompanionHighlight() {}


    /**
     * The runs to highlight in a line.
     *
     * @param text The line.
     * @param names The names filled into it (nations, colonies, goods,
     *     places); numbers and very short names are left alone.
     * @return Runs {start, end, flags}, in order, not overlapping.
     */
    public static List<int[]> runs(String text, Collection<String> names) {
        final List<int[]> ret = new ArrayList<>();
        if (text == null || text.isEmpty()) return ret;
        final int[] flags = new int[text.length()];
        if (names != null) {
            for (String n : names) {
                if (n == null) continue;
                final String name = n.trim();
                if (name.length() < 3 || name.matches("[\\d.,\\s%+-]+")) continue;
                int at = 0;
                while ((at = text.indexOf(name, at)) >= 0) {
                    // Whole words only (not "Arawak" inside "Arawaks'").
                    final int end = at + name.length();
                    final boolean startOk = at == 0
                        || !Character.isLetterOrDigit(text.charAt(at - 1));
                    final boolean endOk = end == text.length()
                        || !Character.isLetterOrDigit(text.charAt(end))
                        || text.charAt(end) == 's';
                    if (startOk && endOk) mark(flags, at, end, BOLD);
                    at = end;
                }
            }
        }
        mark(flags, TROUBLE.matcher(text), BOLD);
        mark(flags, GOOD_NEWS.matcher(text), GOOD);
        mark(flags, BAD_NEWS.matcher(text), BAD);
        // Bad news wins over good ("lost the peace").
        for (int i = 0; i < flags.length; i++) {
            if ((flags[i] & BAD) != 0) flags[i] &= ~GOOD;
        }
        int i = 0;
        while (i < flags.length) {
            if (flags[i] == 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < flags.length && flags[j] == flags[i]) j++;
            ret.add(new int[] { i, j, flags[i] });
            i = j;
        }
        return ret;
    }

    private static void mark(int[] flags, Matcher m, int flag) {
        while (m.find()) mark(flags, m.start(), m.end(), flag);
    }

    private static void mark(int[] flags, int from, int to, int flag) {
        for (int k = Math.max(0, from); k < Math.min(flags.length, to); k++) {
            flags[k] |= flag;
        }
    }
}
