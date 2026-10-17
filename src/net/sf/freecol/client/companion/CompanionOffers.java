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
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.ai.EuropeanAIPlayer;


/**
 * The notice board and peace terms on the companion page: only the
 * offers the player was shown or took (single player games, where the
 * computer players run in this program).  Read only: the player answers
 * in the game.
 */
final class CompanionOffers {

    private static final Logger logger = Logger.getLogger(CompanionOffers.class.getName());

    private CompanionOffers() {}


    /**
     * The offers the player knows of, copied.
     *
     * @param fcc The {@code FreeColClient}.
     * @param me The client {@code Player}.
     * @return The offers.
     */
    static List<Offer> known(FreeColClient fcc, Player me) {
        List<Offer> ret = new ArrayList<>();
        if (fcc == null || !fcc.getSinglePlayer()) return ret;
        final FreeColServer server = fcc.getFreeColServer();
        if (server == null || server.getGame() == null
            || server.getAIMain() == null) return ret;
        final String id = me.getNationId();
        final java.util.Set<String> seen = new java.util.HashSet<>();
        try {
            for (Player p : server.getGame().getLivePlayerList()) {
                final AIPlayer ai = server.getAIMain().getAIPlayer(p);
                // The Europeans' offers, and the tribes' (mod, step 7).
                final List<Offer> book = (ai instanceof EuropeanAIPlayer)
                    ? ((EuropeanAIPlayer)ai).getOffers()
                    : (ai instanceof net.sf.freecol.server.ai.NativeAIPlayer)
                    ? ((net.sf.freecol.server.ai.NativeAIPlayer)ai).getOffers()
                    : null;
                if (book == null) continue;
                for (Offer o : new ArrayList<>(book)) {
                    if (o.kind == Offer.Kind.SALE) continue; // a dialog only
                    if ((o.told.contains(id) || id.equals(o.takenBy)
                         || id.equals(o.from)) && seen.add(o.id)) {
                        final Offer c = Offer.parse(o.serialize());
                        if (c != null) ret.add(c);
                    }
                }
            }
        } catch (RuntimeException e) { // The server is busy with its turn
            logger.log(Level.FINE, "Offers", e);
        }
        return ret;
    }

    /**
     * The offers as JSON.
     *
     * @param offers The offers the player knows of.
     * @param game The client {@code Game}.
     * @param me The client {@code Player}.
     * @return The JSON.
     */
    static String build(List<Offer> offers, Game game, Player me) {
        final int now = game.getTurn().getNumber();
        Json j = new Json().obj().put("turn", now);
        j.key("offers").arr();
        offers.sort((a, b) -> b.posted - a.posted);
        for (Offer o : offers) {
            final Player from = game.getPlayerByNationId(o.from);
            final GoodsType gt = (o.goods == null) ? null
                : game.getSpecification().getGoodsType(o.goods);
            final boolean mine = me.getNationId().equals(o.takenBy);
            final boolean yours = me.getNationId().equals(o.from);
            final Player taker = (o.takenBy == null) ? null
                : game.getPlayerByNationId(o.takenBy);
            j.obj().put("id", o.id)
                .put("kind", o.kind.name().toLowerCase())
                .put("from", (from == null) ? o.from
                    : Messages.message(from.getNationLabel()))
                .put("color", (from == null) ? java.awt.Color.GRAY
                    : from.getNationColor())
                .put("against", (o.against == null
                        || game.getPlayerByNationId(o.against) == null) ? ""
                    : Messages.message(game.getPlayerByNationId(o.against)
                        .getNationLabel()))
                .put("goods", (gt == null) ? "" : gt.getId())
                .put("goodsName", (gt == null) ? "" : Messages.getName(gt))
                .put("amount", o.amount)
                .put("gold", o.gold)
                // Envoys (step 8): who it went to; our terms: for what.
                .put("to", (o.audience.isEmpty()
                        || game.getPlayerByNationId(o.audience.get(0)) == null) ? ""
                    : Messages.message(game.getPlayerByNationId(o.audience.get(0))
                        .getNationLabel()))
                .put("deal", (o.kind == Offer.Kind.TERMS && o.goods != null)
                    ? o.goods.toLowerCase() : "")
                .put("place", o.placeName)
                .put("state", o.state.name().toLowerCase())
                .put("mine", mine)
                .put("yours", yours)
                .put("takenBy", (taker == null) ? ""
                    : Messages.message(taker.getNationLabel()))
                .put("deadline", o.deadline)
                .put("posted", Messages.message(
                    new net.sf.freecol.common.model.Turn(o.posted).getLabel()));
            if (mine && o.state == Offer.State.TAKEN) {
                j.put("left", o.turnsLeft(now)).put("priceNow", o.priceAt(now));
            }
            if (o.paid > 0) j.put("paid", o.paid);
            if (o.delivered > 0) j.put("delivered", o.delivered);
            j.put("tribe", from != null && from.isIndian());
            j.end();
        }
        j.endArr();
        return j.end().toString();
    }
}
