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


package net.sf.freecol.server.ai.mission;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.AIMain;
import net.sf.freecol.server.ai.AIUnit;


/**
 * Sail a ship towards where we think an enemy lives, to find its
 * colonies (mod, diplomacy plan: "where are they").
 *
 * The nation only has a guess (a direction and a rough distance, from
 * what it has seen of the enemy, see
 * {@link net.sf.freecol.common.diplomacy.Bearings}).  The ship sails to
 * the sea near the guess, and if nothing is there, on the same way in
 * a zigzag (the guess is rough).  The mission ends as soon as one of the enemy's colonies has
 * been seen (then the invasion planner can see it too), or after
 * {@link #MAX_TURNS} turns.
 */
public class ScoutBearingMission extends Mission {

    public static final String TAG = "scoutBearingMission";

    /** The tag for this mission. */
    private static final String tag = "AI bearing scout";

    /** Give up after this many turns. */
    public static final int MAX_TURNS = 12;
    /** How much further to look when nothing is at the target (tiles). */
    public static final int STEP = 8;
    /** Each leg turns this much off the guess, left then right (degrees). */
    public static final double ZIGZAG = 35;
    /** How far from a guessed point to look for open water (tiles). */
    public static final int WATER_RADIUS = 6;

    /** The sea tile to sail to. */
    private Tile target = null;
    /** The nation we look for. */
    private String nation = null;
    /** The turn we set out. */
    private int start = 0;
    /** The direction of the guess, x east and y south (in tiles). */
    private double dx = 0, dy = 0;
    /** Legs sailed so far past the first target. */
    private int leg = 0;


    /**
     * Creates a mission for the given {@code AIUnit}.
     *
     * @param aiMain The main AI-object.
     * @param aiUnit The {@code AIUnit} this mission is created for.
     * @param nation The nation id to look for.
     * @param target The sea tile to sail to.
     * @param dx The direction of the guess, east.
     * @param dy The direction of the guess, south.
     */
    public ScoutBearingMission(AIMain aiMain, AIUnit aiUnit, String nation,
                               Tile target, double dx, double dy) {
        super(aiMain, aiUnit);
        this.nation = nation;
        this.target = target;
        this.dx = dx;
        this.dy = dy;
        this.start = getGame().getTurn().getNumber();
    }

    /**
     * Creates a new {@code ScoutBearingMission} from a reader.
     *
     * @param aiMain The main AI-object.
     * @param aiUnit The {@code AIUnit} this mission is created for.
     * @param xr The input stream containing the XML.
     * @throws XMLStreamException if a problem was encountered
     *      during parsing.
     */
    public ScoutBearingMission(AIMain aiMain, AIUnit aiUnit,
                               FreeColXMLReader xr) throws XMLStreamException {
        super(aiMain, aiUnit);
        readFromXML(xr);
    }


    /** The nation we look for. */
    public String getNation() {
        return nation;
    }

    /**
     * Has a player seen one of a nation's colonies?
     *
     * @param player The {@code Player} looking.
     * @param nation The nation id.
     * @return True if a colony of that nation is on an explored tile.
     */
    public static boolean found(Player player, String nation) {
        final Player other = player.getGame().getPlayerByNationId(nation);
        if (other == null) return false;
        for (Colony c : other.getColonyList()) {
            if (c.getTile().isExploredBy(player)) return true;
        }
        return false;
    }

    /**
     * The open water nearest to a point.  North-south steps count half
     * (map rows are half a tile high).
     *
     * @param map The {@code Map}.
     * @param x The x of the point (may be off the map).
     * @param y The y of the point (may be off the map).
     * @return The nearest water {@code Tile} within
     *     {@link #WATER_RADIUS}, or null.
     */
    public static Tile waterNear(Map map, double x, double y) {
        final int cx = (int)Math.max(0, Math.min(map.getWidth() - 1, Math.round(x)));
        final int cy = (int)Math.max(0, Math.min(map.getHeight() - 1, Math.round(y)));
        Tile best = null;
        double bestD = Double.MAX_VALUE;
        for (int j = -2 * WATER_RADIUS; j <= 2 * WATER_RADIUS; j++) {
            for (int i = -WATER_RADIUS; i <= WATER_RADIUS; i++) {
                final Tile t = map.getTile(cx + i, cy + j);
                if (t == null || t.isLand() || !t.isHighSeasConnected()) continue;
                final double d = Math.hypot(i, j / 2.0);
                if (d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
        }
        return (bestD <= WATER_RADIUS) ? best : null;
    }

    /**
     * Where to look next when nothing was at the target: further the
     * same way, zigzagging left and right of the guess so the ship sees
     * a wider band (the guess is only rough).
     *
     * @return The next sea {@code Tile}, or null if there is none.
     */
    Tile further() {
        if (target == null) return null;
        final Map map = getGame().getMap();
        final double[] turns = { ZIGZAG, -ZIGZAG, 0 };
        for (int k = 0; k < turns.length; k++) {
            final double a = Math.toRadians((k < 2 && leg % 2 == 1) ? -turns[k]
                                            : turns[k]);
            final double vx = dx * Math.cos(a) - dy * Math.sin(a);
            final double vy = dx * Math.sin(a) + dy * Math.cos(a);
            final Tile t = waterNear(map, target.getX() + vx * STEP,
                                     target.getY() + 2 * vy * STEP);
            if (t != null && t != target) {
                leg++;
                return t;
            }
        }
        return null;
    }

    // Implement Mission
    //   Inherit dispose, getBaseTransportPriority, getTransportDestination

    @Override
    public Location getTarget() {
        return target;
    }

    @Override
    public void setTarget(Location target) {
        if (target instanceof Tile) this.target = (Tile)target;
    }

    @Override
    public Location findTarget() {
        return target;
    }

    @Override
    public boolean isOneTime() {
        return false;
    }

    @Override
    public String invalidReason() {
        String reason = invalidAIUnitReason(getAIUnit());
        if (reason != null) return reason;
        final Unit unit = getUnit();
        if (!unit.isNaval()) return "not a ship";
        if (target == null || nation == null) return "no target";
        if (getGame().getTurn().getNumber() - start > MAX_TURNS) return "gave up";
        final Player other = getGame().getPlayerByNationId(nation);
        if (other == null || other.isDead()) return "nation gone";
        if (found(getPlayer(), nation)) return "found them";
        return null;
    }

    @Override
    public Mission doMission(LogBuilder lb) {
        lb.add(tag, " for ", nation);
        String reason = invalidReason();
        if ("found them".equals(reason)) return lbDone(lb, false, reason);
        if (reason != null) return lbFail(lb, false, reason);

        final Unit unit = getUnit();
        if (unit.getTile() == target) { // nothing here, look further
            Tile next = further();
            if (next == null) return lbDone(lb, false, "nothing that way");
            target = next;
            lbRetarget(lb);
        }
        Unit.MoveType mt = travelToTarget(target, null, lb);
        switch (mt) {
        case MOVE: // arrived; looks further next turn
            break;
        case MOVE_HIGH_SEAS: case MOVE_NO_MOVES:
            return lbWait(lb);
        case MOVE_ILLEGAL:
            return lbFail(lb, false, "no way there");
        case MOVE_NO_REPAIR:
            return lbFail(lb, false, AIUNITDIED);
        case MOVE_NO_TILE:
            moveRandomly(tag, null);
            return lbDodge(lb);
        default:
            return lbMove(lb, mt);
        }
        if (found(getPlayer(), nation)) return lbDone(lb, false, "found them");
        return lbAt(lb);
    }


    // Serialization

    private static final String NATION_TAG = "nation";
    private static final String START_TAG = "start";
    private static final String TARGET_TAG = "target";
    private static final String DX_TAG = "dx";
    private static final String DY_TAG = "dy";
    private static final String LEG_TAG = "leg";

    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);
        if (target != null) xw.writeAttribute(TARGET_TAG, target.getId());
        if (nation != null) xw.writeAttribute(NATION_TAG, nation);
        xw.writeAttribute(START_TAG, start);
        xw.writeAttribute(DX_TAG, (float)dx);
        xw.writeAttribute(DY_TAG, (float)dy);
        xw.writeAttribute(LEG_TAG, leg);
    }

    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        super.readAttributes(xr);
        Location loc = xr.getLocationAttribute(getGame(), TARGET_TAG, false);
        target = (loc instanceof Tile) ? (Tile)loc : null;
        nation = xr.getAttribute(NATION_TAG, (String)null);
        start = xr.getAttribute(START_TAG, 0);
        dx = xr.getAttribute(DX_TAG, 0f);
        dy = xr.getAttribute(DY_TAG, 0f);
        leg = xr.getAttribute(LEG_TAG, 0);
    }

    @Override
    public String getXMLTagName() { return TAG; }
}
