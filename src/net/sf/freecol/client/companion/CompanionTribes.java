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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.diplomacy.Memories;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.Mood;
import net.sf.freecol.common.diplomacy.TribeMood;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.ai.NativeAIPlayer;
import net.sf.freecol.server.model.TribeNews;


/**
 * What a tribe thinks, for its card on the Diplomacy tab (mod, step
 * 7g).  Only in a single player game.  Honest: its feelings about you,
 * its unrest against you (it speaks of war openly) and what it
 * remembers of you; its friends among the tribes only when you have a
 * mission with it (your missionaries tell you), its old rival also
 * when they are at war (wars are public); the news everyone hears.
 */
final class CompanionTribes {

    private static final Logger logger = Logger.getLogger(CompanionTribes.class.getName());

    private CompanionTribes() {}


    /**
     * The server's tribe player for a client player.
     *
     * @param fcc The {@code FreeColClient}.
     * @param p The client tribe.
     * @return The {@code NativeAIPlayer}, or null.
     */
    static NativeAIPlayer aiOf(FreeColClient fcc, Player p) {
        if (fcc == null || !fcc.getSinglePlayer()) return null;
        final FreeColServer server = fcc.getFreeColServer();
        if (server == null || server.getGame() == null
            || server.getAIMain() == null) return null;
        final Player sp = server.getGame().getPlayerByNationId(p.getNationId());
        if (sp == null) return null;
        final AIPlayer ai = server.getAIMain().getAIPlayer(sp);
        return (ai instanceof NativeAIPlayer) ? (NativeAIPlayer)ai : null;
    }

    /**
     * Write a tribe's thoughts as a "think" object.
     *
     * @param j The {@code Json} to write to.
     * @param fcc The {@code FreeColClient}.
     * @param game The client {@code Game}.
     * @param me The client {@code Player}.
     * @param tribe The client tribe {@code Player}.
     */
    static void put(Json j, FreeColClient fcc, Game game, Player me,
                    Player tribe) {
        try {
            final NativeAIPlayer ai = aiOf(fcc, tribe);
            final TribeMood m = (ai == null) ? null : ai.getMood();
            final List<TribeNews.News> news = (fcc == null
                || fcc.getFreeColServer() == null) ? new ArrayList<>()
                : TribeNews.recent(fcc.getFreeColServer().getGame());
            put(j, m, news, game, me, tribe);
        } catch (RuntimeException e) { // The server is busy with its turn
            logger.log(Level.FINE, "Tribe " + tribe.getNationId(), e);
        }
    }

    /**
     * Write a tribe's thoughts (testable part).
     *
     * @param j The {@code Json} to write to.
     * @param m The {@code TribeMood}, may be null.
     * @param news The recent tribe news.
     * @param game The client {@code Game}.
     * @param me The client {@code Player}.
     * @param tribe The client tribe {@code Player}.
     */
    static void put(Json j, TribeMood m, List<TribeNews.News> news, Game game,
                    Player me, Player tribe) {
        // Allied settlements are public.
        j.key("allied").arr();
        for (IndianSettlement is : tribe.getIndianSettlementList()) {
            final Player a = is.getAlliedPlayer();
            if (a == null) continue;
            j.obj().put("name", is.getName())
                .put("ally", (a == me) ? "you" : Messages.message(a.getNationLabel()))
                .put("you", a == me).end();
        }
        j.endArr();
        j.key("news").arr();
        for (TribeNews.News n : news) {
            final String s = newsText(n, game, me, tribe);
            if (s == null) continue;
            j.obj().put("text", s)
                .put("year", Messages.message(new Turn(n.turn).getLabel()))
                .put("bad", n.kind == TribeNews.Kind.UPRISING
                     || n.kind == TribeNews.Kind.UNREST
                     || n.kind == TribeNews.Kind.SIDED_WITH)
                .end();
        }
        j.endArr();
        if (m == null) return;
        final String id = me.getNationId();
        final double opinion = m.opinions.getOrDefault(id, 0.0);
        final double trust = m.trust.getOrDefault(id, 50.0);
        j.key("think").obj()
            .put("character", m.character.word)
            .put("word", Mood.moodWord(opinion))
            .put("trust", Mood.trustWord(trust));
        final Integer unrest = m.unrest.get(id);
        if (unrest != null) {
            j.put("unrest", unrest.intValue()).put("unrestOf", m.unrestTurns);
        }
        // What they remember of you.
        j.key("remembers").arr();
        int n = 0;
        for (Memory x : Memories.about(id, m.turn, m.memories)) {
            if (n++ >= 4) break;
            j.obj().put("text", CompanionMoods.memory(x))
                .put("good", x.kind.weight > 0).end();
        }
        j.endArr();
        // Friends and the old rival: known through your missionaries;
        // a war with the rival is public.
        final boolean mission = hasMission(tribe, me);
        j.put("told", mission);
        final Player rival = (m.rival == null) ? null
            : game.getPlayerByNationId(m.rival);
        final boolean atWar = rival != null && m.atWar.contains(m.rival);
        if (rival != null && (mission || atWar)) {
            j.key("rival").obj()
                .put("name", Messages.message(rival.getNationLabel()))
                .put("color", rival.getNationColor())
                .put("war", atWar).end();
        }
        j.key("friends").arr();
        if (mission) {
            final List<String> tribes = new ArrayList<>();
            for (Player p : game.getLiveNativePlayerList()) tribes.add(p.getNationId());
            for (String f : m.friends(tribes)) {
                final Player q = game.getPlayerByNationId(f);
                if (q == null) continue;
                j.obj().put("name", Messages.message(q.getNationLabel()))
                    .put("color", q.getNationColor()).end();
            }
        }
        j.endArr();
        j.end();
    }

    private static boolean hasMission(Player tribe, Player me) {
        for (IndianSettlement is : tribe.getIndianSettlementList()) {
            if (is.getMissionary() != null && is.getMissionary().getOwner() == me) {
                return true;
            }
        }
        return false;
    }

    private static String name(Game game, Player me, String id) {
        if (id == null) return "";
        if (id.equals(me.getNationId())) return "you";
        final Player p = game.getPlayerByNationId(id);
        return (p == null) ? id : Messages.message(p.getNationLabel());
    }

    /** News about this tribe in words, or null if not for this card. */
    static String newsText(TribeNews.News n, Game game, Player me, Player tribe) {
        final String t = tribe.getNationId();
        final boolean actor = t.equals(n.tribe);
        if (!actor && !t.equals(n.other)) return null;
        final String a = name(game, me, n.tribe), o = name(game, me, n.other),
            e = name(game, me, n.european);
        switch (n.kind) {
        case HELP:
            return (actor) ? "came to the aid of the " + o + " against " + e
                : "the " + a + " came to their aid against " + e;
        case SIDED_WITH:
            return (actor) ? "sided with " + e + " against the " + o
                : "their old rival the " + a + " sided with " + e;
        case REFUGEES:
            return (n.other == null) ? "survivors fled: " + n.place
                : (actor) ? "took in refugees of the " + o + " (" + n.place + ")"
                : "survivors fled to the " + a + " (" + n.place + ")";
        case UPRISING:
            return (actor) ? "rose against " + e
                + (n.place.isEmpty() ? "" : " (near " + n.place + ")") : null;
        case UNREST:
            return (actor && me.getNationId().equals(n.european))
                ? "speaks of war against you" : null;
        case JOINED:
            return (actor) ? ((n.place.isEmpty()) ? "allied with " + e
                : n.place + " broke off to " + e) : null;
        case BROKE_AWAY:
            return (actor) ? "broke away from the " + o + " under " + e
                + " (" + n.place + ")"
                : n.place + " broke away and founded the " + a
                + ", under " + e;
        default:
            return null; // who stayed home is the caller's business
        }
    }
}
