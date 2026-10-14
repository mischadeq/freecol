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

import java.util.List;

import net.sf.freecol.common.diplomacy.CombatMath;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.CombatModel;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Monarch;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.option.GameOptions;


/**
 * The war of independence as the companion page shows it: the player's
 * forces against the King's expeditionary force (REF), how well each
 * colony would hold, who would join the Continental Army when
 * independence is declared, and what it takes to win.
 *
 * Fights in FreeCol are decided by chance: the attacker wins with
 * probability attack / (attack + defence).  The attack of a King's unit
 * is worked out here with the same modifiers the game's combat model
 * uses for a unit attacking a colony (attack bonus, the REF's bombard
 * bonus, popular support from the colony's tories); our defenders'
 * strength comes straight from the combat model.
 *
 * Must be called on the Swing event thread.
 */
final class CompanionIndependence {

    /** The REF gives up below this many land units (ServerPlayer). */
    static final int REF_LAND_UNITS_REQUIRED = 7;
    /** ... or below this many ships. */
    static final int REF_NAVAL_UNITS_REQUIRED = 2;

    private CompanionIndependence() {}


    // Combat sums live in CombatMath (shared with the diplomat).

    static double attack(Specification spec, Turn turn, UnitType type,
                         Role role, NationType nationType, int popularSupport,
                         boolean settlement) {
        return CombatMath.attack(spec, turn, type, role, nationType,
                                 popularSupport, settlement);
    }

    static double defence(Specification spec, Turn turn, Player owner,
                          UnitType type, Role role, Colony colony,
                          boolean fortified) {
        return CombatMath.defence(spec, turn, owner, type, role, colony,
                                  fortified);
    }

    static double holdChance(double attack, double defence) {
        return CombatMath.holdChance(attack, defence);
    }

    /**
     * How many veteran soldiers a colony turns into Continental
     * regulars when independence is declared (ServerInGameController
     * declareIndependence): none at 50% rebels or less, else
     * (units + 2) × (rebels − 50) / 100.
     *
     * @param sol The colony's rebel percentage.
     * @param units All units in and around the colony.
     * @return The most that can be mustered.
     */
    static int musterLimit(int sol, int units) {
        if (sol <= 50) return 0;
        return (units + 2) * (sol - 50) / 100;
    }

    /** What a unit is, in a few words. */
    private static String label(Unit u) {
        return Messages.message(u.getLabel(Unit.UnitLabelType.NATIONAL))
            .replaceFirst("^\\S+ ", ""); // Drop the nation adjective
    }

    private static String where(Unit u, Player me) {
        if (u.getColony() != null) return u.getColony().getName();
        if (u.getLocation() instanceof Unit) {
            Unit carrier = (Unit)u.getLocation();
            return Messages.message(carrier.getLabel(Unit.UnitLabelType.PLAIN))
                + ((carrier.getTile() != null && carrier.getTile().getColony() != null)
                    ? " at " + carrier.getTile().getColony().getName() : " (aboard)");
        }
        if (u.isInEurope()) return "Europe";
        if (u.getTile() == null) return "at sea";
        return (u.getTile().getColony() != null)
            ? u.getTile().getColony().getName()
            : "in the field";
    }

    /**
     * Build the independence picture.
     *
     * @param game The {@code Game}.
     * @param me The client {@code Player}.
     * @return The JSON text.
     */
    static String build(Game game, Player me) {
        final Specification spec = game.getSpecification();
        final Turn turn = game.getTurn();
        final CombatModel cm = game.getCombatModel();
        final Monarch monarch = me.getMonarch();
        final Nation refNation = me.getNation().getREFNation();
        final NationType refType = (refNation == null) ? null : refNation.getType();
        final double soldier = NationEstimate.soldierPower(spec);
        final Json j = new Json().obj();
        j.put("turn", turn.getNumber())
            .put("playerType", me.getPlayerType().toString().toLowerCase())
            .put("sol", me.getSoL())
            .put("solNeeded", 50)
            .put("year", turn.getYear())
            .put("lastYear", spec.getInteger(GameOptions.LAST_COLONIAL_YEAR))
            .put("soldierPower", Math.round(soldier));
        boolean port = false;
        for (Colony c : me.getColonyList()) port |= c.isConnectedPort();
        j.put("coastal", port);

        // The King's force, unit kind by unit kind, with the attack of
        // one such unit on a colony where nobody supports the King.
        int refLand = 0, refNaval = 0;
        double refLandAttack = 0;
        j.key("ref").arr();
        if (monarch != null) {
            for (AbstractUnit au : monarch.getExpeditionaryForce().getUnitList()) {
                final UnitType ut = au.getType(spec);
                final Role role = au.getRole(spec);
                final boolean naval = ut.isNaval();
                final double a = attack(spec, turn, ut, role, refType, 0, !naval);
                if (naval) refNaval += au.getNumber();
                else {
                    refLand += au.getNumber();
                    refLandAttack += au.getNumber() * a;
                }
                j.obj().put("kind", kind(ut, role)).put("number", au.getNumber())
                    .put("name", Messages.message(au.getLabel()))
                    .put("attack10", Math.round(10 * a))
                    .put("naval", naval).end();
            }
        }
        j.endArr();
        j.put("refLand", refLand).put("refNaval", refNaval)
            .put("refLandAttack", Math.round(refLandAttack));
        j.put("ratioLand", Math.round(100 * me.getRebelStrengthRatio(false)))
            .put("ratioNaval", Math.round(100 * me.getRebelStrengthRatio(true)));

        // Our armed units.
        double myLandAttack = 0;
        j.key("mine").arr();
        for (Unit u : me.getUnitSet()) {
            if (u.isDisposed()) continue;
            final double a = cm.getOffencePower(u, null);
            if (a <= 0 && !u.isDefensiveUnit()) continue;
            if (!u.isNaval()) myLandAttack += a;
            j.obj().put("kind", CompanionNations.unitKind(u))
                .put("name", label(u))
                .put("where", where(u, me))
                .put("attack10", Math.round(10 * a))
                .put("defence10", Math.round(10 * cm.getDefencePower(null, u)))
                .put("naval", u.isNaval())
                .put("seized", u.isInEurope() || (u.getTile() == null
                    && u.getDestination() == me.getEurope()))
                .put("veteran", "model.unit.veteranSoldier".equals(u.getType().getId())
                    || "model.unit.colonialRegular".equals(u.getType().getId())
                    || "model.unit.continentalArmy".equals(u.getType().getId()))
                .end();
        }
        j.endArr();
        j.put("myLandAttack", Math.round(myLandAttack));
        int muskets = 0, horses = 0;
        for (Colony c : me.getColonyList()) {
            muskets += c.getGoodsCount(spec.getGoodsType("model.goods.muskets"));
            horses += c.getGoodsCount(spec.getGoodsType("model.goods.horses"));
        }
        j.put("muskets", muskets).put("horses", horses);

        // Colony by colony: walls, best defender, chances, muster.
        final UnitType regular = spec.getUnitType("model.unit.kingsRegular");
        final Role infantry = spec.getRole("model.role.infantry");
        final Role cavalry = spec.getRole("model.role.cavalry");
        final UnitType artillery = spec.getUnitType("model.unit.artillery");
        j.key("colonies").arr();
        for (Colony c : me.getColonyList()) {
            final int sol = c.getSonsOfLiberty();
            final Unit def = c.getTile().getDefendingUnit(null);
            final double d = (def == null) ? 0 : cm.getDefencePower(null, def);
            final Building stockade = c.getStockade();
            final int tories = 100 - sol;
            final double aInf = (regular == null) ? 0
                : attack(spec, turn, regular, infantry, refType, tories, true);
            final double aCav = (regular == null) ? 0
                : attack(spec, turn, regular, cavalry, refType, tories, true);
            final double aArt = (artillery == null) ? 0
                : attack(spec, turn, artillery, spec.getDefaultRole(), refType, tories, true);
            final List<Unit> all = c.getAllUnitsList();
            int vets = 0;
            for (Unit u : all) {
                if (u.getUnitChange(net.sf.freecol.common.model.UnitChangeType.INDEPENDENCE) != null) vets++;
            }
            final int limit = musterLimit(sol, all.size());
            int armed = 0;
            for (Unit u : c.getTile().getUnitList()) {
                if (u.isDefensiveUnit() && !u.isNaval()) armed++;
            }
            // The best we could do here with what is in the colony:
            // the best soldier-type unit, armed with what is in store,
            // fortified.
            final net.sf.freecol.common.model.GoodsType mt
                = spec.getGoodsType("model.goods.muskets");
            final net.sf.freecol.common.model.GoodsType ht
                = spec.getGoodsType("model.goods.horses");
            Role bestRole = null;
            if (c.getGoodsCount(mt) >= 50) {
                bestRole = (c.getGoodsCount(ht) >= 50)
                    ? spec.getRole("model.role.dragoon")
                    : spec.getRole("model.role.soldier");
            }
            double best = d;
            String bestText = "";
            if (bestRole != null || (def != null && def.isDefensiveUnit())) {
                UnitType bestType = null;
                for (Unit u : all) {
                    if (u.isNaval() || !u.isPerson()) continue;
                    if (u.hasAbility(net.sf.freecol.common.model.Ability.EXPERT_SOLDIER)
                        || bestType == null) bestType = u.getType();
                    if (u.hasAbility(net.sf.freecol.common.model.Ability.EXPERT_SOLDIER)) break;
                }
                if (bestType != null) {
                    Role r = (bestRole != null) ? bestRole
                        : (def != null) ? def.getRole() : null;
                    double b = defence(spec, turn, me, bestType, r, c, true);
                    if (b > best + 0.05) {
                        best = b;
                        bestText = Messages.getName(bestType) + " as "
                            + Messages.getName(r).toLowerCase() + ", fortified";
                    }
                }
            }
            j.obj().put("name", c.getName()).put("x", c.getTile().getX())
                .put("bestText", bestText)
                .put("bestDefence10", Math.round(10 * best))
                .put("bestHoldInf", Math.round(100 * holdChance(aInf, best)))
                .put("bestHoldCav", Math.round(100 * holdChance(aCav, best)))
                .put("y", c.getTile().getY())
                .put("sol", sol).put("units", c.getUnitCount())
                .put("walls", (stockade == null) ? ""
                    : Messages.getName(stockade.getType()))
                .put("armed", armed)
                .put("defender", (def == null) ? "" : label(def))
                .put("defenderArmed", def != null && def.isDefensiveUnit())
                .put("defence10", Math.round(10 * d))
                .put("holdInf", Math.round(100 * holdChance(aInf, d)))
                .put("holdCav", Math.round(100 * holdChance(aCav, d)))
                .put("holdArt", Math.round(100 * holdChance(aArt, d)))
                .put("attackInf10", Math.round(10 * aInf))
                .put("musterLimit", limit).put("veterans", vets)
                .put("muster", Math.min(limit, vets))
                .end();
        }
        j.endArr();

        // Help after declaring.
        j.put("interventionBells", spec.getInteger(GameOptions.INTERVENTION_BELLS));
        j.key("intervention").arr();
        if (monarch != null) {
            for (AbstractUnit au : monarch.getInterventionForce().getUnitList()) {
                j.obj().put("number", au.getNumber())
                    .put("name", Messages.message(au.getLabel())).end();
            }
        }
        j.endArr();
        j.put("landRequired", REF_LAND_UNITS_REQUIRED)
            .put("navalRequired", REF_NAVAL_UNITS_REQUIRED);
        return j.end().toString();
    }

    private static String kind(UnitType ut, Role role) {
        if (ut.isNaval()) return "warship";
        if ("model.unit.artillery".equals(ut.getId())) return "artillery";
        if (role != null && role.getId().endsWith("cavalry")) return "dragoon";
        return "soldier";
    }
}
