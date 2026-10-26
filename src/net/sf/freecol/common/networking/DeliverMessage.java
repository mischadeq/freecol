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
 * Deliver a notice-board deal now (mod): the player moved a ship or a
 * wagon train into the other nation's colony to hand over the goods
 * (or fetch them). The server answers "done", "part" or "none".
 */
public class DeliverMessage extends AttributeMessage {

    public static final String TAG = "deliver";
    private static final String COLONY_TAG = "colony";
    private static final String RESULT_TAG = "result";


    /**
     * Ask to deliver at a colony.
     *
     * @param colonyId The colony id.
     */
    public DeliverMessage(String colonyId) {
        super(TAG, COLONY_TAG, colonyId);
    }

    /**
     * The answer.
     *
     * @param colonyId The colony id.
     * @param result "done", "part" or "none".
     */
    public DeliverMessage(String colonyId, String result) {
        super(TAG, COLONY_TAG, colonyId, RESULT_TAG, result);
    }

    /**
     * Create a new {@code DeliverMessage} from a stream.
     *
     * @param game The {@code Game} this message belongs to.
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if the stream is corrupt.
     */
    public DeliverMessage(Game game, FreeColXMLReader xr)
        throws XMLStreamException {
        super(TAG, xr, COLONY_TAG, RESULT_TAG);
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
        // Only the human player delivers this way.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clientHandler(FreeColClient freeColClient) {
        igc(freeColClient).deliverHandler(getStringAttribute(RESULT_TAG));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        return igc(freeColServer).deliver(serverPlayer,
            getStringAttribute(COLONY_TAG));
    }
}
