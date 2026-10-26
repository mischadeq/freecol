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
     * The ship loans the player made (mod), copied, with what is due
     * next: [loan, next payment] pairs.
     *
     * @param fcc The {@code FreeColClient}.
     * @param me The client {@code Player}.
     * @return The loans.
     */
    static List<Object[]> loans(FreeColClient fcc, Player me) {
        final List<Object[]> ret = new ArrayList<>();
        if (fcc == null || !fcc.getSinglePlayer()) return ret;
        final FreeColServer server = fcc.getFreeColServer();
        if (server == null || server.getGame() == null
            || server.getAIMain() == null) return ret;
        try {
            for (net.sf.freecol.common.diplomacy.Loan l
                     : net.sf.freecol.server.ai.diplomacy.LoanDesk.all(
                         server.getAIMain())) {
                if (!me.getNationId().equals(l.lender)) continue;
                final Player b = server.getGame().getPlayerByNationId(l.borrower);
                final Player lp = server.getGame().getPlayerByNationId(l.lender);
                final boolean war = b != null && lp != null
                    && b.getStance(lp) == net.sf.freecol.common.model.Stance.WAR;
                final net.sf.freecol.common.diplomacy.Loan c
                    = net.sf.freecol.common.diplomacy.Loan.parse(l.serialize());
                if (c != null) {
                    ret.add(new Object[] { c, (l.collectible(war)) ? l.due() : 0 });
                }
            }
        } catch (RuntimeException e) { // The server is busy with its turn
            logger.log(Level.FINE, "Loans", e);
        }
        return ret;
    }

    /**
     * A nation's public credit standing (mod), lower case, or null if
     * not known (not a single player game).
     *
     * @param fcc The {@code FreeColClient}.
     * @param nationId The nation.
     * @return "good", "new", "wary", "bad", or null.
     */
    static String standing(FreeColClient fcc, String nationId) {
        if (fcc == null || !fcc.getSinglePlayer()) return null;
        final FreeColServer server = fcc.getFreeColServer();
        if (server == null || server.getGame() == null) return null;
        final Player p = server.getGame().getPlayerByNationId(nationId);
        if (p == null || !p.isEuropean() || p.isREF() || !p.isAI()) return null;
        try {
            return net.sf.freecol.server.model.CreditRecords.standing(p)
                .name().toLowerCase();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * A nation's credit record in words, for a hover (mod), or null.
     *
     * @param fcc The {@code FreeColClient}.
     * @param nationId The nation.
     * @return E.g. "2 repaid (1 early), 1 default 14 turns ago
     *     (forgiven), 1 seizure", or "no loans yet".
     */
    static String recordText(FreeColClient fcc, String nationId) {
        if (standing(fcc, nationId) == null) return null;
        final Game sg = fcc.getFreeColServer().getGame();
        final Player p = sg.getPlayerByNationId(nationId);
        try {
            final net.sf.freecol.common.diplomacy.CreditRecord r
                = net.sf.freecol.server.model.CreditRecords.of(p);
            final int now = sg.getTurn().getNumber();
            final List<String> parts = new ArrayList<>();
            if (r.repaid + r.early > 0) {
                parts.add((r.repaid + r.early) + " repaid"
                    + ((r.early > 0) ? " (" + r.early + " early)" : ""));
            }
            for (net.sf.freecol.common.diplomacy.CreditRecord.Default d
                     : r.recentDefaults(now)) {
                parts.add("a default " + (now - d.turn) + " turns ago"
                    + ((d.mark == net.sf.freecol.common.diplomacy.CreditRecord
                        .Mark.PLAIN) ? "" : " (" + d.mark.name().toLowerCase() + ")"));
            }
            if (r.seized > 0) {
                parts.add(r.seized + ((r.seized == 1) ? " seizure" : " seizures"));
            }
            if (r.inDefault) parts.add("in default now");
            return (parts.isEmpty()) ? "no loans yet" : String.join(", ", parts);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * What the player has out on loans (mod): owed now, loans open, gold
     * lent and got back (loans of the last turns).
     *
     * @param fcc The {@code FreeColClient}.
     * @param me The client {@code Player}.
     * @return { owed, open loans, lent, received }, or null if none.
     */
    static int[] lentOut(FreeColClient fcc, Player me) {
        final List<Object[]> loans = loans(fcc, me);
        if (loans.isEmpty()) return null;
        final int[] ret = new int[4];
        for (Object[] x : loans) {
            final net.sf.freecol.common.diplomacy.Loan l
                = (net.sf.freecol.common.diplomacy.Loan)x[0];
            if (!l.over()) {
                ret[0] += l.claim();
                ret[1]++;
            }
            ret[2] += l.principal;
            ret[3] += l.received;
        }
        return ret;
    }

    /**
     * Alerts for the player's loans (mod): a decision waiting in the
     * game, payments missed, a ship to be seized.
     *
     * @param fcc The {@code FreeColClient}.
     * @param me The client {@code Player}.
     * @param out The alerts: { level, text }.
     */
    static void alerts(FreeColClient fcc, Player me, List<String[]> out) {
        final Game game = (fcc == null) ? null : fcc.getGame();
        if (game == null) return;
        final int now = game.getTurn().getNumber();
        for (Offer o : known(fcc, me)) {
            if (o.state != Offer.State.OPEN || (o.kind != Offer.Kind.LOAN
                && o.kind != Offer.Kind.LOAN_DEFAULT)) continue;
            final Player from = game.getPlayerByNationId(o.from);
            if (from == null) continue;
            out.add(new String[] {
                (o.kind == Offer.Kind.LOAN_DEFAULT) ? "alarm" : "info",
                Messages.message(net.sf.freecol.common.model.StringTemplate
                    .template((o.kind == Offer.Kind.LOAN_DEFAULT)
                        ? "companion.alert.loanDefault"
                        : "companion.alert.loanRequest")
                    .addStringTemplate("%nation%", from.getNationLabel())
                    .addAmount("%gold%", o.amount)) });
        }
        // A deal we took to fetch goods, but we lack the gold (mod).
        for (Offer o : known(fcc, me)) {
            if (o.kind != Offer.Kind.SELL || o.state != Offer.State.TAKEN
                || !me.getNationId().equals(o.takenBy)) continue;
            final int price = o.priceAt(now);
            if (price < 0 || me.getGold() >= price) continue;
            final Player from = game.getPlayerByNationId(o.from);
            if (from == null) continue;
            out.add(new String[] { "warn", Messages.message(
                net.sf.freecol.common.model.StringTemplate
                    .template("companion.alert.fetchNoGold")
                    .addStringTemplate("%nation%", from.getNationLabel())
                    .addName("%colony%", o.placeName)
                    .addAmount("%gold%", price)
                    .addAmount("%have%", me.getGold())
                    .addAmount("%turns%", Math.max(0, o.turnsLeft(now)))) });
        }
        for (Object[] x : loans(fcc, me)) {
            final net.sf.freecol.common.diplomacy.Loan l
                = (net.sf.freecol.common.diplomacy.Loan)x[0];
            final Player b = game.getPlayerByNationId(l.borrower);
            if (b == null || l.over()) continue;
            String key = null, level = "warn";
            if (l.state == net.sf.freecol.common.diplomacy.Loan.State.SEIZURE_PENDING) {
                key = "companion.alert.loanSeize";
                level = "info";
            } else if (l.missed > 0 && l.state.running()) {
                key = "companion.alert.loanLate";
            }
            if (key == null) continue;
            out.add(new String[] { level, Messages.message(
                net.sf.freecol.common.model.StringTemplate.template(key)
                    .addStringTemplate("%nation%", b.getNationLabel())
                    .addAmount("%amount%", l.missed)
                    .addAmount("%gold%", l.claim())) });
        }
    }

    /**
     * The offers and the player's loans as JSON.
     *
     * @param fcc The {@code FreeColClient}.
     * @param offers The offers the player knows of.
     * @param loans The player's loans ({@link #loans}).
     * @param game The client {@code Game}.
     * @param me The client {@code Player}.
     * @return The JSON.
     */
    static String build(FreeColClient fcc, List<Offer> offers,
                        List<Object[]> loans, Game game, Player me) {
        final String o = build(offers, game, me);
        final Json j = new Json().arr();
        loans.sort((a, b) -> Boolean.compare(
            ((net.sf.freecol.common.diplomacy.Loan)a[0]).over(),
            ((net.sf.freecol.common.diplomacy.Loan)b[0]).over()));
        for (Object[] x : loans) {
            final net.sf.freecol.common.diplomacy.Loan l
                = (net.sf.freecol.common.diplomacy.Loan)x[0];
            final Player b = game.getPlayerByNationId(l.borrower);
            final String st = standing(fcc, l.borrower);
            final String rec = recordText(fcc, l.borrower);
            j.obj().put("id", l.id)
                .put("from", (b == null) ? l.borrower
                    : Messages.message(b.getNationLabel()))
                .put("color", (b == null) ? java.awt.Color.GRAY
                    : b.getNationColor())
                .put("state", l.state.name().toLowerCase())
                .put("war", l.warLoan())
                .put("principal", l.principal)
                .put("balance", l.claim())
                .put("rent", l.rent)
                .put("term", l.term)
                .put("paidTurns", l.paidTurns)
                .put("missed", l.missed)
                .put("next", (Integer)x[1])
                .put("over", l.over())
                .put("credit", (st == null) ? "" : st)
                .put("creditText", (rec == null) ? "" : rec)
                .put("received", l.received)
                .put("signed", Messages.message(
                    new net.sf.freecol.common.model.Turn(l.signed).getLabel()))
                .end();
        }
        j.endArr();
        // {"turn":..,"offers":[..]} + "loans":[..]
        return o.substring(0, o.length() - 1) + ",\"loans\":" + j + "}";
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
                .put("ship", (o.kind == Offer.Kind.LOAN
                              || o.kind == Offer.Kind.LOAN_DEFAULT) && o.goods != null)
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
