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
import java.util.List;


/**
 * A nation's public credit record (mod, ship loans): loans repaid,
 * defaults, ships seized.  Everyone can see it, so a nation that does
 * not pay cannot just borrow from someone else.
 */
public final class CreditRecord {

    /** Loans repaid that make a Good standing. */
    public static final int GOOD_REPAID = 2;
    /** Turns a plain default makes a Bad standing. */
    public static final int BAD_TURNS = 30;
    /** Turns before a default is forgotten. */
    public static final int FORGET_TURNS = 100;

    public enum Standing {
        /** Repays; never refused for its record. */
        GOOD,
        /** No (telling) history. */
        NEW,
        /** An old default, or a forgiven or cured one: dearer loans. */
        WARY,
        /** In default now, or lately: computer nations do not lend. */
        BAD
    }

    /** How a default ended (so far). */
    public enum Mark { PLAIN, FORGIVEN, CURED }

    /** One default. */
    public static final class Default {
        public final int turn;
        public Mark mark;

        public Default(int turn, Mark mark) {
            this.turn = turn;
            this.mark = mark;
        }
    }

    /** Loans repaid on time, repaid early, ships seized. */
    public int repaid, early, seized;
    /** A loan in default now. */
    public boolean inDefault;
    public final List<Default> defaults = new ArrayList<>();
    /** Lenders it defaulted on and did not make good with: they never
        lend to it again, it never asks them again. */
    public final List<String> burned = new ArrayList<>();


    public CreditRecord() {}

    /** Nothing on the record. */
    public boolean empty() {
        return repaid == 0 && early == 0 && seized == 0 && !inDefault
            && defaults.isEmpty() && burned.isEmpty();
    }

    /**
     * A new default.
     *
     * @param now The turn.
     * @param lender The lender's nation id.
     */
    public void defaulted(int now, String lender) {
        defaults.add(new Default(now, Mark.PLAIN));
        inDefault = true;
        if (lender != null && !burned.contains(lender)) burned.add(lender);
    }

    /**
     * The last default was forgiven or cured: a lighter mark, and the
     * lender may lend again.
     *
     * @param m The {@code Mark}.
     * @param lender The lender's nation id.
     */
    public void mark(Mark m, String lender) {
        if (!defaults.isEmpty()) defaults.get(defaults.size() - 1).mark = m;
        inDefault = false;
        burned.remove(lender);
    }

    /**
     * The standing now.
     *
     * @param now The turn.
     * @return The {@code Standing}.
     */
    public Standing standing(int now) {
        if (inDefault) return Standing.BAD;
        boolean wary = false, recent = false;
        for (Default d : defaults) {
            final int age = now - d.turn;
            if (age >= FORGET_TURNS) continue;
            recent = true;
            if (d.mark == Mark.PLAIN && age < BAD_TURNS) return Standing.BAD;
            wary = true;
        }
        if (wary) return Standing.WARY;
        return (!recent && repaid + early >= GOOD_REPAID) ? Standing.GOOD
            : Standing.NEW;
    }

    /** The defaults that still count, newest last. */
    public List<Default> recentDefaults(int now) {
        final List<Default> ret = new ArrayList<>();
        for (Default d : defaults) {
            if (now - d.turn < FORGET_TURNS) ret.add(d);
        }
        return ret;
    }


    // Serialization: "repaid,early,seized,inDefault,turn:MARK/...,lender/..."

    public String serialize() {
        final StringBuilder sb = new StringBuilder();
        for (Default d : defaults) {
            if (sb.length() > 0) sb.append('/');
            sb.append(d.turn).append(':').append(d.mark.name());
        }
        return repaid + "," + early + "," + seized + "," + inDefault + ","
            + sb + "," + String.join("/", burned);
    }

    /**
     * Read a record back.
     *
     * @param s The line from {@link #serialize}, may be null.
     * @return The {@code CreditRecord} (empty if it can not be read).
     */
    public static CreditRecord parse(String s) {
        final CreditRecord r = new CreditRecord();
        if (s == null || s.isEmpty()) return r;
        final String[] f = s.split(",", -1);
        if (f.length != 6) return r;
        try {
            r.repaid = Integer.parseInt(f[0]);
            r.early = Integer.parseInt(f[1]);
            r.seized = Integer.parseInt(f[2]);
            r.inDefault = Boolean.parseBoolean(f[3]);
            if (!f[4].isEmpty()) {
                for (String d : f[4].split("/")) {
                    final String[] g = d.split(":");
                    r.defaults.add(new Default(Integer.parseInt(g[0]),
                                               Mark.valueOf(g[1])));
                }
            }
            if (!f[5].isEmpty()) {
                for (String b : f[5].split("/")) r.burned.add(b);
            }
        } catch (RuntimeException e) {
            return new CreditRecord();
        }
        return r;
    }
}
