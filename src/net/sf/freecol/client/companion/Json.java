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

import java.awt.Color;


/**
 * A very small JSON writer, enough for the companion page.  Values are
 * written in order; commas between them are added automatically.
 *
 * <pre>
 *   Json j = new Json().obj().key("a").val(1).key("b").arr().val("x").end().end();
 * </pre>
 */
final class Json {

    private final StringBuilder sb = new StringBuilder(4096);

    /** Does the next value at the current level need a comma first? */
    private boolean comma = false;


    private void sep() {
        if (this.comma) this.sb.append(',');
        this.comma = true;
    }

    /** Open an object. */
    Json obj() {
        sep();
        this.sb.append('{');
        this.comma = false;
        return this;
    }

    /** Open an array. */
    Json arr() {
        sep();
        this.sb.append('[');
        this.comma = false;
        return this;
    }

    /** Close an object. */
    Json end() {
        this.sb.append('}');
        this.comma = true;
        return this;
    }

    /** Close an array. */
    Json endArr() {
        this.sb.append(']');
        this.comma = true;
        return this;
    }

    /** Write a key; the next call writes its value. */
    Json key(String k) {
        sep();
        str(k);
        this.sb.append(':');
        this.comma = false;
        return this;
    }

    Json val(String s) {
        sep();
        if (s == null) this.sb.append("null"); else str(s);
        return this;
    }

    Json val(long n) {
        sep();
        this.sb.append(n);
        return this;
    }

    Json val(boolean b) {
        sep();
        this.sb.append(b);
        return this;
    }

    Json val(Color c) {
        return val((c == null) ? null
            : String.format("#%02x%02x%02x", c.getRed(), c.getGreen(),
                            c.getBlue()));
    }

    /** Shorthands for a key with a value. */
    Json put(String k, String v) { return key(k).val(v); }
    Json put(String k, long v) { return key(k).val(v); }
    Json put(String k, boolean v) { return key(k).val(v); }
    Json put(String k, Color v) { return key(k).val(v); }

    private void str(String s) {
        this.sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
            case '"': this.sb.append("\\\""); break;
            case '\\': this.sb.append("\\\\"); break;
            case '\n': this.sb.append("\\n"); break;
            case '\r': this.sb.append("\\r"); break;
            case '\t': this.sb.append("\\t"); break;
            default:
                if (c < 0x20 || c == '<' || c == '>' || c == '&') {
                    this.sb.append(String.format("\\u%04x", (int)c));
                } else {
                    this.sb.append(c);
                }
            }
        }
        this.sb.append('"');
    }

    @Override
    public String toString() {
        return this.sb.toString();
    }
}
