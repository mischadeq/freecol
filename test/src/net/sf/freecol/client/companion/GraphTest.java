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

package net.sf.freecol.client.companion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.diplomacy.Coalition;
import net.sf.freecol.common.diplomacy.TribeCharacter;
import net.sf.freecol.common.diplomacy.TribeMood;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The relations graph: only what the player could know.
 */
public class GraphTest extends FreeColTestCase {

    private static final String DUTCH = "model.nation.dutch",
        FRENCH = "model.nation.french", SPANISH = "model.nation.spanish",
        ENGLISH = "model.nation.english", TUPI = "model.nation.tupi",
        ARAWAK = "model.nation.arawak";

    private static void stance(Player a, Player b, Stance s) {
        a.setStance(b, s);
        b.setStance(a, s);
    }

    public void testGraph() {
        Game game = getStandardGame();
        Player me = game.getPlayerByNationId(DUTCH);
        Player french = game.getPlayerByNationId(FRENCH);
        Player spanish = game.getPlayerByNationId(SPANISH);
        Player english = game.getPlayerByNationId(ENGLISH);
        Player tupi = game.getPlayerByNationId(TUPI);
        Player arawak = game.getPlayerByNationId(ARAWAK);
        stance(me, french, Stance.ALLIANCE);
        stance(me, spanish, Stance.PEACE);
        stance(me, tupi, Stance.PEACE);
        stance(me, arawak, Stance.PEACE);
        stance(french, spanish, Stance.WAR);
        stance(tupi, arawak, Stance.PEACE);
        Map<String, TribeMood> moods = new HashMap<>();
        TribeMood m = new TribeMood(TUPI, 10, TribeCharacter.TRADERS, ARAWAK, 5);
        moods.put(TUPI, m);
        List<Coalition> coalitions = new ArrayList<>();
        Coalition c = new Coalition(SPANISH, 5);
        c.members.add(FRENCH);
        c.members.add(DUTCH);
        coalitions.add(c);

        Json j = new Json().obj();
        CompanionGraph.put(j, game, me, game, moods, coalitions);
        String s = j.end().toString();
        assertTrue(s, s.contains("\"id\":\"" + DUTCH + "\""));
        assertTrue(s, s.contains("\"me\":true"));
        assertFalse("never met", s.contains(ENGLISH));
        assertTrue(s, s.contains("\"kind\":\"alliance\""));
        assertTrue("wars are public", s.contains("\"a\":\"" + FRENCH + "\",\"b\":\""
            + SPANISH + "\",\"kind\":\"war\"") || s.contains("\"a\":\"" + SPANISH
            + "\",\"b\":\"" + FRENCH + "\",\"kind\":\"war\""));
        assertFalse("no mission, no war: the rival is unknown", s.contains("rival"));
        assertTrue(s, s.contains("\"target\":\"" + SPANISH + "\""));

        // A war between the rivals is public.
        m.atWar.add(ARAWAK);
        j = new Json().obj();
        CompanionGraph.put(j, game, me, game, moods, coalitions);
        assertTrue(j.end().toString().contains("\"kind\":\"rival\""));

        // Without the server (multiplayer): only our own stances.
        j = new Json().obj();
        CompanionGraph.put(j, game, me, null, new HashMap<>(), new ArrayList<>());
        s = j.end().toString();
        assertFalse(s, s.contains("\"kind\":\"war\""));
        assertTrue(s, s.contains("\"kind\":\"alliance\""));
    }
}
