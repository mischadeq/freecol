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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Turn;


/**
 * The prices of the market in Europe, turn by turn, for the market
 * section of the companion page.
 *
 * The game does not keep old prices, so they are recorded here: the
 * latest price of each goods type in each turn, written to a small
 * file per game in the user data folder
 * ({@code companion/prices-<game>.csv}), so the history survives
 * restarting FreeCol and grows as the game is played.
 *
 * Must be called on the Swing event thread.
 */
public final class CompanionMarket {

    private static final Logger logger = Logger.getLogger(CompanionMarket.class.getName());

    private static final String DIRECTORY = "companion";

    /** Prices by turn, then goods type id: {buy, sell}. */
    private static final TreeMap<Integer, Map<String, int[]>> history
        = new TreeMap<>();

    /** The game the history belongs to. */
    private static String gameKey = null;

    /** Changes with every recorded change, for the page. */
    private static long rev = 0;


    private CompanionMarket() {}


    private static File file(Game game, Player player) {
        final File base = FreeColDirectories.getUserDataDirectory();
        if (base == null) return null;
        final File dir = new File(base, DIRECTORY);
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        return new File(dir, "prices-" + game.getUUID() + "-"
            + player.getId().replace(':', '_') + ".csv");
    }

    /**
     * Note the current prices.  Cheap when nothing changed.  Called
     * with every snapshot and at the start of every turn.
     *
     * @param game The {@code Game}.
     * @param player The client {@code Player}.
     */
    public static synchronized void record(Game game, Player player) {
        if (game == null || player == null || player.getMarket() == null) {
            return;
        }
        final String gk = game.getUUID() + "/" + player.getId();
        if (!gk.equals(gameKey)) {
            gameKey = gk;
            history.clear();
            load(file(game, player));
            rev++;
        }
        final Market market = player.getMarket();
        final int turn = game.getTurn().getNumber();
        final Map<String, int[]> now
            = history.computeIfAbsent(turn, t -> new TreeMap<>());
        boolean changed = false;
        for (GoodsType gt : game.getSpecification().getStorableGoodsTypeList()) {
            final int buy = market.getCostToBuy(gt);
            final int sell = market.getPaidForSale(gt);
            if (buy <= 0 && sell <= 0) continue;
            final int[] old = now.get(gt.getId());
            if (old == null || old[0] != buy || old[1] != sell) {
                now.put(gt.getId(), new int[] { buy, sell });
                changed = true;
            }
        }
        if (changed) {
            rev++;
            save(file(game, player));
        }
    }

    static synchronized long getRev() {
        return rev;
    }

    private static void load(File f) {
        if (f == null || !f.isFile()) return;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split(",");
                if (p.length != 4) continue;
                try {
                    history.computeIfAbsent(Integer.parseInt(p[0]),
                                            t -> new TreeMap<>())
                        .put(p[1], new int[] { Integer.parseInt(p[2]),
                                               Integer.parseInt(p[3]) });
                } catch (NumberFormatException nfe) {
                    // Skip a bad line.
                }
            }
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Could not read " + f, ioe);
        }
    }

    private static void save(File f) {
        if (f == null) return;
        StringBuilder sb = new StringBuilder(4096);
        for (Map.Entry<Integer, Map<String, int[]>> e : history.entrySet()) {
            for (Map.Entry<String, int[]> g : e.getValue().entrySet()) {
                sb.append(e.getKey()).append(',').append(g.getKey())
                    .append(',').append(g.getValue()[0])
                    .append(',').append(g.getValue()[1]).append('\n');
            }
        }
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Could not write " + f, ioe);
        }
    }

    /**
     * Build the market section.
     *
     * @param game The {@code Game}.
     * @param player The client {@code Player}.
     * @return The market as JSON.
     */
    static synchronized String build(Game game, Player player) {
        record(game, player);
        final Market market = player.getMarket();
        Json j = new Json().obj().put("rev", rev).put("tax", player.getTax())
            .put("turn", game.getTurn().getNumber());
        int taxes = 0, balance = 0;
        final List<GoodsType> goods = game.getSpecification()
            .getStorableGoodsTypeList();
        for (GoodsType gt : goods) {
            taxes += market.getIncomeBeforeTaxes(gt)
                - market.getIncomeAfterTaxes(gt);
            balance += market.getIncomeAfterTaxes(gt);
        }
        j.put("taxesPaid", taxes).put("balance", balance);

        // Year labels for the turns in the history.
        j.key("turns").obj();
        for (Integer t : history.keySet()) {
            j.put(String.valueOf(t), Messages.message(new Turn(t).getLabel()));
        }
        j.end();

        j.key("goods").arr();
        for (GoodsType gt : goods) {
            List<int[]> hist = new ArrayList<>();
            for (Map.Entry<Integer, Map<String, int[]>> e : history.entrySet()) {
                int[] p = e.getValue().get(gt.getId());
                if (p != null) hist.add(new int[] { e.getKey(), p[0], p[1] });
            }
            if (hist.isEmpty()) continue;
            j.obj().put("g", gt.getId()).put("name", Messages.getName(gt))
                .put("buy", market.getCostToBuy(gt))
                .put("sell", market.getPaidForSale(gt))
                .put("sales", market.getSales(gt))
                .put("income", market.getIncomeAfterTaxes(gt))
                .put("taxes", market.getIncomeBeforeTaxes(gt)
                    - market.getIncomeAfterTaxes(gt))
                .put("boycott", player.getArrears(gt) > 0);
            j.key("hist").arr();
            for (int[] h : hist) j.arr().val(h[0]).val(h[1]).val(h[2]).endArr();
            j.endArr().end();
        }
        j.endArr();
        return j.end().toString();
    }
}
