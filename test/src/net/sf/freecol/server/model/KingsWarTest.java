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

package net.sf.freecol.server.model;

import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.WorldView;
import net.sf.freecol.common.diplomacy.WorldViewBuilder;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * A war the King orders adds no anger and leaves no grudge; anger
 * between European nations at peace fades (mod). Mischa's game: the
 * French King kept declaring war on the Dutch, and every war plus peace
 * left France angrier for good.
 */
public class KingsWarTest extends FreeColTestCase {

    private Game game;
    private ServerPlayer dutch, french;

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    private void setUpGame() {
        game = ServerTestHelper.startServerGame(getTestMap(true));
        dutch = getServerPlayer(game, "model.nation.dutch");
        french = getServerPlayer(game, "model.nation.french");
        dutch.setStance(french, Stance.PEACE);
        french.setStance(dutch, Stance.PEACE);
        dutch.getTension(french).setValue(0);
        french.getTension(dutch).setValue(0);
    }

    private boolean grudge(ServerPlayer who, ServerPlayer about) {
        WorldView w = WorldViewBuilder.build(game, who, true, null);
        for (Memory m : w.memories) {
            if (m.kind == Memory.Kind.WAR
                && about.getNationId().equals(m.about)) return true;
        }
        return false;
    }

    public void testKingsWarAddsNoAnger() {
        setUpGame();
        WarDeclarations.asKing(() ->
            french.csChangeStance(Stance.WAR, dutch, true, new ChangeSet()));
        assertEquals(Stance.WAR, french.getStance(dutch));
        assertEquals(0, french.getTension(dutch).getValue());
        assertEquals(0, dutch.getTension(french).getValue());
        assertFalse("no grudge for the King's war", grudge(french, dutch));
        assertFalse(grudge(dutch, french));
        // Peace after it does not leave anger either.
        french.csChangeStance(Stance.PEACE, dutch, true, new ChangeSet());
        assertEquals(0, french.getTension(dutch).getValue());
    }

    public void testOwnWarAngersAndOnlyTheVictimHoldsAGrudge() {
        setUpGame();
        french.csChangeStance(Stance.WAR, dutch, true, new ChangeSet());
        assertTrue(french.getTension(dutch).getValue() > 0);
        assertTrue("the Dutch were attacked", grudge(dutch, french));
        assertFalse("France declared it itself", grudge(french, dutch));
    }

    public void testAngerFadesInPeace() {
        setUpGame();
        french.getTension(dutch).setValue(500);
        french.csFadeTension();
        assertEquals(500 - ServerPlayer.TENSION_FADE,
                     french.getTension(dutch).getValue());
        // Not while at war.
        french.setStance(dutch, Stance.WAR);
        french.csFadeTension();
        assertEquals(500 - ServerPlayer.TENSION_FADE,
                     french.getTension(dutch).getValue());
        // Never below nothing.
        french.setStance(dutch, Stance.PEACE);
        french.getTension(dutch).setValue(4);
        french.csFadeTension();
        assertEquals(0, french.getTension(dutch).getValue());
    }
}
