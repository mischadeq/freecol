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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.Action;
import net.sf.freecol.common.diplomacy.CaptureSim;
import net.sf.freecol.common.diplomacy.Diplomat;
import net.sf.freecol.common.diplomacy.PlaceView;
import net.sf.freecol.common.diplomacy.ScoredAction;
import net.sf.freecol.common.diplomacy.Situation;
import net.sf.freecol.common.diplomacy.WorldView;
import net.sf.freecol.common.diplomacy.WorldViewBuilder;
import net.sf.freecol.common.model.CombatModel;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.AIUnit;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.ai.mission.Mission;
import net.sf.freecol.server.ai.mission.TransportMission;
import net.sf.freecol.server.ai.mission.UnitSeekAndDestroyMission;


/**
 * Plans attacks on enemy colonies and settlements, including across
 * the sea (mod, step 1 of the diplomacy plan).
 *
 * Until now a computer player only attacked what its soldiers could
 * reach overland within a few turns, so on a map of islands wars were
 * declared and nothing happened.  Each turn this asks the nation's
 * diplomat ({@link Diplomat}, from the nation's honest view) which
 * attack is worth making; if one scores above the threshold, it picks
 * the smallest force of spare units that takes the place with a good
 * chance, and gives them a seek-and-destroy mission on it.  Units that
 * must cross the sea ask for transport the usual way (the mission's
 * transport destination), so the existing transport missions carry
 * them and land them next to the target.
 *
 * Spare means: waiting in Europe, or not in a colony under attack and
 * not the last armed unit in its colony.  One attack at a time: a new
 * one waits until the last army has landed.  An army going by sea gets
 * a high transport priority, so ships carry it before ordinary cargo.  At
 * most one new attack a turn, and units already on their way are left
 * alone.
 */
public final class InvasionPlanner {

    private static final Logger logger = Logger.getLogger(InvasionPlanner.class.getName());

    /** The force is grown until it takes the place this often. */
    public static final double TARGET_CHANCE = 0.65;
    /** Below this chance, do not go at all. */
    public static final double MIN_CHANCE = 0.5;
    /** Most units sent on one attack. */
    public static final int MAX_FORCE = 8;
    /** Transport priority of an army going by sea (ordinary is 100). */
    public static final int INVASION_PRIORITY = 200;
    /** Distance at which a target counts as "far" (so its attackers are kept). */
    public static final int FAR = 10;

    private final EuropeanAIPlayer aiPlayer;


    public InvasionPlanner(EuropeanAIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
    }


    /**
     * Is a unit on its way to a far attack, which the military
     * coordinator should leave alone?
     *
     * @param aiu The {@code AIUnit}.
     * @return True if it is.
     */
    public static boolean onInvasion(AIUnit aiu) {
        final Mission m = aiu.getMission();
        if (!(m instanceof UnitSeekAndDestroyMission) || !m.isValid()) return false;
        final Location target = m.getTarget();
        if (!(target instanceof Settlement)) return false;
        final Unit unit = aiu.getUnit();
        if (!unit.getOwner().atWarWith(((Settlement)target).getOwner())) return false;
        final Tile here = unit.getTile(), there = target.getTile();
        return unit.isOnCarrier() || here == null || there == null
            || here.getContiguity() != there.getContiguity()
            || here.getDistanceTo(there) > FAR;
    }

    /**
     * The smallest force (strongest units first) that takes a place with
     * at least {@link #TARGET_CHANCE}, or the largest available if that
     * still reaches {@link #MIN_CHANCE}.
     *
     * @param attacks The attack of each available unit, strongest first.
     * @param target The place.
     * @param max The most units that can go.
     * @param seed Seed for the simulation.
     * @return The number of units to send, 0 for none.
     */
    public static int forceFor(double[] attacks, PlaceView target, int max,
                               long seed) {
        final int n = Math.min(Math.min(max, MAX_FORCE), attacks.length);
        for (int k = 1; k <= n; k++) {
            if (CaptureSim.attack(Arrays.copyOf(attacks, k), target, seed).chance
                >= TARGET_CHANCE) return k;
        }
        if (n > 0 && CaptureSim.attack(Arrays.copyOf(attacks, n), target,
                                       seed).chance >= MIN_CHANCE) return n;
        return 0;
    }

    /**
     * Launch at most one attack this turn.
     *
     * @param candidates Military units still free this turn; those sent
     *     are removed.
     * @param attacked Colonies under attack: they keep all their units.
     * @param threatened Colonies with enemies near (kept for later use;
     *     they keep one armed unit, like any colony).
     * @param lb A {@code LogBuilder} to log to.
     * @return The units sent.
     */
    public List<AIUnit> plan(Collection<AIUnit> candidates,
                             Set<Settlement> attacked,
                             Set<Settlement> threatened, LogBuilder lb) {
        final List<AIUnit> sent = new ArrayList<>();
        final Player player = aiPlayer.getPlayer();
        final Game game = player.getGame();
        if (!player.isAtWar() || candidates.isEmpty()) return sent;
        // One attack at a time: wait until the last one has landed.
        for (Unit u : (Iterable<Unit>)player.getUnits()::iterator) {
            AIUnit aiu = aiPlayer.getAIMain().getAIUnit(u);
            if (aiu != null && onInvasion(aiu)) return sent;
        }
        final WorldView w = WorldViewBuilder.build(game, player, true, null,
                                                   aiPlayer.getBearings());
        final Situation s = new Situation(w);
        final CombatModel cm = game.getCombatModel();
        for (ScoredAction sa : Diplomat.rank(s)) {
            if (sa.action.kind != Action.Kind.ATTACK_COLONY) continue;
            if (sa.total < Diplomat.THRESHOLD) break;
            final Situation.Attack at = s.attacks.get(sa.action.target);
            if (at == null) continue;
            final Settlement target = game.getFreeColGameObject(at.place.id,
                                                                Settlement.class);
            if (target == null || !player.atWarWith(target.getOwner())) continue;

            // Spare units, strongest against this target first.
            List<AIUnit> spare = new ArrayList<>();
            for (AIUnit aiu : candidates) {
                if (spare(aiu, attacked, threatened, sent)) spare.add(aiu);
            }
            spare.sort(Comparator.comparingDouble((AIUnit a) ->
                -cm.getOffencePower(a.getUnit(), target)));
            boolean bySea = true;
            for (AIUnit aiu : spare) {
                Tile t = aiu.getUnit().getTile();
                if (t != null && t.getContiguity() == target.getTile().getContiguity()) {
                    bySea = false;
                }
            }
            int max = (bySea) ? carrierSpace(player) : MAX_FORCE;
            double[] attacks = new double[spare.size()];
            for (int i = 0; i < attacks.length; i++) {
                attacks[i] = cm.getOffencePower(spare.get(i).getUnit(), target);
            }
            int n = forceFor(attacks, at.place, max,
                             31L * game.getTurn().getNumber() + target.getId().hashCode());
            if (n == 0) {
                lb.add("\n  Invasion of ", target.getName(), " skipped: too few spare units (",
                       spare.size(), ") or ships (", max, ")");
                continue;
            }
            for (AIUnit aiu : spare.subList(0, n)) {
                aiu.changeMission(new UnitSeekAndDestroyMission(aiu.getAIMain(), aiu, target));
                // Ahead of ordinary cargo, so the ships carry the army now.
                if (bySea) aiu.setTransportPriority(INVASION_PRIORITY);
                sent.add(aiu);
            }
            candidates.removeAll(sent);
            if (bySea) bookShips(new ArrayList<>(sent), lb);
            lb.add("\n  Invasion: ", n, " units to ", target.getName(),
                   " (", target.getOwner().getNationId(), ")", (bySea) ? " by sea" : "",
                   ", score ", (int)sa.total, ", ", sa.note);
            logger.info(player.getNationId() + " sends " + n + " units against "
                + target.getName() + ((bySea) ? " by sea" : "") + ": " + sa.parts());
            break;
        }
        return sent;
    }

    /**
     * Book armies waiting to sail on the ships now, instead of waiting
     * until a ship happens to look for cargo.  Called when an attack
     * starts and every turn after for those still waiting.
     *
     * @param army The units (those already aboard or booked are skipped).
     * @param lb A {@code LogBuilder} to log to.
     */
    public void bookShips(List<AIUnit> army, LogBuilder lb) {
        List<net.sf.freecol.server.ai.TransportableAIObject> waiting = new ArrayList<>();
        for (AIUnit aiu : army) {
            if (!aiu.getUnit().isOnCarrier() && aiu.getTransport() == null
                && aiu.getTransportDestination() != null) waiting.add(aiu);
        }
        if (waiting.isEmpty()) return;
        List<TransportMission> ships = new ArrayList<>();
        for (Unit u : (Iterable<Unit>)aiPlayer.getPlayer().getUnits()::iterator) {
            if (!u.isNaval()) continue;
            AIUnit c = aiPlayer.getAIMain().getAIUnit(u);
            TransportMission tm = (c == null) ? null
                : c.getMission(TransportMission.class);
            if (tm != null) ships.add(tm);
        }
        aiPlayer.allocateTransportables(waiting, ships, lb);
    }

    /**
     * Book the player's armies already on their way but still waiting
     * for a ship.
     *
     * @param lb A {@code LogBuilder} to log to.
     */
    public void bookWaitingArmies(LogBuilder lb) {
        List<AIUnit> army = new ArrayList<>();
        for (Unit u : (Iterable<Unit>)aiPlayer.getPlayer().getUnits()::iterator) {
            AIUnit aiu = aiPlayer.getAIMain().getAIUnit(u);
            if (aiu != null && onInvasion(aiu)) army.add(aiu);
        }
        bookShips(army, lb);
    }

    /** Can this unit go: on the map, and not needed to hold its colony? */
    private static boolean spare(AIUnit aiu, Set<Settlement> attacked,
                                 Set<Settlement> threatened,
                                 List<AIUnit> sent) {
        final Unit unit = aiu.getUnit();
        if (unit.isNaval() || !unit.isOffensiveUnit()) return false;
        // New soldiers waiting in Europe can sail straight to the front.
        if (unit.isInEurope()) return true;
        final Tile tile = unit.getTile();
        if (tile == null) return false;
        final Settlement here = tile.getSettlement();
        if (here == null) return true;
        if (attacked.contains(here)) return false;
        int others = 0;
        for (Unit u : tile.getUnitList()) {
            if (u != unit && u.isOffensiveUnit() && !u.isNaval()
                && sent.stream().noneMatch(a -> a.getUnit() == u)) others++;
        }
        // The game marks nearly every colony "threatened" while at war,
        // so a threatened colony keeps one armed unit like any other;
        // only a colony under attack keeps them all.
        return others >= 1;
    }

    /**
     * Free room on the player's ships that are not being repaired: an
     * army only sets out by sea when there is a ship to carry it.
     */
    private static int carrierSpace(Player player) {
        int space = 0;
        for (Unit u : (Iterable<Unit>)player.getUnits()::iterator) {
            if (u.isNaval() && !u.isDamaged()) space += u.getSpaceLeft();
        }
        return space;
    }
}
