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
import net.sf.freecol.common.diplomacy.Coalition;
import net.sf.freecol.common.diplomacy.Threat;
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
 * alliance and is remembered; a nation allied to both sides stays out.
 * When a King's war sets two New World allies against each other, a
 * loyal ally keeps a local truce.  An alliance with no reason left
 * lapses into peace.
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
        changed |= followJointWars(cs, lb);
        changed |= inviteToJointWar(outlook, cs, lb);
        changed |= runCoalitions(outlook, cs, lb);
        changed |= buyOut(outlook, cs, lb);
        inciteTribes(lb);
        changed |= startCoalition(outlook, cs, lb);
        changed |= lapse(outlook, cs, lb);
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
        if (me.getStance(attacker) == Stance.ALLIANCE) {
            // Allied to both: we stay out, and the alliance with the
            // one who started it ends (peace).  Nobody's word is broken.
            changeStance(Stance.PEACE, attacker);
            tell(cs, attacker, "alliance.staysOut", victim);
            tell(cs, victim, "alliance.staysOutOfYours", attacker);
            lb.add("\n  Allied to both ", attacker.getDebugName(), " and ",
                   victim.getDebugName(), ": stays out");
            return true;
        }
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


    // Alliances that lapse

    /**
     * An alliance is kept while it scores this (making one takes
     * {@link Diplomat#THRESHOLD}, so alliances do not flip-flop).
     */
    public static final double KEEP_ALLIANCE = Diplomat.SMALL_THRESHOLD;
    /** Turns a new alliance holds before it can lapse. */
    public static final int ALLIANCE_HOLD = 10;

    /**
     * Leave the alliances that have no reason left (score under
     * {@link #KEEP_ALLIANCE}): not a young one, not while we fight a
     * war side by side.  It ends in peace; a war on the former ally
     * soon after counts as betrayal (see {@code Memories}).
     */
    private boolean lapse(Outlook outlook, ChangeSet cs, LogBuilder lb) {
        final Player me = me();
        boolean changed = false;
        for (NationView v : outlook.view.others.values()) {
            if (!v.european || v.stance != Stance.ALLIANCE) continue;
            final Player p = game().getPlayerByNationId(v.id);
            if (p == null || p.isREF() || me.getStance(p) != Stance.ALLIANCE
                || fightTogether(me, p)) continue;
            final int made = allianceTurn(me, p);
            if (made >= 0 && now() - made < ALLIANCE_HOLD) continue;
            final ScoredAction sa = Diplomat.score(Action.alliance(v.id),
                                                   outlook.situation);
            if (sa.total >= KEEP_ALLIANCE) continue;
            changeStance(Stance.PEACE, p);
            tell(cs, p, "alliance.lapses", p);
            lb.add("\n  Alliance with ", p.getDebugName(), " lapses (", sa, ")");
            changed = true;
        }
        return changed;
    }

    /** The turn our alliance with a nation was made (-1 if unknown). */
    static int allianceTurn(Player me, Player p) {
        int turn = -1;
        for (net.sf.freecol.common.model.HistoryEvent h : me.getHistory()) {
            if (h.getEventType() == net.sf.freecol.common.model.HistoryEvent
                    .HistoryEventType.FORM_ALLIANCE
                && p.getId().equals(h.getPlayerId())) {
                turn = Math.max(turn, h.getTurn().getNumber());
            }
        }
        return turn;
    }

    /** Are two nations at war with the same enemy? */
    static boolean fightTogether(Player a, Player b) {
        for (Player q : a.getGame().getLivePlayerList()) {
            if (q != a && q != b && a.getStance(q) == Stance.WAR
                && b.getStance(q) == Stance.WAR) return true;
        }
        return false;
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


    // Joint wars (5b)

    /**
     * Ask a nation to join our war against an enemy, when the diplomat
     * says paying it is worth it (its INCITE move at 20 or more): gold
     * now, the colonies it takes are its own, and when we lack the gold
     * the colony we plan to take, if we take it.
     */
    private boolean inviteToJointWar(Outlook outlook, ChangeSet cs,
                                     LogBuilder lb) {
        final Player me = me();
        final int now = now();
        for (Offer o : ai.getOffers()) {
            if (o.kind == Offer.Kind.JOINT_WAR && me.getNationId().equals(o.from)
                && now - o.posted < OfferPlanner.GAP) return false;
        }
        for (ScoredAction sa : outlook.ranked) {
            if (sa.action.kind != Action.Kind.INCITE
                || sa.total < Diplomat.THRESHOLD) continue;
            final Player x = game().getPlayerByNationId(sa.action.target);
            final Player z = game().getPlayerByNationId(sa.action.against);
            if (x == null || z == null || x.isREF() || !x.isEuropean()
                || me.getStance(z) != Stance.WAR
                || x.getStance(z) == Stance.WAR
                || me.getStance(x) == Stance.WAR) continue;
            final int gold = Math.min(sa.action.price, Math.max(0, me.getGold()));
            String place = null, placeName = "";
            if (gold < sa.action.price) {
                final net.sf.freecol.common.diplomacy.Situation.Attack at
                    = outlook.situation.attacks.get(z.getNationId());
                if (at == null) continue; // neither gold nor a prize
                place = at.place.id;
                placeName = at.place.name;
            }
            Offer o = new Offer("joint:" + me.getNationId() + ":"
                + x.getNationId() + ":" + now, Offer.Kind.JOINT_WAR,
                me.getNationId(), null, 0, gold, place, placeName, now, 0);
            o.against = z.getNationId();
            o.audience.add(x.getNationId());
            ai.getOffers().add(o);
            lb.add("\n  Asks ", x.getDebugName(), " to join the war on ",
                   z.getDebugName(), " for ", gold, (place == null) ? ""
                   : " and " + placeName);
            if (x.isAI()) return computerAnswers(o, x, z, cs);
            return false; // the player answers in a dialog
        }
        return false;
    }

    /**
     * A computer nation weighs a joint-war offer with its own diplomat:
     * the war itself plus the gold (and the prize).
     */
    private boolean computerAnswers(Offer o, Player x, Player z, ChangeSet cs) {
        final AIPlayer xa = ai.getAIMain().getAIPlayer(x);
        if (!(xa instanceof EuropeanAIPlayer)) return false;
        final EuropeanAIPlayer xai = (EuropeanAIPlayer)xa;
        final Outlook xo = xai.getOutlook();
        if (!xo.view.others.containsKey(z.getNationId())) {
            o.state = Offer.State.DECLINED;
            return false;
        }
        final ScoredAction war = Diplomat.score(Action.war(z.getNationId()),
                                                xo.situation);
        final double worth = war.total + xo.view.character.gold * o.gold / 100.0
            + ((o.place == null) ? 0 : PRIZE_POINTS);
        if (worth < Diplomat.THRESHOLD) {
            o.state = Offer.State.DECLINED;
            ai.getMemories().add(new Memory(Memory.Kind.REFUSED_OFFER,
                                            x.getNationId(), now(), 1));
            return false;
        }
        accept(o, x, z, cs);
        xai.getCommitments().chose(z.getNationId(), war, now());
        return true;
    }

    /** What a promised colony is worth to the partner, in points. */
    public static final double PRIZE_POINTS = 15;

    /**
     * The partner accepts: it declares war, the gold moves, the offer
     * stays as the record of the joint war.
     *
     * @param o The offer.
     * @param x The partner.
     * @param z The enemy.
     * @param cs A {@code ChangeSet} to update.
     */
    public void accept(Offer o, Player x, Player z, ChangeSet cs) {
        ((net.sf.freecol.server.model.ServerPlayer)x)
            .csChangeStance(Stance.WAR, z, true, cs);
        final int gold = Math.min(o.gold, Math.max(0, me().getGold()));
        me().modifyGold(-gold);
        x.modifyGold(gold);
        cs.addPartial(ChangeSet.See.only(x), x, "gold", String.valueOf(x.getGold()));
        o.paid = gold;
        o.state = Offer.State.TAKEN;
        o.takenBy = x.getNationId();
        o.takenTurn = now();
        remember(x, Memory.Kind.GIFT, me());
    }

    /**
     * Joint wars running: hand over a promised colony once we took it;
     * a partner that makes peace alone broke its word; the joint war
     * ends with the war.
     */
    private boolean followJointWars(ChangeSet cs, LogBuilder lb) {
        final Player me = me();
        boolean changed = false;
        for (Offer o : ai.getOffers()) {
            if (o.kind != Offer.Kind.JOINT_WAR || o.state != Offer.State.TAKEN
                || !me.getNationId().equals(o.from)) continue;
            final Player x = game().getPlayerByNationId(o.takenBy);
            final Player z = game().getPlayerByNationId(o.against);
            if (x == null || z == null) {
                o.state = Offer.State.DONE;
                continue;
            }
            if (o.place != null) {
                final net.sf.freecol.common.model.Colony c = game()
                    .getFreeColGameObject(o.place, net.sf.freecol.common.model.Colony.class);
                if (c != null && c.getOwner() == me) {
                    ((net.sf.freecol.server.model.ServerColony)c)
                        .csChangeOwner(x, false, null, cs);
                    x.invalidateCanSeeTiles();
                    me.invalidateCanSeeTiles();
                    tell(cs, x, "alliance.spoils", z);
                    remember(x, Memory.Kind.DEAL_KEPT, me);
                    lb.add("\n  Hands ", c.getName(), " to ", x.getDebugName());
                    changed = true;
                }
            }
            final boolean weFight = me.getStance(z) == Stance.WAR;
            final boolean theyFight = x.getStance(z) == Stance.WAR;
            if (weFight && !theyFight) {
                ai.getMemories().add(new Memory(Memory.Kind.DEAL_BROKEN,
                                                x.getNationId(), now(), 1));
                o.state = Offer.State.FAILED;
            } else if (!weFight) {
                o.state = Offer.State.DONE;
            }
        }
        return changed;
    }

    /**
     * Are we bound not to make peace with a nation: a joint war we
     * asked a partner into (or joined), with the partner still
     * fighting?
     *
     * @param ai The computer player.
     * @param enemy The enemy nation id.
     * @return True if peace now would be a separate peace.
     */
    public static boolean boundToFight(EuropeanAIPlayer ai, String enemy) {
        final Player me = ai.getPlayer();
        for (Player p : ai.getGame().getLiveEuropeanPlayerList()) {
            final AIPlayer pa = ai.getAIMain().getAIPlayer(p);
            if (!(pa instanceof EuropeanAIPlayer)) continue;
            for (Offer o : ((EuropeanAIPlayer)pa).getOffers()) {
                if (o.kind != Offer.Kind.JOINT_WAR
                    || o.state != Offer.State.TAKEN
                    || !enemy.equals(o.against)) continue;
                final String partner = me.getNationId().equals(o.from)
                    ? o.takenBy : me.getNationId().equals(o.takenBy)
                    ? o.from : null;
                if (partner == null) continue;
                final Player q = ai.getGame().getPlayerByNationId(partner);
                final Player e = ai.getGame().getPlayerByNationId(enemy);
                if (q != null && e != null && q.getStance(e) == Stance.WAR) {
                    return true;
                }
            }
        }
        return false;
    }


    // Coalitions (5c)

    /** Turns before trying again to start a coalition against a nation. */
    public static final int RETRY_TURNS = 10;
    /** Bonus to a coalition's war on its target, in points. */
    public static final double COALITION_BONUS = 10;

    /** Every coalition in the game, founder first in each. */
    public static List<Coalition> allCoalitions(EuropeanAIPlayer ai) {
        List<Coalition> ret = new java.util.ArrayList<>();
        for (Player p : ai.getGame().getLiveEuropeanPlayerList()) {
            final AIPlayer pa = (p.isAI()) ? ai.getAIMain().getAIPlayer(p) : null;
            if (pa instanceof EuropeanAIPlayer) {
                ret.addAll(((EuropeanAIPlayer)pa).getCoalitions());
            }
        }
        return ret;
    }

    /**
     * Start a coalition against a runaway leader: balance keepers at
     * the line, others when it is well over.  Computer nations that see
     * the same leader and like us join; the player is invited.
     */
    private boolean startCoalition(Outlook outlook, ChangeSet cs,
                                   LogBuilder lb) {
        final Player me = me();
        final String leader = Threat.leaderToContain(outlook.view);
        if (leader == null) return false;
        final Player l = game().getPlayerByNationId(leader);
        if (l == null || l.isREF() || me.getStance(l) == Stance.ALLIANCE) {
            return false;
        }
        final double threat = Threat.all(outlook.view).getOrDefault(leader, 0.0);
        if (outlook.view.character != net.sf.freecol.common.diplomacy.NationCharacter.BALANCE_KEEPER
            && threat < outlook.view.coalitionLine + 0.05) return false;
        for (Coalition c : allCoalitions(ai)) {
            if (leader.equals(c.target)) return false; // one is enough
        }
        final Integer tried = ai.getCoalitionTries().get(leader);
        if (tried != null && now() - tried < RETRY_TURNS) return false;
        ai.getCoalitionTries().put(leader, now());
        final Coalition c = new Coalition(leader, now());
        c.members.add(me.getNationId());
        boolean changed = false;
        for (net.sf.freecol.common.diplomacy.NationView v : outlook.view.others.values()) {
            final Player p = game().getPlayerByNationId(v.id);
            if (p == null || p == l || !v.european || p.isREF() || v.atWar()
                || v.opinion <= net.sf.freecol.common.diplomacy.Mood.NEUTRAL
                || p.getStance(l) == Stance.ALLIANCE) continue;
            if (!p.isAI()) {
                Offer o = new Offer("coalition:" + me.getNationId() + ":"
                    + leader + ":" + p.getNationId() + ":" + now(),
                    Offer.Kind.COALITION, me.getNationId(), null, 0, 0,
                    null, "", now(), 0);
                o.against = leader;
                o.audience.add(p.getNationId());
                ai.getOffers().add(o);
                continue;
            }
            final AIPlayer pa = ai.getAIMain().getAIPlayer(p);
            if (!(pa instanceof EuropeanAIPlayer)) continue;
            final Outlook po = ((EuropeanAIPlayer)pa).getOutlook();
            final net.sf.freecol.common.diplomacy.NationView meToThem
                = po.view.others.get(me.getNationId());
            if (!leader.equals(Threat.leaderToContain(po.view))
                || meToThem == null
                || meToThem.opinion <= net.sf.freecol.common.diplomacy.Mood.NEUTRAL) {
                continue;
            }
            join(c, p, cs);
            changed = true;
        }
        boolean invited = false;
        for (Offer o : ai.getOffers()) {
            invited |= o.kind == Offer.Kind.COALITION && o.posted == now()
                && leader.equals(o.against);
        }
        if (c.members.size() < 2 && !invited) return false; // nobody to ask
        ai.getCoalitions().add(c);
        lb.add("\n  Coalition against ", leader, ": ", c.members);
        if (c.members.size() >= 2) announce(c, cs);
        return changed;
    }

    /**
     * A nation joins a coalition: allied with every member.
     *
     * @param c The {@code Coalition}.
     * @param p The joining {@code Player}.
     * @param cs A {@code ChangeSet} to update.
     */
    public void join(Coalition c, Player p, ChangeSet cs) {
        for (String m : c.members) {
            final Player q = game().getPlayerByNationId(m);
            if (q != null && q != p && q.getStance(p) != Stance.ALLIANCE) {
                ((net.sf.freecol.server.model.ServerPlayer)q)
                    .csChangeStance(Stance.ALLIANCE, p, true, cs);
            }
        }
        if (!c.members.contains(p.getNationId())) c.members.add(p.getNationId());
        if (c.members.size() == 2) announce(c, cs);
    }

    /** Tell a human target: a coalition is public. */
    private void announce(Coalition c, ChangeSet cs) {
        final Player t = game().getPlayerByNationId(c.target);
        if (t == null || t.isAI()) return;
        net.sf.freecol.common.model.StringTemplate names
            = net.sf.freecol.common.model.StringTemplate.label(", ");
        for (String m : c.members) {
            final Player q = game().getPlayerByNationId(m);
            if (q != null) names.addStringTemplate(q.getNationLabel());
        }
        cs.addMessage(t, new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, "coalition.formed", me())
            .addStringTemplate("%members%", names));
    }

    /**
     * Our coalitions: end them when the target is calm, else go to war
     * together when strong enough, and call the members.
     */
    private boolean runCoalitions(Outlook outlook, ChangeSet cs, LogBuilder lb) {
        final Player me = me();
        boolean changed = false;
        for (java.util.Iterator<Coalition> it = ai.getCoalitions().iterator();
             it.hasNext();) {
            final Coalition c = it.next();
            final Player t = game().getPlayerByNationId(c.target);
            c.members.removeIf(m -> {
                final Player q = game().getPlayerByNationId(m);
                return q == null || (q != me && (q.getStance(me) == Stance.WAR
                                                 || q.getStance(t) == Stance.ALLIANCE));
            });
            final double threat = (t == null) ? 0
                : Threat.all(outlook.view).getOrDefault(c.target, 0.0);
            if (t == null || c.over(threat, now())) {
                it.remove();
                lb.add("\n  Coalition against ", c.target, " ends");
                continue;
            }
            if (c.members.size() < 2) continue;
            final net.sf.freecol.common.diplomacy.NationView tv
                = outlook.view.others.get(c.target);
            if (tv == null) continue;
            if (me.getStance(t) != Stance.WAR) {
                double army = outlook.view.me.army;
                for (String m : c.members) {
                    final net.sf.freecol.common.diplomacy.NationView mv
                        = outlook.view.others.get(m);
                    if (mv != null) army += mv.army;
                }
                final ScoredAction war = Diplomat.score(Action.war(c.target),
                                                        outlook.situation);
                if (army >= Coalition.STRENGTH * tv.army
                    && war.total + COALITION_BONUS >= Diplomat.THRESHOLD) {
                    changeStance(Stance.WAR, t);
                    ai.getCommitments().chose(c.target, war, now());
                    lb.add("\n  Coalition war on ", c.target);
                    changed = true;
                } else {
                    continue;
                }
            }
            // At war with the target: the members fight too.
            for (String m : new java.util.ArrayList<>(c.members)) {
                final Player q = game().getPlayerByNationId(m);
                if (q == null || q == me || q.getStance(t) == Stance.WAR) continue;
                changed |= callMember(c, q, t, cs, lb);
            }
            planJointStrike(c, t, outlook, cs, lb);
        }
        return changed;
    }

    /** Call a coalition member to the war on the target. */
    private boolean callMember(Coalition c, Player q, Player t, ChangeSet cs,
                               LogBuilder lb) {
        final Player me = me();
        if (!q.isAI()) {
            for (Offer o : ai.getOffers()) {
                if (o.kind == Offer.Kind.CALL_TO_ARMS
                    && o.audience.contains(q.getNationId())
                    && c.target.equals(o.against)
                    && now() - o.posted < Offer.PEACE_TURNS) return false;
            }
            Offer o = new Offer("call:" + me.getNationId() + ":" + c.target
                + ":" + q.getNationId() + ":" + now(), Offer.Kind.CALL_TO_ARMS,
                me.getNationId(), null, 0, 0, null, "", now(), 0);
            o.against = c.target;
            o.audience.add(q.getNationId());
            ai.getOffers().add(o);
            return false;
        }
        final AIPlayer qa = ai.getAIMain().getAIPlayer(q);
        if (!(qa instanceof EuropeanAIPlayer)) return false;
        final EuropeanAIPlayer qai = (EuropeanAIPlayer)qa;
        final Outlook qo = qai.getOutlook();
        if (!qo.view.others.containsKey(c.target)) return false;
        final ScoredAction sa = Diplomat.score(Action.joinWar(c.target,
            me.getNationId()), qo.situation);
        if (sa.total + COALITION_BONUS >= 0) {
            ((net.sf.freecol.server.model.ServerPlayer)q)
                .csChangeStance(Stance.WAR, t, true, cs);
            qai.getCommitments().chose(c.target, sa, now());
            ai.getMemories().add(new Memory(Memory.Kind.HELPED_IN_WAR,
                                            q.getNationId(), now(), 1));
            lb.add("\n  ", q.getDebugName(), " joins the coalition war");
        } else {
            c.members.remove(q.getNationId());
            ((net.sf.freecol.server.model.ServerPlayer)me)
                .csChangeStance(Stance.PEACE, q, true, cs);
            ai.getMemories().add(new Memory(Memory.Kind.DEAL_BROKEN,
                                            q.getNationId(), now(), 1));
            lb.add("\n  ", q.getDebugName(), " leaves the coalition");
        }
        return true;
    }


    // Joint attacks

    /**
     * The members strike the same colony together: the one our diplomat
     * would attack (else the target's colony nearest to us), on the
     * turn the slowest member can be there.  Human members are told.
     */
    private void planJointStrike(Coalition c, Player t, Outlook outlook,
                                 ChangeSet cs, LogBuilder lb) {
        final int now = now();
        net.sf.freecol.common.model.Colony col = (c.jointTarget == null) ? null
            : game().getFreeColGameObject(c.jointTarget,
                net.sf.freecol.common.model.Colony.class);
        if (c.hasPlan(now) && col != null && col.getOwner() == t) return;
        c.clearPlan();
        final net.sf.freecol.common.diplomacy.Situation.Attack at
            = outlook.situation.attacks.get(c.target);
        col = (at == null) ? null : game().getFreeColGameObject(at.place.id,
            net.sf.freecol.common.model.Colony.class);
        if (col == null || col.getOwner() != t) col = nearestColony(t, me());
        if (col == null) return;
        final List<Player> fighting = new java.util.ArrayList<>();
        for (String m : c.members) {
            final Player q = game().getPlayerByNationId(m);
            if (q != null && q.getStance(t) == Stance.WAR) fighting.add(q);
        }
        if (fighting.size() < 2) return;
        final int[] travel = new int[fighting.size()];
        for (int i = 0; i < travel.length; i++) {
            travel[i] = travelTurns(fighting.get(i), col.getTile());
        }
        c.jointTarget = col.getId();
        c.jointName = col.getName();
        c.strikeTurn = Coalition.strikeTurn(now, travel);
        lb.add("\n  Joint strike on ", col.getName(), " in turn ", c.strikeTurn);
        for (Player q : fighting) {
            if (q.isAI()) continue;
            cs.addMessage(q, new ModelMessage(
                ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                "coalition.jointStrike", me())
                .addStringTemplate("%nation%", me().getNationLabel())
                .addStringTemplate("%enemy%", t.getNationLabel())
                .addName("%colony%", col.getName())
                .addAmount("%turns%", c.strikeTurn - now));
        }
    }

    /** A nation's colony nearest to another's colonies. */
    private static net.sf.freecol.common.model.Colony nearestColony(Player p,
                                                                     Player from) {
        net.sf.freecol.common.model.Colony best = null;
        int top = Integer.MAX_VALUE;
        for (net.sf.freecol.common.model.Colony c : p.getColonyList()) {
            for (net.sf.freecol.common.model.Colony f : from.getColonyList()) {
                final int d = c.getTile().getDistanceTo(f.getTile());
                if (d < top) {
                    top = d;
                    best = c;
                }
            }
            if (best == null) best = c;
        }
        return best;
    }

    /**
     * Turns a nation's army needs to reach a tile: by land a tile a
     * turn, by sea four tiles a turn and two turns to load and land.
     *
     * @param p The nation.
     * @param to The tile.
     * @return The turns (99 if it has no colony).
     */
    public static int travelTurns(Player p, net.sf.freecol.common.model.Tile to) {
        int best = 99;
        for (net.sf.freecol.common.model.Colony c : p.getColonyList()) {
            final int d = c.getTile().getDistanceTo(to);
            final int t = (c.getTile().getContiguity() == to.getContiguity())
                ? d : (d + 3) / 4 + 2;
            best = Math.min(best, t);
        }
        return best;
    }

    /**
     * The coalition with a joint plan against an enemy that a nation is
     * in, if any.
     *
     * @param ai The nation's computer player.
     * @param enemy The enemy nation id.
     * @return The {@code Coalition}, or null.
     */
    public static Coalition jointPlanFor(EuropeanAIPlayer ai, String enemy) {
        final int now = ai.getGame().getTurn().getNumber();
        for (Coalition c : allCoalitions(ai)) {
            if (c.target.equals(enemy) && c.hasPlan(now)
                && c.members.contains(ai.getPlayer().getNationId())) return c;
        }
        return null;
    }

    // Paying a tribe to fight a tribe (step 6)

    /** Share of our gold we spend on inciting a tribe at most. */
    public static final double INCITE_SHARE = 1.0 / 3;
    /** Turns between tries against the same tribe. */
    public static final int INCITE_GAP = 10;

    /**
     * At war with a tribe: pay another tribe (the cheapest: its old
     * rival first) to go to war with it.  Secret: only the tribes know.
     *
     * @param lb A {@code LogBuilder} to log to.
     * @return The tribe paid, or null.
     */
    public Player inciteTribes(LogBuilder lb) {
        final Player me = me();
        final net.sf.freecol.server.control.InGameController igc
            = ai.getAIMain().getFreeColServer().getInGameController();
        for (Player r : game().getLiveNativePlayerList()) {
            if (me.getStance(r) != Stance.WAR) continue;
            final String key = "incite:" + r.getNationId();
            final Integer tried = ai.getCoalitionTries().get(key);
            if (tried != null && now() - tried < INCITE_GAP) continue;
            ai.getCoalitionTries().put(key, now());
            Player best = null;
            int bestPrice = Integer.MAX_VALUE;
            for (Player t : game().getLiveNativePlayerList()) {
                if (t == r || t.getStance(r) == Stance.WAR
                    || me.getStance(t) == Stance.WAR
                    || me.getStance(t) == Stance.UNCONTACTED) continue;
                final int price = igc.tribeIncitePrice(t, r,
                    t.getTension(me).getValue());
                if (price < bestPrice) {
                    best = t;
                    bestPrice = price;
                }
            }
            if (best == null || bestPrice > INCITE_SHARE * me.getGold()) continue;
            me.modifyGold(-bestPrice);
            best.modifyGold(bestPrice);
            best.getTension(r).modify(net.sf.freecol.common.model.Tension.WAR_MODIFIER);
            r.getTension(me).modify(
                net.sf.freecol.common.model.Tension.TENSION_ADD_WAR_INCITER);
            lb.add("\n  Pays ", best.getDebugName(), " ", bestPrice,
                   " to fight ", r.getDebugName());
            return best;
        }
        return null;
    }

    // Buy-outs

    /** Share of our gold we spend on one buy-out at most. */
    public static final double BUYOUT_SHARE = 0.5;

    /**
     * A member's synergy with its coalition, as it sees it itself.
     *
     * @param member The member's computer player.
     * @param c The {@code Coalition}.
     * @return The synergy in points.
     */
    public static double synergy(EuropeanAIPlayer member, Coalition c) {
        final Outlook o = member.getOutlook();
        final String me = member.getPlayer().getNationId();
        double opinion = 0, trust = 0;
        int n = 0;
        for (String m : c.members) {
            if (m.equals(me)) continue;
            final NationView v = o.view.others.get(m);
            if (v == null) continue;
            opinion += v.opinion;
            trust += v.trust;
            n++;
        }
        if (n > 0) {
            opinion /= n;
            trust /= n;
        } else {
            trust = Relations.DEFAULT_TRUST;
        }
        final double threat = Threat.all(o.view).getOrDefault(c.target, 0.0);
        return Coalition.synergy(opinion, trust, threat,
            member.getGame().getTurn().getNumber() - c.since,
            sharedEnemies(member.getGame(), c, member.getPlayer()));
    }

    /**
     * A human member's synergy: nobody reads its mind, so it counts as
     * neutral, with the public part (the target's threat, how long,
     * shared wars).
     *
     * @param game The {@code Game}.
     * @param c The {@code Coalition}.
     * @param member The member.
     * @param threat The target's threat (as the target sees itself).
     * @return The synergy in points.
     */
    public static double publicSynergy(Game game, Coalition c, Player member,
                                       double threat) {
        return Coalition.synergy(0, Relations.DEFAULT_TRUST, threat,
            game.getTurn().getNumber() - c.since,
            sharedEnemies(game, c, member));
    }

    /** Nations (not the target) a member fights with another member. */
    private static int sharedEnemies(Game game, Coalition c, Player member) {
        int n = 0;
        for (Player e : game.getLiveEuropeanPlayerList()) {
            if (e == member || c.target.equals(e.getNationId())
                || member.getStance(e) != Stance.WAR) continue;
            for (String m : c.members) {
                final Player q = game.getPlayerByNationId(m);
                if (q != null && q != member && q.getStance(e) == Stance.WAR) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /**
     * The gold a member asks to leave a coalition: its own synergy for
     * a computer nation, the public part for a human.
     *
     * @param ai Any computer player (to reach the others).
     * @param c The {@code Coalition}.
     * @param member The member.
     * @return The price in gold.
     */
    public static int buyoutPrice(EuropeanAIPlayer ai, Coalition c,
                                  Player member) {
        final AIPlayer ma = (member.isAI())
            ? ai.getAIMain().getAIPlayer(member) : null;
        if (ma instanceof EuropeanAIPlayer) {
            return Coalition.price(synergy((EuropeanAIPlayer)ma, c));
        }
        final Player t = ai.getGame().getPlayerByNationId(c.target);
        final AIPlayer ta = (t == null || !t.isAI()) ? null
            : ai.getAIMain().getAIPlayer(t);
        final double threat = (ta instanceof EuropeanAIPlayer)
            ? Threat.all(((EuropeanAIPlayer)ta).getOutlook().view)
                .getOrDefault(c.target, 0.0)
            : Threat.COALITION_LINE;
        return Coalition.price(publicSynergy(ai.getGame(), c, member, threat));
    }

    /**
     * We are the target of a coalition: pay a member to leave, the
     * cheapest we can afford (not the founder), once every few turns.
     * A computer member takes the gold if it outweighs its synergy; a
     * human member gets the offer.
     */
    private boolean buyOut(Outlook outlook, ChangeSet cs, LogBuilder lb) {
        final Player me = me();
        final int now = now();
        for (Offer o : ai.getOffers()) {
            if (o.kind == Offer.Kind.BUYOUT && me.getNationId().equals(o.from)
                && now - o.posted < OfferPlanner.GAP) return false;
        }
        for (Coalition c : allCoalitions(ai)) {
            if (!me.getNationId().equals(c.target) || c.members.size() < 2) {
                continue;
            }
            Player best = null;
            int bestPrice = Integer.MAX_VALUE;
            for (String m : c.members.subList(1, c.members.size())) {
                final Player q = game().getPlayerByNationId(m);
                if (q == null || q.isREF()) continue;
                final int price = buyoutPrice(ai, c, q);
                if (price < bestPrice) {
                    best = q;
                    bestPrice = price;
                }
            }
            if (best == null || bestPrice > BUYOUT_SHARE * me.getGold()) continue;
            final Offer o = new Offer("buyout:" + me.getNationId() + ":"
                + best.getNationId() + ":" + now, Offer.Kind.BUYOUT,
                me.getNationId(), null, 0, bestPrice, null, "", now, 0);
            o.against = c.members.get(0);
            o.audience.add(best.getNationId());
            ai.getOffers().add(o);
            lb.add("\n  Offers ", best.getDebugName(), " ", bestPrice,
                   " gold to leave the coalition");
            if (!best.isAI()) return false; // the player answers
            final AIPlayer ba = ai.getAIMain().getAIPlayer(best);
            if (!(ba instanceof EuropeanAIPlayer)) return false;
            final EuropeanAIPlayer bai = (EuropeanAIPlayer)ba;
            if (Coalition.takes(bai.getOutlook().view.character.gold,
                                bestPrice, synergy(bai, c))) {
                o.state = Offer.State.DONE;
                o.takenBy = best.getNationId();
                o.takenTurn = now;
                o.paid = Math.min(bestPrice, Math.max(0, me.getGold()));
                me.modifyGold(-o.paid);
                best.modifyGold(o.paid);
                remember(best, Memory.Kind.GIFT, me);
                leave(c, best, cs);
                lb.add(": taken");
                return true;
            }
            o.state = Offer.State.DECLINED;
            ai.getMemories().add(new Memory(Memory.Kind.REFUSED_OFFER,
                                            best.getNationId(), now, 1));
            lb.add(": refused");
            return false;
        }
        return false;
    }

    /**
     * A member leaves a coalition, bought out: its alliances with the
     * members end, its war on the target too, and the members remember
     * that it broke its word.
     *
     * @param c The {@code Coalition}.
     * @param p The leaving {@code Player}.
     * @param cs A {@code ChangeSet} to update.
     */
    public void leave(Coalition c, Player p, ChangeSet cs) {
        if (!c.members.remove(p.getNationId())) return;
        final net.sf.freecol.server.model.ServerPlayer sp
            = (net.sf.freecol.server.model.ServerPlayer)p;
        final Player t = game().getPlayerByNationId(c.target);
        for (String m : c.members) {
            final Player q = game().getPlayerByNationId(m);
            if (q == null) continue;
            if (p.getStance(q) == Stance.ALLIANCE) {
                sp.csChangeStance(Stance.PEACE, q, true, cs);
            }
            remember(q, Memory.Kind.DEAL_BROKEN, p);
            if (!q.isAI()) {
                cs.addMessage(q, new ModelMessage(
                    ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                    "coalition.boughtOut", p)
                    .addStringTemplate("%nation%", p.getNationLabel())
                    .addStringTemplate("%enemy%", (t == null)
                        ? p.getNationLabel() : t.getNationLabel()));
            }
        }
        if (t != null && p.getStance(t) == Stance.WAR) {
            sp.csChangeStance(Stance.PEACE, t, true, cs);
        }
        if (t != null && !t.isAI()) {
            cs.addMessage(t, new ModelMessage(
                ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                "coalition.youBoughtOut", p)
                .addStringTemplate("%nation%", p.getNationLabel()));
        }
    }

    /**
     * Find the coalition against a target that a nation is in.
     *
     * @param ai Any computer player.
     * @param target The target nation id.
     * @param member The member nation id.
     * @return The {@code Coalition}, or null.
     */
    public static Coalition coalitionOf(EuropeanAIPlayer ai, String target,
                                        String member) {
        for (Coalition c : allCoalitions(ai)) {
            if (c.target.equals(target) && c.members.contains(member)) return c;
        }
        return null;
    }

    /**
     * The founder's planner, which owns the coalition (its offers and
     * memories go to the founder).
     *
     * @param ai Any computer player.
     * @param c The {@code Coalition}.
     * @return The founder's planner, or one for {@code ai}.
     */
    public static AlliancePlanner of(EuropeanAIPlayer ai, Coalition c) {
        final Player f = (c.members.isEmpty()) ? null
            : ai.getGame().getPlayerByNationId(c.members.get(0));
        final AIPlayer fa = (f == null || !f.isAI()) ? null
            : ai.getAIMain().getAIPlayer(f);
        return new AlliancePlanner((fa instanceof EuropeanAIPlayer)
            ? (EuropeanAIPlayer)fa : ai);
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
