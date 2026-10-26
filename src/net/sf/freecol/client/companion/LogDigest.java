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

import java.util.Map;
import java.util.TreeMap;


/**
 * Sorts the log's entries for the page (mod): which group a line goes
 * in (news, deals, warnings, routes, units), and the key that tells
 * the same warning coming back turn after turn.  Pure rules, no game.
 */
final class LogDigest {

    /** The groups, in the order the page shows them. */
    static final String NEWS = "news", DEAL = "deal", WARN = "warn",
        ROUTE = "route", UNIT = "unit";

    private LogDigest() {}


    /**
     * The group of an entry.
     *
     * @param kind The log kind ("msg-warning", "load", "arrive"...).
     * @param templateId The message's template id, or null.
     * @return The group.
     */
    static String group(String kind, String templateId) {
        if (kind == null) return NEWS;
        switch (kind) {
        case "load": case "unload":
            return ROUTE;
        case "arrive":
            return UNIT;
        case "natives":
            return DEAL;
        case "colony": case "lost":
            return NEWS;
        default:
            break;
        }
        final String id = (templateId == null) ? "" : templateId;
        if (id.startsWith("offer.") || id.startsWith("envoy.")
            || id.startsWith("loan.") || id.startsWith("agreement.")) {
            return DEAL;
        }
        switch (kind) {
        case "msg-warehouse_capacity": case "msg-missing_goods":
        case "msg-warning": case "msg-government_efficiency":
            return WARN;
        case "msg-unit_arrived": case "msg-unit_added":
        case "msg-unit_repaired": case "msg-unit_demoted":
            return UNIT;
        case "msg-market_prices":
            return DEAL;
        default:
            return NEWS;
        }
    }

    /**
     * The key of a message that may come back every turn: the same
     * template about the same thing, whatever the numbers.
     *
     * @param templateId The template id.
     * @param sourceId The message's source id, or null.
     * @param values The filled-in values by key ("%colony%"...).
     * @return The key.
     */
    static String repeatKey(String templateId, String sourceId,
                            Map<String, String> values) {
        final StringBuilder sb = new StringBuilder(String.valueOf(templateId))
            .append('|').append((sourceId == null) ? "" : sourceId);
        if (values != null) {
            for (Map.Entry<String, String> e : new TreeMap<>(values).entrySet()) {
                final String v = e.getValue();
                if (v == null || v.matches("[-+]?[0-9.,%]+")) continue;
                sb.append('|').append(e.getKey()).append('=').append(v);
            }
        }
        return sb.toString();
    }
}
