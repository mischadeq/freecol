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

package net.sf.freecol.server.ai.diplomacy;

import java.util.Map;

import net.sf.freecol.common.diplomacy.Grievance;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.model.ServerGame;


/**
 * Grievances and ultimatums (mod, step 6): before a computer nation
 * declares war of its own choice, it complains, then sets terms, and
 * only then goes to war.
 */
public final class GrievancePlanner {

    /** Tiles from our colonies where their soldiers worry us. */
    public static final int SOLDIER_RADIUS = 2;
    /** Tiles from our colonies where their colonies crowd us. */
    public static final int CROWD_RADIUS = 4;
    /** Remembered harm (opinion points) that makes a grievance. */
    public static final double HARM_LINE = -20;

    private final EuropeanAIPlayer ai;


    public GrievancePlanner(EuropeanAIPlayer ai) {
        this.ai = ai;
    }

    private Player me() {
        return ai.getPlayer();
    }

    private Game game() {
        return ai.getGame();
    }

    private int now() {
        return game().getTurn().getNumber();
    }

    /**
     * Forget grievances that no longer count.
     */
    public void tidy() {
        final int now = now();
        ai.getGrievances().values().removeIf(g -> g.stale(now)
            || (g.stage == Grievance.Stage.WAR
                && me().getStance(game().getPlayerByNationId(g.against))
                    != net.sf.freecol.common.model.Stance.WAR));
    }

    /**
     * We want war with a nation: may we declare it now?  The first time
     * we complain; after a while we set terms; war only when they were
     * refused or ignored.  Terms that were met hold us back a while.
     *
     * @param p The other nation.
     * @param lb A {@code LogBuilder} to log to.
     * @return True if war may be declared now.
     */
    public boolean mayDeclareWar(Player p, LogBuilder lb) {
        final Map<String, Grievance> book = ai.getGrievances();
        final int now = now();
        final String id = p.getNationId();
        Grievance g = book.get(id);
        final ChangeSet cs = new ChangeSet();
        try {
            if (g != null && g.settled(now)) return false;
            if (g == null || g.stale(now) || g.stage == Grievance.Stage.SETTLED) {
                g = new Grievance(id, cause(p), now);
                book.put(id, g);
                tell(cs, p, "grievance.complaint", g);
                lb.add(" complains to ", p.getDebugName(), " (", g.cause, ")");
                return false;
            }
            switch (g.stage) {
            case COMPLAINT:
                if (g.turnsLeft(now) > 0) return false;
                g.stage = Grievance.Stage.ULTIMATUM;
                g.since = now;
                g.demand = Grievance.demandFor(p.getGold());
                lb.add(" ultimatum to ", p.getDebugName(), " (", g.demand, ")");
                if (p.isAI()) return computerAnswers(g, p, cs);
                final Offer o = new Offer("ultimatum:" + me().getNationId()
                    + ":" + id + ":" + now, Offer.Kind.ULTIMATUM,
                    me().getNationId(), null, 0, g.demand, null,
                    g.cause.words, now, Grievance.ULTIMATUM_TURNS);
                o.audience.add(id);
                ai.getOffers().add(o);
                return false;
            case ULTIMATUM:
                if (g.turnsLeft(now) > 0) return false;
                g.stage = Grievance.Stage.WAR;
                g.since = now;
                return true;
            case WAR: default:
                return true;
            }
        } finally {
            ((ServerGame)game()).sendToAll(cs);
        }
    }

    /**
     * A computer nation answers an ultimatum at once: it pays when it
     * can and fears us (our army is not weaker than its own), else it
     * refuses and the war comes.
     */
    private boolean computerAnswers(Grievance g, Player p, ChangeSet cs) {
        final boolean pays = p.getGold() >= g.demand
            && me().calculateStrength(false) >= p.calculateStrength(false);
        if (pays) {
            settle(g, p, cs);
            return false;
        }
        g.stage = Grievance.Stage.WAR;
        g.since = now();
        ai.getMemories().add(new Memory(Memory.Kind.REFUSED_OFFER,
                                        p.getNationId(), now(), 1));
        return true;
    }

    /**
     * The terms are met: the gold moves, the grievance is settled.
     *
     * @param g The {@code Grievance}.
     * @param p The nation that pays.
     * @param cs A {@code ChangeSet} to update.
     */
    public void settle(Grievance g, Player p, ChangeSet cs) {
        final int gold = Math.min(g.demand, Math.max(0, p.getGold()));
        p.modifyGold(-gold);
        me().modifyGold(gold);
        if (!p.isAI()) {
            cs.addPartial(ChangeSet.See.only(p), p, "gold",
                          String.valueOf(p.getGold()));
        }
        g.stage = Grievance.Stage.SETTLED;
        g.since = now();
        ai.getMemories().add(new Memory(Memory.Kind.DEAL_KEPT,
                                        p.getNationId(), now(), 1));
        if (g.cause == Grievance.Cause.DEBT) { // the claim is paid (mod)
            for (net.sf.freecol.common.diplomacy.Loan l
                     : LoanDesk.all(ai.getAIMain())) {
                if (l.borrower.equals(p.getNationId())
                    && l.lender.equals(me().getNationId())
                    && l.state == net.sf.freecol.common.diplomacy.Loan.State.SEIZED
                    && !l.over()) {
                    l.received += Math.min(gold, l.claim());
                    l.balance = Math.max(0, l.claim() - gold);
                    l.rentOwed = 0;
                    if (l.balance == 0) l.closed = now();
                }
            }
        }
        if (p.isAI()) {
            final AIPlayer pa = ai.getAIMain().getAIPlayer(p);
            if (pa instanceof EuropeanAIPlayer) {
                ((EuropeanAIPlayer)pa).getMemories().add(new Memory(
                    Memory.Kind.REFUSED_OFFER, me().getNationId(), now(), 2));
            }
        }
    }

    /**
     * A claim left from a ship loan (mod): we complain, then demand it;
     * the borrower (a computer nation) pays if it has the gold above
     * its reserve, else it is a reason for war like any grievance.
     *
     * @param p The borrower.
     * @param claim The gold still owed.
     * @param lb A {@code LogBuilder} to log to.
     */
    public void pressDebt(Player p, int claim, LogBuilder lb) {
        final Map<String, Grievance> book = ai.getGrievances();
        final int now = now();
        final String id = p.getNationId();
        Grievance g = book.get(id);
        if (g == null || g.stale(now)) {
            book.put(id, new Grievance(id, Grievance.Cause.DEBT, now));
            lb.add(" claims its loan from ", p.getDebugName());
            return;
        }
        if (g.cause != Grievance.Cause.DEBT
            || g.stage != Grievance.Stage.COMPLAINT
            || g.turnsLeft(now) > 0) return;
        g.stage = Grievance.Stage.ULTIMATUM;
        g.since = now;
        g.demand = claim;
        final AIPlayer pa = ai.getAIMain().getAIPlayer(p);
        final ChangeSet cs = new ChangeSet();
        if (pa instanceof EuropeanAIPlayer
            && ((EuropeanAIPlayer)pa).spendable() >= claim) {
            settle(g, p, cs);
        } else {
            g.stage = Grievance.Stage.WAR;
            ai.getMemories().add(new Memory(Memory.Kind.REFUSED_OFFER,
                                            id, now, 1));
        }
        ((ServerGame)game()).sendToAll(cs);
    }

    /**
     * The player refused the terms: war on our next turn.
     *
     * @param p The player.
     */
    public void refused(Player p) {
        final Grievance g = ai.getGrievances().get(p.getNationId());
        if (g == null || g.stage != Grievance.Stage.ULTIMATUM) return;
        g.since = now() - Grievance.ULTIMATUM_TURNS;
        ai.getMemories().add(new Memory(Memory.Kind.REFUSED_OFFER,
                                        p.getNationId(), now(), 1));
    }

    /**
     * Why we have a grievance, from what we can see: their soldiers
     * near our colonies, harm we remember, their colonies crowding
     * ours, else they stand in our way.
     *
     * @param p The other nation.
     * @return The {@code Grievance.Cause}.
     */
    public Grievance.Cause cause(Player p) {
        final Player me = me();
        for (Colony c : me.getColonyList()) {
            for (Tile t : c.getTile().getSurroundingTiles(SOLDIER_RADIUS)) {
                if (!me.canSee(t)) continue;
                for (Unit u : t.getUnitList()) {
                    if (u.getOwner() == p && !u.isNaval() && u.isOffensiveUnit()) {
                        return Grievance.Cause.SOLDIERS;
                    }
                }
            }
        }
        double harm = 0;
        for (Memory m : ai.getMemories().list()) {
            if (p.getNationId().equals(m.about)) {
                final double v = m.value(now());
                if (v < 0) harm += v;
            }
        }
        if (harm <= HARM_LINE) return Grievance.Cause.HARM;
        for (Colony c : me.getColonyList()) {
            for (Colony o : p.getColonyList()) {
                if (c.getTile().getDistanceTo(o.getTile()) <= CROWD_RADIUS) {
                    return Grievance.Cause.CROWDING;
                }
            }
        }
        return Grievance.Cause.RIVALRY;
    }

    /** A line in a human player's turn report. */
    private void tell(ChangeSet cs, Player to, String key, Grievance g) {
        if (to.isAI()) return;
        cs.addMessage(to, new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, key, me())
            .addStringTemplate("%nation%", me().getNationLabel())
            .addName("%cause%", g.cause.words)
            .addAmount("%turns%", Grievance.COMPLAINT_TURNS));
    }
}
