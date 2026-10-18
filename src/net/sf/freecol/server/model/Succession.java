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
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.SuccessionRules;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeSet.See;
import net.sf.freecol.common.util.LogBuilder;


/**
 * The Treaty of Utrecht (mod, see {@link SuccessionRules}): the loser
 * cedes its colonies nearest the winner; small ones are abandoned and
 * their colonists sail home; a tenth of the people of the others (the
 * skilled first) and the garrison sail home too; the ceded colonies
 * start with no rebels (their bells were for the old crown).  The
 * loser stays in the game, at peace with the winner.
 */
public final class Succession {

    private static final Logger logger = Logger.getLogger(Succession.class.getName());

    private final ServerPlayer loser, winner;
    private final java.util.Random random;

    /** What happened, for the message and the tests. */
    final List<String> ceded = new ArrayList<>(), abandoned = new ArrayList<>();
    int left = 0;


    public Succession(ServerPlayer loser, ServerPlayer winner,
                      java.util.Random random) {
        this.loser = loser;
        this.winner = winner;
        this.random = random;
    }

    /** Tiles from a colony to the winner's nearest colony. */
    private int distanceToWinner(Colony c) {
        int best = Integer.MAX_VALUE;
        for (Colony w : winner.getColonyList()) {
            best = Math.min(best, w.getTile().getDistanceTo(c.getTile()));
        }
        return best;
    }

    /**
     * The colonies the loser cedes: the ones nearest the winner.
     *
     * @return The colonies, nearest first.
     */
    List<Colony> toCede() {
        final List<Colony> all = new ArrayList<>(loser.getColonyList());
        all.sort(Comparator.comparingInt(this::distanceToWinner)
                 .thenComparing(Colony::getName));
        return all.subList(0, SuccessionRules.ceded(all.size()));
    }

    /** A unit sails home to the loser's Europe (or stays if none). */
    private boolean sailHome(Unit u) {
        final Europe europe = loser.getEurope();
        if (europe == null) return false;
        u.setLocation(europe);//-vis
        u.setState((u.isNaval()) ? Unit.UnitState.ACTIVE
                   : Unit.UnitState.SENTRY);
        return true;
    }

    /**
     * Sign the treaty.
     *
     * @param cs A {@code ChangeSet} to update.
     * @param lb A {@code LogBuilder} to log to.
     */
    public void csTreaty(ChangeSet cs, LogBuilder lb) {
        for (Colony c : new ArrayList<>(toCede())) {
            final Tile tile = c.getTile();
            // The garrison and the ships in port go home.
            for (Unit u : new ArrayList<>(tile.getUnitList())) {
                if (u.getOwner() == loser) sailHome(u);
            }
            final List<Unit> people = new ArrayList<>(c.getUnitList());
            final int size = people.size();
            if (SuccessionRules.abandoned(size)) {
                for (Unit u : people) sailHome(u);
                abandoned.add(c.getName());
                lb.add(" ", c.getName(), "(abandoned)");
                loser.csDisposeSettlement(c, cs);
                continue;
            }
            // The skilled leave first: officials, officers, merchants.
            people.sort(Comparator.comparingInt(Unit::getSkillLevel)
                        .reversed().thenComparing(Unit::getId));
            final int n = SuccessionRules.leaving(size, random);
            for (int i = 0; i < n; i++) {
                if (sailHome(people.get(i))) left++;
            }
            // Their bells were for the old crown.
            final int liberty = c.getLiberty();
            c.modifyLiberty(-liberty);
            loser.modifyLiberty(liberty);
            ((ServerColony)c).csChangeOwner(winner, false, null, cs);
            ceded.add(c.getName());
            lb.add(" ", c.getName(), "(-", n, ")");
        }
        if (loser.getStance(winner) == Stance.WAR
            || loser.getStance(winner) == Stance.CEASE_FIRE) {
            loser.csChangeStance(Stance.PEACE, winner, true, cs);
        }
        loser.invalidateCanSeeTiles();
        winner.invalidateCanSeeTiles();
        cs.add(See.only(loser), loser);

        final StringTemplate loserLabel = loser.getNationLabel();
        final StringTemplate winnerLabel = winner.getNationLabel();
        final ModelMessage m = new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            "model.game.successionTreaty", winner)
            .addStringTemplate("%loserNation%", loserLabel)
            .addStringTemplate("%nation%", winnerLabel)
            .addName("%colonies%", (ceded.isEmpty()) ? "-"
                     : String.join(", ", ceded))
            .addStringTemplate("%abandoned%", (abandoned.isEmpty())
                ? StringTemplate.name("")
                : StringTemplate.template("model.game.successionAbandoned")
                    .addName("%settlements%", String.join(", ", abandoned)))
            .addStringTemplate("%left%", (left == 0)
                ? StringTemplate.name("")
                : StringTemplate.template("model.game.successionLeft")
                    .addAmount("%count%", left)
                    .addStringTemplate("%nation%", winnerLabel));
        cs.addGlobalMessage(loser.getGame(), null, m);
        cs.addGlobalHistory(loser.getGame(),
            new HistoryEvent(loser.getGame().getTurn(),
                HistoryEvent.HistoryEventType.SPANISH_SUCCESSION, null)
                .addStringTemplate("%loserNation%", loserLabel)
                .addStringTemplate("%nation%", winnerLabel));
        logger.info("Treaty of Utrecht: " + loser.getName() + " cedes "
            + ceded + " to " + winner.getName() + ", abandoned " + abandoned
            + ", " + left + " colonists sailed home");
    }
}
