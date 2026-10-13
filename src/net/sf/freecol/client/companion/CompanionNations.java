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

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;


/**
 * The nations ranking of the companion page: the European nations side
 * by side, with the same figures the game's Foreign Affairs report
 * shows (more of them once Jan de Witt is in Congress), plus the sizes
 * of their colonies as seen on the map.  The figures are recorded per
 * turn to {@code companion/nations-<game>.csv} for the trend chart.
 *
 * Must be called on the Swing event thread.
 */
final class CompanionNations {

    private static final Logger logger = Logger.getLogger(CompanionNations.class.getName());

    /** The measures, in the order they are recorded and shown. */
    static final String[] MEASURES = {
        "colonies", "colonists", "avgSize", "units", "military", "naval",
        "gold", "sol", "fathers", "tax"
    };

    /** History by turn, then nation id: values in MEASURES order. */
    private static final TreeMap<Integer, Map<String, int[]>> history
        = new TreeMap<>();

    private static String gameKey = null;

    /** The turn the nation summaries were last refreshed. */
    private static int refreshedTurn = -1;


    private CompanionNations() {}


    private static File file(Game game, Player me) {
        final File base = FreeColDirectories.getUserDataDirectory();
        if (base == null) return null;
        final File dir = new File(base, "companion");
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        return new File(dir, "nations-" + game.getUUID() + "-"
            + me.getId().replace(':', '_') + ".csv");
    }

    /**
     * Gather the figures for every European nation.
     *
     * @param fcc The {@code FreeColClient}.
     * @param game The {@code Game}.
     * @param me The client {@code Player}.
     * @return Rows of {nation, values, met, known colonies, colony sizes}.
     */
    private static List<Object[]> gather(FreeColClient fcc, Game game,
                                         Player me) {
        final int turn = game.getTurn().getNumber();
        final boolean refresh = turn != refreshedTurn;
        refreshedTurn = turn;
        List<Object[]> rows = new ArrayList<>();
        for (Player p : game.getLiveEuropeanPlayerList()) {
            if (p.isREF()) continue;
            final boolean met = p == me || me.hasContacted(p);

            // Colony sizes as seen on the map (all of ours).
            int known = 0, colonists = 0;
            List<Integer> sizes = new ArrayList<>();
            for (Tile t : game.getMap().getTileSet(Tile::hasSettlement)) {
                Settlement s = t.getSettlement();
                if (s.getOwner() != p) continue;
                known++;
                int n = (s instanceof Colony)
                    ? ((Colony)s).getApparentUnitCount() : 0;
                colonists += n;
                sizes.add(n);
            }
            sizes.sort(java.util.Comparator.reverseOrder());

            NationSummary ns = null;
            if (p == me) {
                ns = new NationSummary(me, me);
            } else {
                // Like the Foreign Affairs report, this works for nations
                // we have not met.  The game caches summaries until
                // relations change; ask afresh once per turn.
                if (refresh) me.clearNationSummary(p);
                try {
                    ns = fcc.getInGameController().nationSummary(p);
                } catch (RuntimeException re) {
                    ns = null;
                }
            }
            int[] v = new int[MEASURES.length];
            java.util.Arrays.fill(v, -1);
            v[0] = (ns != null) ? ns.getNumberOfSettlements() : known;
            v[1] = colonists;
            v[2] = (known > 0) ? Math.round(10f * colonists / known) : -1;
            if (ns != null) {
                v[3] = ns.getNumberOfUnits();
                v[4] = ns.getMilitaryStrength();
                v[5] = ns.getNavalStrength();
                v[6] = ns.getGold();
                v[7] = ns.getSoL();
                v[8] = ns.getFoundingFathers();
                v[9] = ns.getTax();
            }
            rows.add(new Object[] { p, v, met, known, sizes });
        }
        return rows;
    }

    /**
     * Record this turn's figures.
     *
     * @param fcc The {@code FreeColClient}.
     */
    static synchronized void record(FreeColClient fcc) {
        final Game game = fcc.getGame();
        final Player me = fcc.getMyPlayer();
        if (game == null || me == null || game.getMap() == null) return;
        build(fcc, game, me, false);
    }

    private static void load(File f) {
        if (f == null || !f.isFile()) return;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split(",");
                if (p.length != 2 + MEASURES.length) continue;
                try {
                    int[] v = new int[MEASURES.length];
                    for (int i = 0; i < v.length; i++) v[i] = Integer.parseInt(p[2 + i]);
                    history.computeIfAbsent(Integer.parseInt(p[0]),
                                            t -> new TreeMap<>()).put(p[1], v);
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
            for (Map.Entry<String, int[]> n : e.getValue().entrySet()) {
                sb.append(e.getKey()).append(',').append(n.getKey());
                for (int v : n.getValue()) sb.append(',').append(v);
                sb.append('\n');
            }
        }
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Could not write " + f, ioe);
        }
    }

    /** Index of a measure. */
    private static int mi(String m) {
        for (int i = 0; i < MEASURES.length; i++) if (MEASURES[i].equals(m)) return i;
        return -1;
    }

    /** A measure of one nation, turn by turn, from the history. */
    private static java.util.SortedMap<Integer, Integer> series(String nationId,
        java.util.function.ToIntFunction<int[]> f) {
        java.util.SortedMap<Integer, Integer> ret = new TreeMap<>();
        for (Map.Entry<Integer, Map<String, int[]>> e : history.entrySet()) {
            int[] v = e.getValue().get(nationId);
            if (v != null) ret.put(e.getKey(), f.applyAsInt(v));
        }
        return ret;
    }

    /** What kind of unit, for the counts on the page. */
    static String unitKind(Unit u) {
        if (u.isNaval()) {
            return (u.isOffensiveUnit()) ? "warship" : "merchant";
        }
        if (u.hasAbility(net.sf.freecol.common.model.Ability.BOMBARD)
            || "model.unit.artillery".equals(u.getType().getId())
            || "model.unit.damagedArtillery".equals(u.getType().getId())) {
            return "artillery";
        }
        final String role = u.getRole().getId();
        if (role.endsWith("dragoon")) return "dragoon";
        if (role.endsWith("soldier")) return "soldier";
        if (u.isCarrier()) return "wagon";
        return (u.isPerson()) ? "colonist" : "other";
    }

    /**
     * Add the plain-language figures of one nation: strength in
     * soldiers and frigates, units (ours: all; theirs: seen now), sea
     * fights, treasury and bells per turn, and output.  Ours are exact;
     * theirs are honest estimates (see {@link NationEstimate}).
     */
    private static void estimates(Json j, Game game, Player me, Player p,
                                  int[] v) {
        final net.sf.freecol.common.model.Specification spec
            = game.getSpecification();
        final boolean mine = p == me;
        final int turn = game.getTurn().getNumber();
        j.put("soldiers", Math.round(10 * NationEstimate.equivalent(v[mi("military")],
                    NationEstimate.soldierPower(spec))))
            .put("frigates", Math.round(10 * NationEstimate.equivalent(v[mi("naval")],
                    NationEstimate.frigatePower(spec))));

        // Units: all of ours; theirs as seen on the map now.
        Map<String, Integer> kinds = new TreeMap<>();
        for (Unit u : p.getUnitSet()) {
            if (u.isDisposed()) continue;
            if (!mine && u.getTile() == null) continue;
            if (u.isInColony()) continue;
            kinds.merge(unitKind(u), 1, Integer::sum);
        }
        j.key("units").obj();
        for (Map.Entry<String, Integer> e : kinds.entrySet()) j.put(e.getKey(), e.getValue());
        j.end();
        if (mine) {
            int muskets = 0, horses = 0;
            final net.sf.freecol.common.model.GoodsType mt
                = spec.getGoodsType("model.goods.muskets");
            final net.sf.freecol.common.model.GoodsType ht
                = spec.getGoodsType("model.goods.horses");
            for (Colony c : me.getColonyList()) {
                if (mt != null) muskets += c.getGoodsCount(mt);
                if (ht != null) horses += c.getGoodsCount(ht);
            }
            j.put("muskets", muskets).put("horses", horses);
        }

        // Sea fights over the last ten turns.
        if (!mine) {
            int[] r = CompanionRaids.totals(turn, 10).get(p.getNationId());
            if (r != null) {
                j.key("raids").arr();
                for (int x : r) j.val(x);
                j.endArr();
            }
        }

        // Treasury change and bells per turn.
        Double gold = NationEstimate.perTurn(series(p.getNationId(),
                x -> x[mi("gold")]), NationEstimate.WINDOW);
        if (gold != null) j.put("goldPerTurn", Math.round(gold));
        if (mine) {
            int bells = 0;
            for (Colony c : me.getColonyList()) {
                for (net.sf.freecol.common.model.GoodsType gt
                         : spec.getLibertyGoodsTypeList()) {
                    bells += c.getNetProductionOf(gt);
                }
            }
            j.put("bellsPerTurn", bells).put("bellsExact", true);
        } else {
            Double bells = NationEstimate.bellsPerTurn(series(p.getNationId(),
                    x -> (x[mi("sol")] < 0 || x[mi("colonists")] < 0) ? -1
                        : (int)Math.round(10 * NationEstimate.rebels(
                            x[mi("sol")], x[mi("colonists")]))),
                NationEstimate.WINDOW);
            if (bells != null) j.put("bellsPerTurn", Math.round(bells));
        }

        // Output in gold per turn, per colony: ours exact, theirs as
        // many colonists times what one of ours makes.
        final double per = NationEstimate.valuePerColonist(me);
        int total = 0;
        boolean known = mine || per > 0;
        j.key("colonyList").arr();
        for (Tile t : game.getMap().getTileSet(Tile::hasSettlement)) {
            if (!(t.getSettlement() instanceof Colony)) continue;
            final Colony c = (Colony)t.getSettlement();
            if (c.getOwner() != p) continue;
            final int size = (mine) ? c.getUnitCount() : c.getApparentUnitCount();
            final int value = (mine)
                ? (int)Math.round(NationEstimate.colonyValue(c, me.getMarket()))
                : NationEstimate.output(size, per);
            if (value > 0) total += value;
            j.obj().put("name", c.getName()).put("size", size)
                .put("value", value).put("x", t.getX()).put("y", t.getY()).end();
        }
        j.endArr();
        if (known) j.put("output", total);
        j.put("exact", mine);
    }

    /**
     * Build the ranking, recording the figures on the way.
     *
     * @param fcc The {@code FreeColClient}.
     * @param game The {@code Game}.
     * @param me The client {@code Player}.
     * @param json If false, only record.
     * @return The ranking as JSON, or null if json is false.
     */
    static synchronized String build(FreeColClient fcc, Game game, Player me,
                                     boolean json) {
        final String gk = game.getUUID() + "/" + me.getId();
        if (!gk.equals(gameKey)) {
            gameKey = gk;
            history.clear();
            refreshedTurn = -1;
            load(file(game, me));
        }
        final int turn = game.getTurn().getNumber();
        final List<Object[]> rows = gather(fcc, game, me);
        Map<String, int[]> now = history.computeIfAbsent(turn, t -> new TreeMap<>());
        boolean changed = false;
        for (Object[] r : rows) {
            final Player p = (Player)r[0];
            final int[] v = (int[])r[1];
            if (!java.util.Arrays.equals(v, now.get(p.getNationId()))) {
                now.put(p.getNationId(), v);
                changed = true;
            }
        }
        if (changed) save(file(game, me));
        if (!json) return null;

        Json j = new Json().obj().put("turn", turn)
            .put("deWitt", me.hasAbility(
                net.sf.freecol.common.model.Ability.BETTER_FOREIGN_AFFAIRS_REPORT));
        j.key("measures").arr();
        for (String m : MEASURES) j.val(m);
        j.endArr();
        j.key("nations").arr();
        for (Object[] r : rows) {
            final Player p = (Player)r[0];
            final int[] v = (int[])r[1];
            j.obj().put("id", p.getNationId())
                .put("name", Messages.message(p.getNationLabel()))
                .put("color", p.getNationColor())
                .put("me", p == me)
                .put("met", (Boolean)r[2])
                .put("knownColonies", (Integer)r[3]);
            if (p == me) j.put("score", me.getScore());
            j.key("sizes").arr();
            for (Object x : (List<?>)r[4]) j.val((Integer)x);
            j.endArr();
            j.key("v").arr();
            for (int x : v) j.val(x);
            j.endArr();
            estimates(j, game, me, p, v);
            j.end();
        }
        j.endArr();
        j.put("soldierPower", Math.round(NationEstimate.soldierPower(game.getSpecification())))
            .put("frigatePower", Math.round(NationEstimate.frigatePower(game.getSpecification())))
            .put("perColonist", Math.round(NationEstimate.valuePerColonist(me)));
        j.key("turns").obj();
        for (Integer t : history.keySet()) {
            j.put(String.valueOf(t), Messages.message(new Turn(t).getLabel()));
        }
        j.end();
        j.key("history").obj();
        for (Map.Entry<Integer, Map<String, int[]>> e : history.entrySet()) {
            j.key(String.valueOf(e.getKey())).obj();
            for (Map.Entry<String, int[]> n : e.getValue().entrySet()) {
                j.key(n.getKey()).arr();
                for (int x : n.getValue()) j.val(x);
                j.endArr();
            }
            j.end();
        }
        j.end();
        return j.end().toString();
    }
}
