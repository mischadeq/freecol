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


package net.sf.freecol.server.ai.military;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.Action;
import net.sf.freecol.common.diplomacy.Diplomat;
import net.sf.freecol.common.diplomacy.Effects;
import net.sf.freecol.common.diplomacy.ScoredAction;
import net.sf.freecol.common.diplomacy.Situation;
import net.sf.freecol.common.diplomacy.WorldView;
import net.sf.freecol.common.diplomacy.WorldViewBuilder;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.AIUnit;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.ai.mission.DefendSettlementMission;


/**
 * The computer player acts on its diplomat's small war moves (mod,
 * diplomacy plan): arming colonists with the muskets in its colonies
 * (ARM), and buying a ship in Europe when an enemy colony across the sea
 * is worth taking but there is no room to carry the army (BUY_SHIP).
 *
 * Colonists armed this way are kept armed while the nation is in
 * danger (and for {@link EuropeanAIPlayer#KEEP_ARMED} turns after it
 * last was): otherwise the colony planner takes their muskets back to
 * put them to work, which is why computer nations used to have fewer
 * soldiers than colonies.
 */
public final class WarPreparations {

    private static final Logger logger = Logger.getLogger(WarPreparations.class.getName());

    /** Most colonists armed in one turn. */
    public static final int MAX_PER_TURN = 4;
    /** A colony keeps at least this many workers. */
    public static final int KEEP_WORKERS = 2;

    private final EuropeanAIPlayer aiPlayer;


    public WarPreparations(EuropeanAIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
    }


    /**
     * Do what the diplomat says is worth it.
     *
     * @param lb A {@code LogBuilder} to log to.
     * @return Units armed plus ships bought.
     */
    public int plan(LogBuilder lb) {
        final Player player = aiPlayer.getPlayer();
        final Game game = player.getGame();
        if (!player.isEuropean() || player.isREF()) return 0;
        final WorldView w = WorldViewBuilder.build(game, player, true, null,
                                                   aiPlayer.getBearings());
        final Situation s = new Situation(w);
        final int turn = game.getTurn().getNumber();
        int done = 0;
        boolean armWanted = false, shipBought = false;
        for (ScoredAction sa : Diplomat.rank(s)) {
            if (sa.total < Diplomat.threshold(sa.action.kind)) continue;
            if (sa.action.kind == Action.Kind.ARM && !armWanted) {
                armWanted = true;
                done += arm(Math.min(MAX_PER_TURN, sa.action.amount), turn, lb);
            } else if (sa.action.kind == Action.Kind.BUY_SHIP && !shipBought) {
                shipBought = true;
                if (buyShip(sa.action, lb)) done++;
            }
        }
        if (armWanted || Effects.inDanger(w)) aiPlayer.keepArmed(turn);
        return done;
    }

    /**
     * Arm colonists with the muskets (and horses) in their colonies.
     *
     * @param n How many.
     * @param turn The turn.
     * @param lb A {@code LogBuilder} to log to.
     * @return How many were armed.
     */
    int arm(int n, int turn, LogBuilder lb) {
        final Player player = aiPlayer.getPlayer();
        final Specification spec = player.getSpecification();
        final GoodsType muskets = spec.getGoodsType("model.goods.muskets");
        final GoodsType horses = spec.getGoodsType("model.goods.horses");
        final Role soldier = spec.getRole("model.role.soldier");
        final Role dragoon = spec.getRole("model.role.dragoon");
        if (muskets == null || soldier == null) return 0;
        List<Colony> colonies = new ArrayList<>(player.getColonyList());
        colonies.sort(Comparator.comparingInt((Colony c) -> -c.getGoodsCount(muskets)));
        int armed = 0;
        for (Colony c : colonies) {
            while (armed < n && c.getUnitCount() > KEEP_WORKERS
                   && c.getGoodsCount(muskets) >= Effects.MUSKETS_PER_SOLDIER) {
                final Unit u = leastNeeded(c);
                if (u == null) break;
                final AIUnit aiu = aiPlayer.getAIMain().getAIUnit(u);
                if (aiu == null) break;
                final Role role = (dragoon != null && horses != null
                    && c.getGoodsCount(horses) >= 50
                    && u.roleIsAvailable(dragoon)) ? dragoon : soldier;
                if (!aiu.equipForRole(role) && (role == soldier
                        || !aiu.equipForRole(soldier))) break;
                aiu.changeMission(new DefendSettlementMission(
                    aiu.getAIMain(), aiu, c));
                aiPlayer.keepArmed(u, turn);
                armed++;
                lb.add("\n  Armed ", u, " at ", c.getName());
            }
            if (armed >= n) break;
        }
        if (armed > 0) {
            logger.fine(player.getName() + " armed " + armed + " colonists");
        }
        return armed;
    }

    /**
     * The colonist a colony misses least: no special skill, lowest
     * skill first.
     *
     * @param c The {@code Colony}.
     * @return The {@code Unit}, or null.
     */
    static Unit leastNeeded(Colony c) {
        Unit best = null;
        for (Unit u : c.getUnitList()) {
            if (!u.isPerson() || u.hasAbility(Ability.REF_UNIT)) continue;
            if (best == null
                || u.getType().getSkill() < best.getType().getSkill()) best = u;
        }
        return best;
    }

    /**
     * Buy the ship the diplomat had in mind.
     *
     * @param a The BUY_SHIP {@code Action}.
     * @param lb A {@code LogBuilder} to log to.
     * @return True if a ship was bought.
     */
    boolean buyShip(Action a, LogBuilder lb) {
        final Player player = aiPlayer.getPlayer();
        if (player.getEurope() == null || player.getGold() < a.price) return false;
        for (UnitType ut : player.getSpecification().getUnitTypeList()) {
            if (!ut.isNaval() || ut.getSpace() != a.amount || !ut.hasPrice()
                || !ut.isAvailableTo(player)) continue;
            if (player.getEuropeanPurchasePrice(new AbstractUnit(ut,
                    Specification.DEFAULT_ROLE_ID, 1)) != a.price) continue;
            final AIUnit ship = aiPlayer.trainAIUnitInEurope(ut);
            if (ship == null) return false;
            lb.add("\n  Bought ", ship.getUnit(), " to reach ", a.target);
            logger.fine(player.getName() + " bought " + ut.getSuffix()
                        + " to reach " + a.target);
            return true;
        }
        return false;
    }
}
