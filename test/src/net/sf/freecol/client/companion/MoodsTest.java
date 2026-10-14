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

import net.sf.freecol.common.diplomacy.Goal;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.Mood;
import net.sf.freecol.common.diplomacy.NationCharacter;
import net.sf.freecol.common.diplomacy.NationView;
import net.sf.freecol.common.diplomacy.Outlook;
import net.sf.freecol.common.diplomacy.Relations;
import net.sf.freecol.common.diplomacy.WorldView;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The moods on the nation cards: in words, and nothing hidden.
 */
public class MoodsTest extends FreeColTestCase {

    private static final String DUTCH = "model.nation.dutch",
        FRENCH = "model.nation.french", ENGLISH = "model.nation.english",
        SPANISH = "model.nation.spanish";


    public void testMoodJson() {
        Game game = getGame();
        Player me = game.getPlayerByNationId(DUTCH);
        Player french = game.getPlayerByNationId(FRENCH);
        Player english = game.getPlayerByNationId(ENGLISH);
        Player spanish = game.getPlayerByNationId(SPANISH);
        me.setStance(french, Stance.PEACE);
        me.setStance(english, Stance.PEACE);
        me.setStance(spanish, Stance.PEACE);

        // The French: we sank two of their ships; at war with England,
        // at peace with Spain (whom they hate in secret).
        NationView self = new NationView(FRENCH, true, true);
        WorldView w = new WorldView(100, self, NationCharacter.CONQUEROR);
        w.memories.add(new Memory(Memory.Kind.UNIT_LOST, DUTCH, 98, 2));
        w.memories.add(new Memory(Memory.Kind.GIFT, DUTCH, 99, 1));
        w.memories.add(new Memory(Memory.Kind.WAR, SPANISH, 99, 3));
        view(w, DUTCH, Stance.PEACE, 400);
        view(w, ENGLISH, Stance.WAR, 1000);
        view(w, SPANISH, Stance.PEACE, 1000);
        Mood m = Mood.of(new Outlook(w));

        Json j = new Json().obj();
        CompanionMoods.put(j, m, game, me);
        String s = j.end().toString();
        assertTrue(s, s.contains("\"mood\":{"));
        assertTrue(s, s.contains("\"word\":\"" + m.about(DUTCH).mood() + "\""));
        assertTrue(s, s.contains("\"trust\":\"careful\""));
        assertTrue(s, s.contains("\"ambition\":\"conqueror\""));
        assertTrue(s, s.contains("lost 2 units or ships to you"));
        assertTrue(s, s.contains("your gift"));
        // England: at war, public.  Spain: at peace, their grudge is
        // their own business.
        assertTrue(s, s.contains(Messages.message(english.getNationLabel())));
        assertFalse(s, s.contains(Messages.message(spanish.getNationLabel())));
        assertFalse(s, s.contains("model.nation"));

        // Only goals a nation makes known: trade wishes and peace with
        // you.  War plans stay secret, also against you.
        assertNull(CompanionMoods.goal(
            new Goal(Goal.Type.REVENGE, DUTCH), game, me, m));
        assertNull(CompanionMoods.goal(
            new Goal(Goal.Type.TAKE_COLONY, DUTCH), game, me, m));
        assertNull(CompanionMoods.goal(
            new Goal(Goal.Type.TAKE_COLONY, ENGLISH), game, me, m));
        assertNull(CompanionMoods.goal(
            new Goal(Goal.Type.MAKE_PEACE, ENGLISH), game, me, m));
        assertEquals("make peace with you", CompanionMoods.goal(
            new Goal(Goal.Type.MAKE_PEACE, DUTCH), game, me, m));
        assertEquals("sell furs", CompanionMoods.goal(
            new Goal(Goal.Type.SELL_SURPLUS, "model.goods.furs"), game, me, m));

        // Nor does a missing mood (another human, a multiplayer game).
        Json n = new Json().obj();
        CompanionMoods.put(n, null, game, me);
        assertEquals("{}", n.end().toString());
    }

    private static void view(WorldView w, String id, Stance stance,
                             int tension) {
        NationView v = new NationView(id, true, false);
        v.stance = stance;
        v.tension = tension;
        v.opinion = Relations.opinion(tension, w.memories, id, w.turn);
        v.trust = Relations.trust(w.memories, id, w.turn);
        w.others.put(id, v);
    }

    /** Their units count only on the tiles we see now. */
    public void testUnitsSeenOnlyInSight() {
        net.sf.freecol.common.model.Game game
            = net.sf.freecol.server.ServerTestHelper.startServerGame(getTestMap(false));
        try {
            net.sf.freecol.server.model.ServerPlayer dutch = getServerPlayer(game, DUTCH);
            net.sf.freecol.server.model.ServerPlayer french = getServerPlayer(game, FRENCH);
            net.sf.freecol.common.model.Colony home = net.sf.freecol.util.test
                .FreeColTestUtils.getColonyBuilder().player(dutch)
                .colonyTile(game.getMap().getTile(5, 5)).initialColonists(1).build();
            dutch.exploreForSettlement(home);
            final net.sf.freecol.common.model.UnitType soldier
                = spec().getUnitType("model.unit.veteranSoldier");
            new net.sf.freecol.server.model.ServerUnit(game,
                game.getMap().getTile(6, 5), french, soldier);   // next door
            new net.sf.freecol.server.model.ServerUnit(game,
                game.getMap().getTile(15, 15), french, soldier); // far away
            dutch.invalidateCanSeeTiles();
            assertTrue(dutch.canSee(game.getMap().getTile(6, 5)));
            assertFalse(dutch.canSee(game.getMap().getTile(15, 15)));
            int[] v = new int[CompanionNations.MEASURES.length];
            java.util.Arrays.fill(v, -1);
            Json j = new Json().obj();
            CompanionNations.estimates(j, game, dutch, french, v);
            String s = j.end().toString();
            // One soldier next door; the one far away is out of sight.
            assertTrue(s, s.contains("\"units\":{\"soldier\":1}"));
        } finally {
            net.sf.freecol.server.ServerTestHelper.stopServerGame();
        }
    }
}
