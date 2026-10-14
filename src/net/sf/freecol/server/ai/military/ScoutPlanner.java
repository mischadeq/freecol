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

import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.Action;
import net.sf.freecol.common.diplomacy.Bearings;
import net.sf.freecol.common.diplomacy.Diplomat;
import net.sf.freecol.common.diplomacy.NationView;
import net.sf.freecol.common.diplomacy.Outlook;
import net.sf.freecol.common.diplomacy.ScoredAction;
import net.sf.freecol.common.diplomacy.Sightings;
import net.sf.freecol.common.diplomacy.Situation;
import net.sf.freecol.common.diplomacy.WorldView;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.AIUnit;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.ai.mission.Mission;
import net.sf.freecol.server.ai.mission.PrivateerMission;
import net.sf.freecol.server.ai.mission.ScoutBearingMission;
import net.sf.freecol.server.ai.mission.TransportMission;
import net.sf.freecol.server.ai.mission.UnitSeekAndDestroyMission;


/**
 * Sends a ship to look for an enemy we can not find (mod, diplomacy
 * plan).  A computer player does not get the map for free: it only
 * knows the colonies it has explored, and a guess of where the others
 * live from what it has seen of them ({@link Bearings}).  When its
 * diplomat says looking is worth it (the SCOUT move, mostly at war with
 * a nation whose colonies it has never seen), one ship sails that way.
 *
 * One scout at a time.  A warship goes first; a trading ship only when
 * it is empty and not the nation's only ship.
 */
public final class ScoutPlanner {

    private static final Logger logger = Logger.getLogger(ScoutPlanner.class.getName());

    private final EuropeanAIPlayer aiPlayer;


    public ScoutPlanner(EuropeanAIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
    }


    /**
     * Send a scout if the diplomat wants one.
     *
     * @param lb A {@code LogBuilder} to log to.
     * @return The scout sent, or null.
     */
    public AIUnit plan(LogBuilder lb) {
        final Player player = aiPlayer.getPlayer();
        final Game game = player.getGame();
        final Tile origin = Sightings.origin(player);
        if (origin == null || game.getMap() == null) return null;
        for (Unit u : (Iterable<Unit>)player.getUnits()::iterator) {
            AIUnit aiu = aiPlayer.getAIMain().getAIUnit(u);
            if (aiu != null && aiu.getMission() instanceof ScoutBearingMission
                && aiu.getMission().isValid()) return null;
        }
        final Outlook o = aiPlayer.getOutlook();
        final WorldView w = o.view;
        final Situation s = o.situation;
        Action best = null;
        for (ScoredAction sa : o.ranked) {
            if (sa.action.kind != Action.Kind.SCOUT) continue;
            if (sa.total < Diplomat.threshold(Action.Kind.SCOUT)) break;
            best = sa.action;
            break;
        }
        if (best == null) return null;
        final NationView v = w.others.get(best.target);
        if (v == null || v.bearing == null) return null;
        final Tile target = targetFor(game.getMap(), origin, v.bearing);
        if (target == null) return null;
        final AIUnit ship = pickShip(target);
        if (ship == null) return null;
        ship.changeMission(new ScoutBearingMission(ship.getAIMain(), ship,
            v.id, target, v.bearing.dx, v.bearing.dy));
        lb.add("\n  Scout ", ship.getUnit(), " looks ", v.bearing.compass(),
               " for ", v.id, " at ", target);
        logger.fine(player.getName() + " sends " + ship.getUnit()
                    + " to look for " + v.id + " " + v.bearing);
        return ship;
    }

    /**
     * Where to sail: open water at the guessed spot, or failing that
     * nearer along the same line.
     *
     * @param map The {@code Map}.
     * @param origin Our biggest colony.
     * @param b The guess.
     * @return A sea {@code Tile}, or null.
     */
    static Tile targetFor(Map map, Tile origin, Bearings.Bearing b) {
        for (double f : new double[] { 1, 1.5, 0.6, 2 }) {
            final double d = Math.max(ScoutBearingMission.STEP, b.distance) * f;
            Tile t = ScoutBearingMission.waterNear(map,
                origin.getX() + b.dx * d, origin.getY() + 2 * b.dy * d);
            if (t != null) return t;
        }
        return null;
    }

    /**
     * Pick a ship: a warship not chasing anything, else an empty trading
     * ship that is not our only ship.  The nearest one wins.
     *
     * @param target Where it will sail.
     * @return The {@code AIUnit}, or null.
     */
    AIUnit pickShip(Tile target) {
        final Player player = aiPlayer.getPlayer();
        int ships = 0;
        AIUnit war = null, trade = null;
        double warD = Double.MAX_VALUE, tradeD = Double.MAX_VALUE;
        for (Unit u : (Iterable<Unit>)player.getUnits()::iterator) {
            if (!u.isNaval()) continue;
            ships++;
            if (u.isDamaged() || u.isAtSea() || u.hasCargo()) continue;
            final AIUnit aiu = aiPlayer.getAIMain().getAIUnit(u);
            if (aiu == null) continue;
            final Mission m = aiu.getMission();
            if (m instanceof UnitSeekAndDestroyMission) continue;
            if (m instanceof PrivateerMission
                && m.getTarget() instanceof Unit) continue; // chasing
            if (m instanceof TransportMission
                && !((TransportMission)m).isEmpty()) continue;
            final Tile t = u.getTile();
            final double d = (t == null) ? 1000
                : Math.hypot(t.getX() - target.getX(),
                             (t.getY() - target.getY()) / 2.0);
            if (u.isOffensiveUnit()) {
                if (d < warD) { warD = d; war = aiu; }
            } else if (d < tradeD) {
                tradeD = d; trade = aiu;
            }
        }
        if (war != null) return war;
        return (ships >= 2) ? trade : null;
    }
}
