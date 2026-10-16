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

package net.sf.freecol.common.networking;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.diplomacy.SaleTerms;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * What a computer nation sells at one of its colonies (mod): the player
 * asks with the colony, the server answers with the terms (how much of
 * each goods, at what price; never the stock itself).
 */
public class SaleTermsMessage extends AttributeMessage {

    public static final String TAG = "saleTerms";
    private static final String COLONY_TAG = "colony";
    private static final String TERMS_TAG = "terms";
    private static final String GOODS_TAG = "goods";
    private static final String OFF_TAG = "off";
    private static final String RESULT_TAG = "result";


    /**
     * The player asks.
     *
     * @param colony The other nation's {@code Colony}.
     */
    public SaleTermsMessage(Colony colony) {
        super(TAG, COLONY_TAG, colony.getId());
    }

    /**
     * The player haggles: asks for a percentage off some goods.
     *
     * @param colony The other nation's {@code Colony}.
     * @param goodsId The goods.
     * @param off Percent more off.
     */
    public SaleTermsMessage(Colony colony, String goodsId, int off) {
        super(TAG, COLONY_TAG, colony.getId(), GOODS_TAG, goodsId,
              OFF_TAG, String.valueOf(off));
    }

    /**
     * The answer.
     *
     * @param colonyId The colony id.
     * @param terms The terms, as {@link SaleTerms#serialize} writes them.
     * @param result What came of a haggle, or null.
     */
    public SaleTermsMessage(String colonyId, String terms, String result) {
        super(TAG, COLONY_TAG, colonyId, TERMS_TAG, terms,
              RESULT_TAG, (result == null) ? "" : result);
    }

    /**
     * Create a new {@code SaleTermsMessage} from a stream.
     *
     * @param game The {@code Game} this message belongs to.
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if the stream is corrupt.
     */
    public SaleTermsMessage(Game game, FreeColXMLReader xr)
        throws XMLStreamException {
        super(TAG, xr, COLONY_TAG, TERMS_TAG, GOODS_TAG, OFF_TAG, RESULT_TAG);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean currentPlayerMessage() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessagePriority getPriority() {
        return Message.MessagePriority.LATE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void aiHandler(FreeColServer freeColServer, AIPlayer aiPlayer) {
        // Only the human player asks.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clientHandler(FreeColClient freeColClient) {
        igc(freeColClient).saleTermsHandler(getStringAttribute(COLONY_TAG),
            SaleTerms.parse(getStringAttribute(TERMS_TAG)),
            getStringAttribute(RESULT_TAG));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        final Game game = freeColServer.getGame();
        final String id = getStringAttribute(COLONY_TAG);
        final Colony colony = game.getFreeColGameObject(id, Colony.class);
        if (colony == null) {
            return serverPlayer.clientError("Not a colony: " + id);
        }
        final Player owner = colony.getOwner();
        String terms = "", result = null;
        if (owner != serverPlayer && serverPlayer.hasContacted(owner)) {
            final AIPlayer ai = freeColServer.getAIMain().getAIPlayer(owner);
            if (ai instanceof EuropeanAIPlayer) {
                final EuropeanAIPlayer eai = (EuropeanAIPlayer)ai;
                final String goods = getStringAttribute(GOODS_TAG);
                if (goods != null && !goods.isEmpty()) {
                    result = eai.haggle(colony, serverPlayer, goods,
                        getIntegerAttribute(OFF_TAG, 0)).name();
                }
                final List<SaleTerms.Line> lines
                    = eai.saleTerms(colony, serverPlayer);
                terms = SaleTerms.serialize(lines);
            }
        }
        return ChangeSet.simpleChange(serverPlayer,
                                      new SaleTermsMessage(id, terms, result));
    }
}
