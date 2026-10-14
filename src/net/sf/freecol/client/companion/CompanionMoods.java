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

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.diplomacy.Goal;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.Mood;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.ai.EuropeanAIPlayer;


/**
 * How the computer nations feel about the player, for their cards on
 * the Nations tab: in words, not numbers.  Only in a single player
 * game, where the server (and its computer players) runs in this
 * program.  What a nation thinks of others is shown only when it is
 * public: they are at war or allied.
 */
final class CompanionMoods {

    private static final Logger logger = Logger.getLogger(CompanionMoods.class.getName());

    private CompanionMoods() {}


    /**
     * The mood of a computer nation, as of its last turn.
     *
     * @param fcc The {@code FreeColClient}.
     * @param p The (client side) {@code Player}.
     * @return The {@code Mood}, or null if not known.
     */
    static Mood moodOf(FreeColClient fcc, Player p) {
        if (fcc == null || !fcc.getSinglePlayer()) return null;
        final FreeColServer server = fcc.getFreeColServer();
        if (server == null || server.getGame() == null
            || server.getAIMain() == null) return null;
        try {
            final Player sp = server.getGame()
                .getPlayerByNationId(p.getNationId());
            if (sp == null || !sp.isAI()) return null;
            final AIPlayer ai = server.getAIMain().getAIPlayer(sp);
            return (ai instanceof EuropeanAIPlayer)
                ? ((EuropeanAIPlayer)ai).getMood() : null;
        } catch (RuntimeException e) { // The server is busy with its turn
            logger.log(Level.FINE, "Mood of " + p.getNationId(), e);
            return null;
        }
    }

    /**
     * Write a nation's mood towards the player as a "mood" object.
     *
     * @param j The {@code Json} to write to.
     * @param m The {@code Mood}, null writes nothing.
     * @param game The client {@code Game}.
     * @param me The client {@code Player}.
     */
    static void put(Json j, Mood m, Game game, Player me) {
        if (m == null) return;
        final Mood.Feeling f = m.about(me.getNationId());
        if (f == null) return;
        j.key("mood").obj()
            .put("word", f.mood())
            .put("trust", f.trustWord())
            .put("ambition", ambition(m))
            .put("truce", Mood.TRUCE.equals(f.why));
        j.key("goals").arr();
        for (Goal g : m.goals) {
            final String s = goal(g, game, me, m);
            if (s != null) j.val(s);
        }
        j.endArr();
        j.key("remembers").arr();
        for (Memory x : f.memories) {
            j.obj().put("text", memory(x))
                .put("year", Messages.message(new Turn(x.turn).getLabel()))
                .put("good", x.kind.weight > 0)
                .end();
        }
        j.endArr();
        // Coalitions are public.
        j.key("coalitions").arr();
        for (String t : m.coalitionTargets) {
            final Player q = game.getPlayerByNationId(t);
            if (q == null) continue;
            j.obj().put("name", (q == me) ? "you"
                    : Messages.message(q.getNationLabel()))
                .put("you", q == me).end();
        }
        j.endArr();
        // Their feelings about others: only wars and alliances are public.
        j.key("feelings").arr();
        for (Mood.Feeling o : m.feelings.values()) {
            if (o.about.equals(me.getNationId())) continue;
            if (o.stance != Stance.WAR && o.stance != Stance.ALLIANCE) continue;
            final Player q = game.getPlayerByNationId(o.about);
            if (q == null || !met(me, q)) continue;
            j.obj().put("name", Messages.message(q.getNationLabel()))
                .put("color", q.getNationColor())
                .put("stance", (o.stance == Stance.WAR) ? "war" : "alliance")
                .put("word", o.mood())
                .put("why", o.why)
                .end();
        }
        j.endArr();
        j.end();
    }

    private static boolean met(Player me, Player q) {
        final Stance s = me.getStance(q);
        return q == me || (s != null && s != Stance.UNCONTACTED);
    }

    /** Their long-term ambition in words. */
    static String ambition(Mood m) {
        switch (m.character) {
        case TRADING_EMPIRE: return "trading empire";
        case CONQUEROR: return "conqueror";
        case BALANCE_KEEPER: return "keeps the balance";
        case INDEPENDENCE_FIRST: return "independence first";
        case SETTLER: default: return "settler";
        }
    }

    /**
     * A goal in words, or null when the player could not know it.
     * Only what a nation makes known: the goods it wants to sell or
     * buy (it posts them on the notice board anyway) and that it wants
     * peace with the player.  War plans (taking a colony, revenge,
     * containing a leader, defending) stay secret: they show as
     * grievances and ultimatums (step 6) or a public coalition.
     */
    static String goal(Goal g, Game game, Player me, Mood m) {
        switch (g.type) {
        case SELL_SURPLUS: return "sell " + goods(game, g.target);
        case GET_GOODS: return "get " + goods(game, g.target);
        case MAKE_PEACE:
            return (me.getNationId().equals(g.target)) ? "make peace with you"
                : null;
        default:
            return null;
        }
    }

    private static String goods(Game game, String id) {
        final GoodsType gt = game.getSpecification().getGoodsType(id);
        return (gt == null) ? id : Messages.getName(gt).toLowerCase();
    }

    /** What they remember about the player, in words. */
    static String memory(Memory m) {
        final int n = m.count;
        switch (m.kind) {
        case WAR: return "war with you";
        case COLONY_TAKEN: return "you took a colony";
        case PEACE: return "peace with you";
        case UNIT_LOST: return (n == 1) ? "lost a unit or ship to you"
            : "lost " + n + " units or ships to you";
        case SHIP_DAMAGED: return (n == 1) ? "you damaged a ship"
            : "you damaged " + n + " ships";
        case SETTLEMENT_DESTROYED: return "you destroyed a settlement";
        case DEAL_KEPT: return "you kept the peace";
        case DEAL_BROKEN: return "you broke your word";
        case DEAL_LATE: return "you delivered late";
        case BETRAYER: return "you turned on your ally";
        case GIFT: return "your gift";
        case TRADE: return "trade with you";
        case HELPED_IN_WAR: return "you fought their enemy";
        case REFUSED_OFFER: return "you turned down their offer";
        default: return m.kind.name().toLowerCase();
        }
    }
}
