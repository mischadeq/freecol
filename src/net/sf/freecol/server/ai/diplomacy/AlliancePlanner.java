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

import java.util.List;

import net.sf.freecol.common.diplomacy.Action;
import net.sf.freecol.common.diplomacy.Diplomat;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.NationView;
import net.sf.freecol.common.diplomacy.Observed;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.diplomacy.Outlook;
import net.sf.freecol.common.diplomacy.Relations;
import net.sf.freecol.common.diplomacy.ScoredAction;
import net.sf.freecol.common.diplomacy.Snapshot;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.ai.EuropeanAIPlayer;


/**
 * Alliances that mean something (mod, step 5a): an alliance is a
 * defensive pact.  When war is declared on an ally (not by the King),
 * its allies are called to arms: a computer ally decides with its
 * diplomat, a human ally gets the call as an offer.  Refusing ends the
 * alliance and is remembered.  When a King's war sets two New World
 * allies against each other, a loyal ally keeps a local truce.
 */
public final class AlliancePlanner {

    private final EuropeanAIPlayer ai;


    public AlliancePlanner(EuropeanAIPlayer ai) {
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
     * This turn's work on alliances.
     *
     * @param outlook The diplomat's outlook.
     * @param lb A {@code LogBuilder} to log to.
     * @return True if a stance changed.
     */
    public boolean plan(Outlook outlook, LogBuilder lb) {
        final Player me = me();
        if (me.isREF() || !me.isEuropean()) return false;
        final ChangeSet cs = new ChangeSet();
        boolean changed = false;
        final List<Observed> d = ai.getLastDiff();
        final Snapshot before = ai.getLastBefore();
        for (Observed o : d) {
            if (o.what != Observed.What.WAR || o.other == null) continue;
            if (o.forced) {
                changed |= kingsWar(o, before, outlook, cs, lb);
            } else {
                changed |= callToArms(o, outlook, cs, lb);
            }
        }
        changed |= keepTruces(d, cs);
        ((net.sf.freecol.server.model.ServerGame)game()).sendToAll(cs);
        return changed;
    }


    // Calls to arms

    /**
     * War was declared by a nation of its own choice: if the victim is
     * our ally, answer its call; if we are the victim, call our human
     * allies (computer allies answer on their own turn).
     */
    private boolean callToArms(Observed o, Outlook outlook, ChangeSet cs,
                               LogBuilder lb) {
        final Player me = me();
        final Player attacker = game().getPlayerByNationId(o.actor);
        final Player victim = game().getPlayerByNationId(o.other);
        if (attacker == null || victim == null || attacker == me) return false;
        if (victim == me) {
            for (Player h : game().getLiveEuropeanPlayerList()) {
                if (h.isAI() || h.isREF() || h == attacker
                    || me.getStance(h) != Stance.ALLIANCE
                    || h.getStance(attacker) == Stance.WAR) continue;
                Offer c = new Offer("call:" + me.getNationId() + ":"
                    + attacker.getNationId() + ":" + now(),
                    Offer.Kind.CALL_TO_ARMS, me.getNationId(), null, 0, 0,
                    null, "", now(), 0);
                c.against = attacker.getNationId();
                c.audience.add(h.getNationId());
                ai.getOffers().add(c);
                lb.add("\n  Calls ", h.getDebugName(), " to arms against ",
                       attacker.getDebugName());
            }
            return false;
        }
        if (me.getStance(victim) != Stance.ALLIANCE
            || me.getStance(attacker) == Stance.WAR) return false;
        final NationView enemy = outlook.view.others.get(attacker.getNationId());
        if (enemy == null) return false; // never met: nothing we can do
        final ScoredAction sa = Diplomat.score(Action.joinWar(
            attacker.getNationId(), victim.getNationId()), outlook.situation);
        if (sa.total >= 0) {
            changeStance(Stance.WAR, attacker);
            ai.getCommitments().chose(attacker.getNationId(), sa, now());
            remember(victim, Memory.Kind.HELPED_IN_WAR, me);
            tell(cs, victim, "alliance.joins", attacker);
            lb.add("\n  Joins ", victim.getDebugName(), "'s war on ",
                   attacker.getDebugName(), " (", sa, ")");
        } else {
            changeStance(Stance.PEACE, victim);
            remember(victim, Memory.Kind.DEAL_BROKEN, me);
            tell(cs, victim, "alliance.refuses", attacker);
            lb.add("\n  Refuses ", victim.getDebugName(), "'s call (", sa, ")");
        }
        return true;
    }


    // The King's wars

    /**
     * A King's war: if it sets us against a New World ally, a loyal
     * ally keeps a local truce (and offers peace), a disloyal one does
     * not.
     */
    private boolean kingsWar(Observed o, Snapshot before, Outlook outlook,
                             ChangeSet cs, LogBuilder lb) {
        final Player me = me();
        final String partner = me.getNationId().equals(o.actor) ? o.other
            : me.getNationId().equals(o.other) ? o.actor : null;
        if (partner == null || before == null
            || before.stance(me.getNationId(), partner) != Stance.ALLIANCE) {
            return false;
        }
        final Player p = game().getPlayerByNationId(partner);
        // Loyalty from before the King's order: the tension then, and
        // the deals we remember (not the war itself).
        final double opinion = Relations.opinion(
            before.tension(me.getNationId(), partner),
            ai.getMemories().list(), partner, now());
        final double trust = Relations.trust(ai.getMemories().list(),
                                             partner, now());
        if (p == null || !loyal(trust, opinion)) {
            lb.add("\n  King's war on ally ", partner, ": not loyal");
            return false;
        }
        ai.getCommitments().truce(partner, now());
        tell(cs, p, "alliance.truce", p);
        lb.add("\n  King's war on ally ", partner, ": keeps a local truce");
        // Offer peace at once: to a computer ally that keeps the truce
        // too, it is made; a human ally gets peace terms.
        final AIPlayer other = (p.isAI()) ? ai.getAIMain().getAIPlayer(p) : null;
        if (other instanceof EuropeanAIPlayer) {
            if (((EuropeanAIPlayer)other).getCommitments().inTruce(me.getNationId())) {
                changeStance(Stance.PEACE, p);
                return true;
            }
        } else if (!p.isAI()) {
            Offer peace = new Offer("truce:" + me.getNationId() + ":"
                + partner + ":" + now(), Offer.Kind.PEACE, me.getNationId(),
                null, 0, 0, null, "", now(), 0);
            peace.audience.add(partner);
            ai.getOffers().add(peace);
        }
        return false;
    }

    /** Loyal: trusts the ally and does not dislike it. */
    public static boolean loyal(double trust, double opinion) {
        return trust >= Relations.DEFAULT_TRUST && opinion >= 0;
    }

    /**
     * Truces end with peace, or when the partner attacks us anyway
     * (then it is a betrayal).
     */
    private boolean keepTruces(List<Observed> d, ChangeSet cs) {
        final Player me = me();
        for (String t : ai.getCommitments().truces()) {
            final Player p = game().getPlayerByNationId(t);
            if (p == null || me.getStance(p) != Stance.WAR) {
                ai.getCommitments().endTruce(t);
                continue;
            }
            for (Observed o : d) {
                final boolean hit
                    = ((o.what == Observed.What.UNITS_LOST
                        || o.what == Observed.What.SHIPS_DAMAGED
                        || o.what == Observed.What.COLONY_LOST)
                       && me.getNationId().equals(o.actor) && t.equals(o.other))
                    || (o.what == Observed.What.CAPTURE && t.equals(o.actor)
                        && me.getNationId().equals(o.other));
                if (hit) {
                    ai.getCommitments().endTruce(t);
                    ai.getMemories().add(new Memory(Memory.Kind.DEAL_BROKEN,
                                                    t, now(), 1));
                    break;
                }
            }
        }
        return false;
    }


    // Helpers

    private void changeStance(Stance stance, Player other) {
        ai.getAIMain().getFreeColServer().getInGameController()
            .changeStance(me(), stance, other, true);
    }

    /** Another computer nation remembers something about us. */
    private void remember(Player who, Memory.Kind k, Player about) {
        if (!who.isAI()) return;
        final AIPlayer other = ai.getAIMain().getAIPlayer(who);
        if (other instanceof EuropeanAIPlayer) {
            ((EuropeanAIPlayer)other).getMemories().add(
                new Memory(k, about.getNationId(), now(), 1));
        }
    }

    /** A line in a human player's turn report. */
    private void tell(ChangeSet cs, Player to, String key, Player other) {
        if (to.isAI()) return;
        cs.addMessage(to, new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, key, me())
            .addStringTemplate("%nation%", me().getNationLabel())
            .addStringTemplate("%enemy%", other.getNationLabel()));
    }
}
