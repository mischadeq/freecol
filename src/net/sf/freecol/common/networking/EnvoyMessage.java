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
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * The player's envoys (step 8, mod).  The player sends one (to a
 * nation, a kind, gold) or just asks how they stand; the server answers
 * with the status of every computer nation he knows ("nation:wait:
 * readyTurn;...") and why an envoy could not go, if it could not.
 */
public class EnvoyMessage extends AttributeMessage {

    public static final String TAG = "envoy";
    private static final String TO_TAG = "to";
    private static final String KIND_TAG = "kind";
    private static final String GOLD_TAG = "gold";
    private static final String STATUS_TAG = "status";
    private static final String ERROR_TAG = "error";


    /**
     * Ask how the envoys stand.
     */
    public EnvoyMessage() {
        super(TAG);
    }

    /**
     * Send an envoy.
     *
     * @param to The nation id.
     * @param kind The envoy kind name.
     * @param gold The gold sent with it.
     */
    public EnvoyMessage(String to, String kind, int gold) {
        super(TAG, TO_TAG, to, KIND_TAG, kind, GOLD_TAG, String.valueOf(gold));
    }

    /**
     * The answer.
     *
     * @param status The status lines.
     * @param error Why the envoy could not go (a message key), or null.
     */
    public EnvoyMessage(String status, String error) {
        super(TAG, STATUS_TAG, status, ERROR_TAG, (error == null) ? "" : error);
    }

    /**
     * Create a new {@code EnvoyMessage} from a stream.
     *
     * @param game The {@code Game} this message belongs to.
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if the stream is corrupt.
     */
    public EnvoyMessage(Game game, FreeColXMLReader xr)
        throws XMLStreamException {
        super(TAG, xr, TO_TAG, KIND_TAG, GOLD_TAG, STATUS_TAG, ERROR_TAG);
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
        // Only the human player sends envoys.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clientHandler(FreeColClient freeColClient) {
        final String error = getStringAttribute(ERROR_TAG);
        igc(freeColClient).envoyHandler(getStringAttribute(STATUS_TAG),
            (error == null || error.isEmpty()) ? null : error);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        return igc(freeColServer).envoy(serverPlayer,
            getStringAttribute(TO_TAG), getStringAttribute(KIND_TAG),
            getIntegerAttribute(GOLD_TAG, 0));
    }
}
