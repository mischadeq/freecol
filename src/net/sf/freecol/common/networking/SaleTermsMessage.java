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


    /**
     * The player asks.
     *
     * @param colony The other nation's {@code Colony}.
     */
    public SaleTermsMessage(Colony colony) {
        super(TAG, COLONY_TAG, colony.getId());
    }

    /**
     * The answer.
     *
     * @param colonyId The colony id.
     * @param terms The terms, as {@link SaleTerms#serialize} writes them.
     */
    public SaleTermsMessage(String colonyId, String terms) {
        super(TAG, COLONY_TAG, colonyId, TERMS_TAG, terms);
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
        super(TAG, xr, COLONY_TAG, TERMS_TAG);
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
            SaleTerms.parse(getStringAttribute(TERMS_TAG)));
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
        String terms = "";
        if (owner != serverPlayer && serverPlayer.hasContacted(owner)) {
            final AIPlayer ai = freeColServer.getAIMain().getAIPlayer(owner);
            if (ai instanceof EuropeanAIPlayer) {
                final List<SaleTerms.Line> lines
                    = ((EuropeanAIPlayer)ai).saleTerms(colony, serverPlayer);
                terms = SaleTerms.serialize(lines);
            }
        }
        return ChangeSet.simpleChange(serverPlayer,
                                      new SaleTermsMessage(id, terms));
    }
}
