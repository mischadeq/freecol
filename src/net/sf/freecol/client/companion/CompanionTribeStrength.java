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
import java.util.function.ToIntFunction;

import net.sf.freecol.common.diplomacy.Estimates;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.CombatModel;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.NationType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.SettlementType;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The tribes' strength for the Nations and Military tabs (mod), next
 * to the European nations.  Honest: the number of settlements is what
 * the game's Native Affairs report shows; braves per settlement come
 * from the rules (the middle of the sizes a settlement may have); the
 * braves in sight count as they are (armed, mounted), the others as
 * plain braves.  Only tribes the player has met.
 */
final class CompanionTribeStrength {

    private CompanionTribeStrength() {}


    /** One tribe's figures. */
    static final class Figures {
        int settlements, known, seen;
        double braves, strength, soldiers;
        final Map<String, Integer> kinds = new TreeMap<>();
    }

    /**
     * What kind of native unit, for the counts on the page.
     *
     * @param u The {@code Unit}.
     * @return "dragoon", "armed", "mounted", "brave" or null (not a
     *     fighter).
     */
    static String kind(Unit u) {
        final String role = u.getRole().getId();
        if (role.endsWith("nativeDragoon")) return "dragoon";
        if (role.endsWith("armedBrave")) return "armed";
        if (role.endsWith("mountedBrave")) return "mounted";
        return (u.getType().getOffence() > 0) ? "brave" : null;
    }

    /** The middle size of a settlement type. */
    private static double mid(SettlementType st, double dflt) {
        return (st == null) ? dflt
            : (st.getMinimumSize() + st.getMaximumSize()) / 2.0;
    }

    /**
     * Work out one tribe's figures.
     *
     * @param game The {@code Game}.
     * @param me The player looking.
     * @param p The tribe.
     * @param settlements Its settlements (Native Affairs report), or -1
     *     if unknown (then the ones seen).
     * @return The {@code Figures}.
     */
    static Figures figures(Game game, Player me, Player p, int settlements) {
        final Figures f = new Figures();
        for (Tile t : game.getMap().getTileSet(Tile::hasSettlement)) {
            if (t.getSettlement() instanceof IndianSettlement
                && t.getSettlement().getOwner() == p) f.known++;
        }
        f.settlements = (settlements >= 0) ? settlements : f.known;
        final CombatModel cm = game.getCombatModel();
        double seenPower = 0;
        for (Tile t : game.getMap().getTileList(me::canSee)) {
            if (t.hasSettlement()) continue; // their braves at home are hidden
            for (Unit u : t.getUnitList()) {
                if (u.getOwner() != p || u.isDisposed() || u.isNaval()) continue;
                final String k = kind(u);
                if (k == null) continue;
                f.kinds.merge(k, 1, Integer::sum);
                f.seen++;
                seenPower += cm.getOffencePower(u, null);
            }
        }
        final NationType nt = p.getNationType();
        final double size = mid((nt == null) ? null
                                : nt.getSettlementType(false), 5);
        final double capital = mid((nt == null) ? null
                                   : nt.getSettlementType(true), size);
        f.braves = Estimates.tribeBraves(f.settlements, size, capital, f.seen);
        f.strength = Estimates.tribeStrength(f.braves, f.seen, seenPower,
            Estimates.bravePower(game.getSpecification()
                .getDefaultUnitType(p)));
        f.soldiers = Estimates.equivalent((int)Math.round(f.strength),
            Estimates.soldierPower(game.getSpecification()));
        return f;
    }

    /**
     * Write the tribes the player has met, as a "tribes" array.
     *
     * @param j The {@code Json} to write to.
     * @param game The {@code Game}.
     * @param me The player looking.
     * @param myMilitary The player's own military strength.
     * @param settlements A tribe's settlement count from the Native
     *     Affairs report (-1 if unknown).
     */
    static void put(Json j, Game game, Player me, int myMilitary,
                    ToIntFunction<Player> settlements) {
        j.key("tribes").arr();
        for (Player p : game.getLiveNativePlayerList()) {
            if (!me.hasContacted(p)) continue;
            final Figures f = figures(game, me, p, settlements.applyAsInt(p));
            j.obj().put("id", p.getNationId())
                .put("name", Messages.message(p.getNationLabel()))
                .put("color", p.getNationColor())
                .put("tribe", true)
                .put("settlements", f.settlements)
                .put("known", f.known)
                .put("braves", Math.round(f.braves))
                .put("seen", f.seen)
                .put("soldiers", Math.round(10 * f.soldiers));
            final net.sf.freecol.common.model.Stance st = me.getStance(p);
            if (st != null) j.put("stance", st.getKey().replace("stance.", ""));
            final String w = Estimates.verdict(f.strength, myMilitary);
            if (w != null) j.put("verdict", w);
            j.key("units").obj();
            for (Map.Entry<String, Integer> e : f.kinds.entrySet()) {
                j.put(e.getKey(), e.getValue());
            }
            j.end();
            final NationType nt = p.getNationType();
            final SettlementType reg = (nt == null) ? null
                : nt.getSettlementType(false);
            final SettlementType cap = (nt == null) ? null
                : nt.getSettlementType(true);
            if (reg != null) {
                j.key("sizes").arr().val(reg.getMinimumSize())
                    .val(reg.getMaximumSize());
                if (cap != null) {
                    j.val(cap.getMinimumSize()).val(cap.getMaximumSize());
                }
                j.endArr();
            }
            j.end();
        }
        j.endArr();
    }
}
