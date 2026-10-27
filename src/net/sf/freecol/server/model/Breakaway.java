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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeSet.See;
import net.sf.freecol.server.FreeColServer;


/**
 * A breakaway tribe (mod, plans/breakaway-tribe.md step 2): mission
 * villages that leave their tribe together found a new tribe, named by
 * the European who protects them, allied to him and at war with the
 * tribe they left.  It takes one of the spare nations
 * ({@link Nation#isBreakaway}), at most three in a game.
 */
public final class Breakaway {

    private static final Logger logger = Logger.getLogger(Breakaway.class.getName());

    private Breakaway() {}


    /**
     * A spare nation no player has had yet in this game.
     *
     * @param game The {@code Game}.
     * @return The {@code Nation}, or null if all three are used.
     */
    public static Nation freeSlot(Game game) {
        for (Nation n : game.getSpecification().getNations()) {
            if (n.isBreakaway() && game.getPlayerByNationId(n.getId()) == null) {
                return n;
            }
        }
        return null;
    }

    /**
     * Found a breakaway tribe.
     *
     * @param server The {@code FreeColServer} (makes the new player).
     * @param parent The tribe the villages leave.
     * @param protector The European who protects them.
     * @param villages The villages (owned by the parent).
     * @param name The new tribe's name.
     * @param cs A {@code ChangeSet} to update.
     * @return The new tribe, or null if there is no spare nation.
     */
    public static ServerPlayer found(FreeColServer server, ServerPlayer parent,
                                     ServerPlayer protector,
                                     List<IndianSettlement> villages,
                                     String name, ChangeSet cs) {
        final ServerGame game = (ServerGame)parent.getGame();
        final Nation nation = freeSlot(game);
        if (nation == null || villages.isEmpty()) return null;

        final ServerPlayer tribe = server.makeAIPlayer(nation);
        tribe.changeNationType(parent.getNationType());
        tribe.setIndependentNationName(name);
        tribe.setBreakawayFrom(parent.getNationId());
        tribe.setProtector(protector.getNationId());
        // It knows whom its old tribe knew, and the land it knew.
        for (Player p : game.getLivePlayerList(tribe)) {
            if (p == parent || p == protector || parent.hasContacted(p)) {
                Player.makeContact(tribe, p);
            }
        }
        final Set<Tile> known = new HashSet<>();
        game.getMap().forEachTile(t -> t.isExploredBy(parent), t -> known.add(t));
        tribe.exploreTiles(known);
        // Everyone learns of the new player before anything refers to it.
        game.updatePlayers(List.of(tribe));

        for (IndianSettlement is : villages) {
            move(is, parent, tribe, cs);
            is.setAlarm(protector, new Tension(Tension.TENSION_MIN));
            is.setAlarm(parent, new Tension(Tension.Level.HATEFUL.getLimit()));
        }

        tribe.csChangeStance(Stance.ALLIANCE, protector, true, cs);
        tribe.csChangeStance(Stance.WAR, parent, true, cs);
        tribe.getTension(protector).setValue(Tension.TENSION_MIN);
        protector.getTension(tribe).setValue(Tension.TENSION_MIN);

        final List<String> names = new ArrayList<>();
        for (IndianSettlement is : villages) names.add(is.getName());
        cs.addMessage(protector, new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, "breakaway.founded",
            tribe)
            .addStringTemplate("%nation%", tribe.getNationLabel())
            .addStringTemplate("%old%", parent.getNationLabel())
            .addName("%settlements%", String.join(", ", names)));
        TribeNews.add(game, new TribeNews.News(game.getTurn().getNumber(),
            TribeNews.Kind.BROKE_AWAY, tribe.getNationId(),
            parent.getNationId(), protector.getNationId(),
            String.join(", ", names)));
        logger.info("Breakaway " + name + " (" + nation.getId() + ") from "
            + parent.getNationId() + " under " + protector.getNationId()
            + ": " + names);
        return tribe;
    }

    /**
     * A later village joins a breakaway tribe (its mission is the
     * protector's, it would break off to him).
     *
     * @param is The {@code IndianSettlement}.
     * @param parent The tribe it leaves.
     * @param tribe The breakaway tribe.
     * @param protector The protector.
     * @param cs A {@code ChangeSet} to update.
     */
    public static void join(IndianSettlement is, ServerPlayer parent,
                            ServerPlayer tribe, Player protector,
                            ChangeSet cs) {
        move(is, parent, tribe, cs);
        is.setAlarm(protector, new Tension(Tension.TENSION_MIN));
        is.setAlarm(parent, new Tension(Tension.Level.HATEFUL.getLimit()));
        cs.addMessage(protector, new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, "breakaway.joined",
            tribe)
            .addName("%settlement%", is.getName())
            .addStringTemplate("%nation%", tribe.getNationLabel())
            .addStringTemplate("%old%", parent.getNationLabel()));
        TribeNews.add(tribe.getGame(), new TribeNews.News(
            tribe.getGame().getTurn().getNumber(), TribeNews.Kind.BROKE_AWAY,
            tribe.getNationId(), parent.getNationId(),
            protector.getNationId(), is.getName()));
    }

    /**
     * An abandoned breakaway tribe goes back to the tribe it left, with
     * all its villages and braves; it then has nothing left and leaves
     * the game.
     *
     * @param tribe The breakaway tribe.
     * @param parent The tribe it left.
     * @param cs A {@code ChangeSet} to update.
     */
    public static void rejoin(ServerPlayer tribe, ServerPlayer parent,
                              ChangeSet cs) {
        final Game game = tribe.getGame();
        final Player protector = (tribe.getProtector() == null) ? null
            : game.getPlayerByNationId(tribe.getProtector());
        final List<String> names = new ArrayList<>();
        for (IndianSettlement is : tribe.getIndianSettlementList()) {
            names.add(is.getName());
            move(is, tribe, parent, cs);
            is.setAlarm(parent, new Tension(Tension.TENSION_MIN));
        }
        // Braves left without a home village go back too.
        for (Unit u : tribe.getUnits().collect(java.util.stream.Collectors.toList())) {
            if (tribe.csChangeOwner(u, parent, null, null, cs)
                && u.getTile() != null) {
                cs.add(See.perhaps(), u.getTile());
            }
        }
        tribe.setProtector(null);
        if (protector != null && !protector.isDead()) {
            cs.addMessage(protector, new ModelMessage(
                ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                "breakaway.rejoined", parent)
                .addStringTemplate("%nation%", tribe.getNationLabel())
                .addStringTemplate("%old%", parent.getNationLabel()));
        }
        TribeNews.add(game, new TribeNews.News(game.getTurn().getNumber(),
            TribeNews.Kind.REJOINED, tribe.getNationId(),
            parent.getNationId(),
            (protector == null) ? null : protector.getNationId(),
            String.join(", ", names)));
        logger.info("Breakaway " + tribe.getNationId() + " rejoins "
            + parent.getNationId() + ": " + names);
    }

    /**
     * Move a village, its braves and its land to another tribe.
     *
     * @param is The {@code IndianSettlement}.
     * @param from The old owner.
     * @param to The new owner.
     * @param cs A {@code ChangeSet} to update.
     */
    static void move(IndianSettlement is, ServerPlayer from, ServerPlayer to,
                     ChangeSet cs) {
        final Set<Unit> units = new LinkedHashSet<>(is.getUnitList());
        units.addAll(is.getOwnedUnitList());
        final Set<Tile> tiles = new HashSet<>(is.getOwnedTiles());
        tiles.add(is.getTile());
        for (Tile t : tiles) t.cacheUnseen();//+til
        is.changeOwner(to);//-vis,-til
        is.setAlliedPlayer(null);
        for (Unit u : units) {
            if (u.getOwner() != from) continue;
            final IndianSettlement home = u.getHomeIndianSettlement();
            if (!from.csChangeOwner(u, to, null, null, cs)) continue;
            // A change of owner clears the brave's home: it keeps it.
            if (home != null) u.changeHomeIndianSettlement(home);
            if (u.getTile() != null) tiles.add(u.getTile());
        }
        cs.add(See.perhaps().always(from).always(to), tiles);
    }
}
