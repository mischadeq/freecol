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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.diplomacy.Coalition;
import net.sf.freecol.common.diplomacy.TribeMood;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.ai.NativeAIPlayer;


/**
 * The relations graph on the Diplomacy tab (mod, step 5d): every nation
 * and tribe you met as a dot, wars and alliances as lines (public),
 * old rivals (as on the tribe cards: through your mission, or when at
 * war), settlements allied to a European (public), and the coalitions.
 * Without the server (multiplayer) only your own wars and alliances.
 */
final class CompanionGraph {

    private static final Logger logger = Logger.getLogger(CompanionGraph.class.getName());

    private CompanionGraph() {}


    /**
     * Write the graph as a "graph" object.
     *
     * @param j The {@code Json} to write to.
     * @param fcc The {@code FreeColClient}, may be null.
     * @param game The client {@code Game}.
     * @param me The client {@code Player}.
     */
    static void put(Json j, FreeColClient fcc, Game game, Player me) {
        Game sg = null;
        final Map<String, TribeMood> moods = new HashMap<>();
        final List<Coalition> coalitions = new ArrayList<>();
        try {
            final FreeColServer server = (fcc == null || !fcc.getSinglePlayer())
                ? null : fcc.getFreeColServer();
            if (server != null && server.getGame() != null
                && server.getAIMain() != null) {
                sg = server.getGame();
                for (Player p : sg.getLivePlayerList()) {
                    final AIPlayer ai = server.getAIMain().getAIPlayer(p);
                    if (ai instanceof NativeAIPlayer
                        && ((NativeAIPlayer)ai).getMood() != null) {
                        moods.put(p.getNationId(), ((NativeAIPlayer)ai).getMood());
                    } else if (ai instanceof EuropeanAIPlayer) {
                        coalitions.addAll(((EuropeanAIPlayer)ai).getCoalitions());
                    }
                }
            }
        } catch (RuntimeException e) { // The server is busy with its turn
            logger.log(Level.FINE, "Graph", e);
        }
        put(j, game, me, sg, moods, coalitions);
    }

    /**
     * Write the graph (testable part).
     *
     * @param j The {@code Json} to write to.
     * @param game The client {@code Game}.
     * @param me The client {@code Player}.
     * @param sg The server {@code Game} for the others' stances, or null.
     * @param moods The tribes' moods by nation id.
     * @param coalitions The coalitions.
     */
    static void put(Json j, Game game, Player me, Game sg,
                    Map<String, TribeMood> moods, List<Coalition> coalitions) {
        final Map<String, Player> nodes = new LinkedHashMap<>();
        nodes.put(me.getNationId(), me);
        for (Player p : game.getLivePlayerList(me)) {
            if (p.isREF() || p.isUnknownEnemy() || p.isUndead()) continue;
            if (me.hasContacted(p)) nodes.put(p.getNationId(), p);
        }
        j.key("graph").obj();
        j.key("nodes").arr();
        for (Player p : nodes.values()) {
            j.obj().put("id", p.getNationId())
                .put("name", (p == me) ? "You" : Messages.message(p.getNationLabel()))
                .put("color", p.getNationColor())
                .put("tribe", p.isIndian())
                .put("me", p == me).end();
        }
        j.endArr();
        j.key("edges").arr();
        final List<String> ids = new ArrayList<>(nodes.keySet());
        for (int a = 0; a < ids.size(); a++) {
            for (int b = a + 1; b < ids.size(); b++) {
                final Stance s = stance(nodes.get(ids.get(a)),
                                        nodes.get(ids.get(b)), me, sg);
                if (s == Stance.WAR || s == Stance.ALLIANCE) {
                    edge(j, ids.get(a), ids.get(b),
                         (s == Stance.WAR) ? "war" : "alliance", 1);
                }
            }
        }
        for (Player t : nodes.values()) {
            if (!t.isIndian()) continue;
            // Old rivals, as the tribe cards show them.
            final TribeMood m = moods.get(t.getNationId());
            if (m != null && m.rival != null && nodes.containsKey(m.rival)
                && (m.atWar.contains(m.rival) || hasMission(t, me))) {
                edge(j, t.getNationId(), m.rival, "rival", 1);
            }
            // Settlements allied to a European (public).
            final Map<String, Integer> allied = new LinkedHashMap<>();
            for (IndianSettlement is : t.getIndianSettlementList()) {
                final Player x = is.getAlliedPlayer();
                if (x != null && nodes.containsKey(x.getNationId())) {
                    allied.merge(x.getNationId(), 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> e : allied.entrySet()) {
                edge(j, t.getNationId(), e.getKey(), "settlement", e.getValue());
            }
        }
        j.endArr();
        j.key("coalitions").arr();
        for (Coalition c : coalitions) {
            if (c.members.size() < 2 || !nodes.containsKey(c.target)) continue;
            j.obj().put("target", c.target);
            j.key("members").arr();
            for (String m : c.members) if (nodes.containsKey(m)) j.val(m);
            j.endArr().end();
        }
        j.endArr();
        j.end();
    }

    private static void edge(Json j, String a, String b, String kind, int n) {
        j.obj().put("a", a).put("b", b).put("kind", kind).put("n", n).end();
    }

    /**
     * The stance between two nations, as far as the player may know:
     * its own from its game, the others' (public) from the server.
     */
    private static Stance stance(Player a, Player b, Player me, Game sg) {
        if (a == me) return me.getStance(b);
        if (b == me) return me.getStance(a);
        if (sg == null) return null;
        final Player sa = sg.getPlayerByNationId(a.getNationId());
        final Player sb = sg.getPlayerByNationId(b.getNationId());
        return (sa == null || sb == null) ? null : sa.getStance(sb);
    }

    private static boolean hasMission(Player tribe, Player me) {
        for (IndianSettlement is : tribe.getIndianSettlementList()) {
            if (is.getMissionary() != null && is.getMissionary().getOwner() == me) {
                return true;
            }
        }
        return false;
    }
}
