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

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsTradeItem;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.AIMain;
import net.sf.freecol.server.ai.AIMessage;
import net.sf.freecol.server.ai.AIUnit;
import net.sf.freecol.server.ai.EuropeanAIPlayer;


/**
 * A computer ship sails to another nation's colony to trade (mod): it
 * brings goods to sell, or comes with gold to buy, and once next to the
 * colony opens the game's trade screen.  The deal is the diplomat's
 * (an {@link Offer} of kind SHIP_SELL or SHIP_BUY in the owner's book).
 */
public class TradeVoyageMission extends Mission {

    private static final java.util.logging.Logger logger
        = java.util.logging.Logger.getLogger(TradeVoyageMission.class.getName());

    public static final String TAG = "tradeVoyageMission";

    private static final String tag = "AI trade voyage";

    /** Turns before the voyage is given up. */
    public static final int MAX_TURNS = 15;

    /** The colony to trade with. */
    private Colony colony = null;
    /** The offer this voyage carries out. */
    private String offerId = null;
    private int start = 0;


    /**
     * Creates a mission for the given {@code AIUnit}.
     *
     * @param aiMain The main AI-object.
     * @param aiUnit The {@code AIUnit} this mission is created for.
     * @param colony The {@code Colony} to trade with.
     * @param offerId The id of the {@code Offer}.
     */
    public TradeVoyageMission(AIMain aiMain, AIUnit aiUnit, Colony colony,
                              String offerId) {
        super(aiMain, aiUnit);
        this.colony = colony;
        this.offerId = offerId;
        this.start = getGame().getTurn().getNumber();
    }

    /**
     * Creates a new {@code TradeVoyageMission} and reads the given
     * element.
     *
     * @param aiMain The main AI-object.
     * @param aiUnit The {@code AIUnit} this mission is created for.
     * @param xr The input stream containing the XML.
     * @exception XMLStreamException if a problem was encountered
     *      during parsing.
     */
    public TradeVoyageMission(AIMain aiMain, AIUnit aiUnit,
                              FreeColXMLReader xr) throws XMLStreamException {
        super(aiMain, aiUnit);
        readFromXML(xr);
    }


    /** The offer, from the owner's book, or null. */
    private Offer offer() {
        if (offerId == null || !getUnit().getOwner().isEuropean()) return null;
        for (Offer o : getEuropeanAIPlayer().getOffers()) {
            if (o.id.equals(offerId)) return o;
        }
        return null;
    }

    /** A water tile next to the colony, the ship's place to trade from. */
    public static Tile berth(Colony c, Unit ship) {
        if (ship.hasTile() && ship.getTile().isAdjacent(c.getTile())) {
            return ship.getTile();
        }
        for (Tile t : c.getTile().getSurroundingTiles(1)) {
            if (!t.isLand() && t.isHighSeasConnected()) return t;
        }
        return null;
    }

    /**
     * The deal to put to the other side.
     *
     * @param o The {@code Offer}.
     * @return The {@code DiplomaticTrade}, or null if it can not be made.
     */
    public DiplomaticTrade deal(Offer o) {
        final Player me = getUnit().getOwner(), other = colony.getOwner();
        final GoodsType gt = getSpecification().getGoodsType(o.goods);
        if (gt == null) return null;
        List<TradeItem> items = new ArrayList<>();
        if (o.kind == Offer.Kind.SHIP_SELL) {
            if (getUnit().getGoodsCount(gt) < o.amount) return null;
            items.add(new GoodsTradeItem(getGame(), me, other,
                new Goods(getGame(), getUnit(), gt, o.amount)));
            items.add(new GoldTradeItem(getGame(), other, me, o.gold));
        } else {
            if (colony.getGoodsCount(gt) < o.amount
                || me.getGold() < o.gold) return null;
            items.add(new GoodsTradeItem(getGame(), other, me,
                new Goods(getGame(), colony, gt, o.amount)));
            items.add(new GoldTradeItem(getGame(), me, other, o.gold));
        }
        return new DiplomaticTrade(getGame(), TradeContext.TRADE, me, other,
                                   items, 0);
    }


    // Implement Mission
    //   Inherit dispose, getBaseTransportPriority, getTransportDestination

    /**
     * {@inheritDoc}
     */
    @Override
    public Location getTarget() {
        return colony;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTarget(Location target) {
        if (target instanceof Colony) this.colony = (Colony)target;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Location findTarget() {
        return colony;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOneTime() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String invalidReason() {
        String reason = invalidAIUnitReason(getAIUnit());
        if (reason != null) return reason;
        final Unit unit = getUnit();
        if (!unit.isNaval() || !unit.canCarryGoods()) return "not a ship";
        if (colony == null || colony.isDisposed()) return "no colony";
        final Offer o = offer();
        if (o == null || o.state != Offer.State.OPEN) return "no offer";
        if (unit.getOwner().getStance(colony.getOwner()) == Stance.WAR) {
            return "at war";
        }
        if (getGame().getTurn().getNumber() - start > MAX_TURNS) return "gave up";
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mission doMission(LogBuilder lb) {
        lb.add(tag, " to ", colony);
        final String reason = invalidReason();
        if (reason != null) {
            final Offer o = offer();
            if (o != null && o.state == Offer.State.OPEN) {
                o.state = Offer.State.EXPIRED;
            }
            return lbFail(lb, false, reason);
        }
        final Unit unit = getUnit();
        final Tile berth = berth(colony, unit);
        if (berth == null) return lbFail(lb, false, "no berth");
        if (unit.getTile() != berth) {
            Unit.MoveType mt = travelToTarget(berth, null, lb);
            switch (mt) {
            case MOVE:
                break;
            case MOVE_HIGH_SEAS: case MOVE_NO_MOVES:
                return lbWait(lb);
            case MOVE_ILLEGAL:
                return lbFail(lb, false, "no way there");
            case MOVE_NO_REPAIR:
                return lbFail(lb, false, AIUNITDIED);
            case MOVE_NO_TILE:
                return lbWait(lb);
            default:
                return lbMove(lb, mt);
            }
            if (unit.getTile() != berth) return lbAt(lb);
        }
        // Next to the colony: open the trade screen.
        final Offer o = offer();
        final DiplomaticTrade dt = deal(o);
        o.state = Offer.State.DONE;
        if (dt == null) return lbFail(lb, false, "deal no longer possible");
        if (!AIMessage.askDiplomacy(getEuropeanAIPlayer(), unit, colony, dt)) {
            logger.warning("Trade voyage could not open the trade: " + o);
        }
        return lbDone(lb, false, "offered ", o);
    }


    // Serialization

    private static final String COLONY_TAG = "colony";
    private static final String OFFER_TAG = "offer";
    private static final String START_TAG = "start";

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);
        if (colony != null) xw.writeAttribute(COLONY_TAG, colony.getId());
        if (offerId != null) xw.writeAttribute(OFFER_TAG, offerId);
        xw.writeAttribute(START_TAG, start);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        super.readAttributes(xr);
        Location loc = xr.getLocationAttribute(getGame(), COLONY_TAG, false);
        colony = (loc instanceof Colony) ? (Colony)loc : null;
        offerId = xr.getAttribute(OFFER_TAG, (String)null);
        start = xr.getAttribute(START_TAG, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getXMLTagName() { return TAG; }
}
