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

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * An offer from a computer nation (mod): peace terms by messenger, or a
 * notice on the notice board.  The server sends the offer to the
 * player; the player answers with its id and accept or decline.
 */
public class OfferMessage extends AttributeMessage {

    public static final String TAG = "offer";
    private static final String OFFER_TAG = "offer";
    private static final String ANSWER_TAG = "answer";


    /**
     * An offer, for the player.
     *
     * @param offer The {@code Offer}.
     */
    public OfferMessage(Offer offer) {
        super(TAG, OFFER_TAG, offer.serialize());
    }

    /**
     * The player's answer.
     *
     * @param offerId The offer id.
     * @param accept True to accept.
     */
    public OfferMessage(String offerId, boolean accept) {
        super(TAG, OFFER_TAG, offerId, ANSWER_TAG, String.valueOf(accept));
    }

    /**
     * Create a new {@code OfferMessage} from a stream.
     *
     * @param game The {@code Game} this message belongs to.
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if the stream is corrupt.
     */
    public OfferMessage(Game game, FreeColXMLReader xr)
        throws XMLStreamException {
        super(TAG, xr, OFFER_TAG, ANSWER_TAG);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean currentPlayerMessage() {
        return false;
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
        // Offers are only made to human players.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clientHandler(FreeColClient freeColClient) {
        final Offer offer = Offer.parse(getStringAttribute(OFFER_TAG));
        if (offer != null) igc(freeColClient).offerHandler(offer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        final String id = getStringAttribute(OFFER_TAG);
        final Boolean accept = getBooleanAttribute(ANSWER_TAG, (Boolean)null);
        if (id == null || accept == null) {
            return serverPlayer.clientError("Bad offer answer: " + id);
        }
        return igc(freeColServer).answerOffer(serverPlayer, id, accept);
    }
}
