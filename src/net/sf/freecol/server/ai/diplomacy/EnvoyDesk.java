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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.Commitments;
import net.sf.freecol.common.diplomacy.DealValue;
import net.sf.freecol.common.diplomacy.EnvoyRules;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.NationView;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.diplomacy.Outlook;
import net.sf.freecol.common.diplomacy.Action;
import net.sf.freecol.common.diplomacy.Coalition;
import net.sf.freecol.common.diplomacy.ScoredAction;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeSet.See;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * The player's envoys to a computer nation (step 8, mod): sent in the
 * player's turn, kept in our offer book, answered on our turn (so the
 * answer reaches him at the start of his next turn): yes, no with a
 * reason, or our price ({@code Offer.Kind.TERMS}). The gold he sends
 * with an envoy is held until the answer: ours if we agree, back to
 * him otherwise.
 */
public final class EnvoyDesk {

    private static final Logger logger = Logger.getLogger(EnvoyDesk.class.getName());

    private final EuropeanAIPlayer ai;


    public EnvoyDesk(EuropeanAIPlayer ai) {
        this.ai = ai;
    }

    private Player me() {
        return ai.getPlayer();
    }

    private int now() {
        return ai.getGame().getTurn().getNumber();
    }


    // Sending (the player's turn)

    /**
     * The turn the player last sent an envoy to us, -1 never.
     *
     * @param ai The receiving {@code EuropeanAIPlayer}.
     * @param human The player's nation id.
     * @return The turn.
     */
    public static int lastSent(EuropeanAIPlayer ai, String human) {
        return lastSent(ai.getOffers(), human);
    }

    /**
     * The turn the player last sent an envoy, in an offer book.
     *
     * @param book The offer book.
     * @param human The player's nation id.
     * @return The turn, -1 never.
     */
    public static int lastSent(List<Offer> book, String human) {
        int last = -1;
        for (Offer o : book) {
            if (o.kind.envoy() && human.equals(o.from)) {
                last = Math.max(last, o.posted);
            }
        }
        return last;
    }

    /**
     * Does a proposal fit how things stand between us?
     *
     * @param kind The envoy kind.
     * @param stance Our stance towards the player.
     * @return True if so.
     */
    public static boolean fits(Offer.Kind kind, Stance stance) {
        switch (kind) {
        case ENVOY_PEACE:
            return stance == Stance.WAR || stance == Stance.CEASE_FIRE;
        case ENVOY_ALLIANCE:
            return stance == Stance.PEACE;
        case ENVOY_GIFT:
            return stance != Stance.UNCONTACTED;
        case ENVOY_WAR:
            return stance != Stance.WAR && stance != Stance.UNCONTACTED;
        case ENVOY_LEAVE:
            return stance == Stance.WAR || stance == Stance.CEASE_FIRE
                || stance == Stance.PEACE;
        default:
            return false;
        }
    }

    /**
     * Why a joint war or a buy-out cannot be (8.2), beyond the stance.
     *
     * @param kind The envoy kind.
     * @param h The player.
     * @param against The enemy (ENVOY_WAR), or null.
     * @return Null if it can be, else the reason.
     */
    EnvoyRules.Reason misfit(Offer.Kind kind, Player h, String against) {
        switch (kind) {
        case ENVOY_WAR: {
            final Player x = (against == null) ? null
                : ai.getGame().getPlayerByNationId(against);
            if (x == null || x.isDead() || x == me() || x == h
                || x.isREF() || h.getStance(x) != Stance.WAR
                || !me().hasContacted(x)) return EnvoyRules.Reason.NOT_NOW;
            if (me().getStance(x) == Stance.ALLIANCE) {
                return EnvoyRules.Reason.FRIENDS;
            }
            return null;
        }
        case ENVOY_LEAVE: {
            final Coalition c = AlliancePlanner.coalitionOf(ai,
                h.getNationId(), me().getNationId());
            if (c == null) return EnvoyRules.Reason.NO_COALITION;
            if (c.members.get(0).equals(me().getNationId())) {
                return EnvoyRules.Reason.LEADER;
            }
            return null;
        }
        default:
            return null;
        }
    }

    /**
     * The player sends an envoy to us.  His gold goes with it.
     *
     * @param human The player.
     * @param kind The envoy kind.
     * @param gold The gold he sends.
     * @param cs A {@code ChangeSet} to update.
     * @return Null if sent, else why not (a message key).
     */
    public String send(ServerPlayer human, Offer.Kind kind, int gold,
                       ChangeSet cs) {
        return send(human, kind, gold, null, cs);
    }

    /**
     * The player sends an envoy to us.  His gold goes with it.
     *
     * @param human The player.
     * @param kind The envoy kind.
     * @param gold The gold he sends.
     * @param against The enemy for {@code ENVOY_WAR}, else null.
     * @param cs A {@code ChangeSet} to update.
     * @return Null if sent, else why not (a message key).
     */
    public String send(ServerPlayer human, Offer.Kind kind, int gold,
                       String against, ChangeSet cs) {
        if (kind == null || !kind.envoy()) return "envoy.error.kind";
        if (me().isREF() || !me().isEuropean() || human.isAI()) {
            return "envoy.error.nation";
        }
        if (!me().hasContacted(human)) return "envoy.error.contact";
        if (EnvoyRules.waitLeft(lastSent(ai, human.getNationId()), now()) > 0) {
            return "envoy.error.wait";
        }
        if (!fits(kind, me().getStance(human))) return "envoy.error.fits";
        if (kind == Offer.Kind.ENVOY_WAR
            && misfit(kind, human, against) == EnvoyRules.Reason.NOT_NOW) {
            return "envoy.error.enemy";
        }
        if (kind == Offer.Kind.ENVOY_LEAVE) {
            final EnvoyRules.Reason r = misfit(kind, human, null);
            if (r == EnvoyRules.Reason.NO_COALITION) return "envoy.error.coalition";
            if (r == EnvoyRules.Reason.LEADER) return "envoy.error.leader";
        }
        if (gold < 0 || gold > human.getGold()
            || (kind == Offer.Kind.ENVOY_GIFT && gold <= 0)) {
            return "envoy.error.gold";
        }
        final Offer o = new Offer("envoy:" + human.getNationId() + ":"
            + me().getNationId() + ":" + now(), kind, human.getNationId(),
            null, 0, gold, null, "", now(), 0);
        o.against = (kind == Offer.Kind.ENVOY_WAR) ? against : null;
        o.audience.add(me().getNationId());
        ai.getOffers().add(o);
        gold(human, -gold, cs);
        logger.info(human.getName() + " sends an envoy to " + me().getName()
            + ": " + o);
        return null;
    }


    // Answering (our turn)

    /**
     * Answer every envoy waiting in our book.
     *
     * @param outlook Our diplomat's outlook this turn.
     * @param cs A {@code ChangeSet} to update.
     * @param lb A {@code LogBuilder} to log to.
     */
    public void answerAll(Outlook outlook, ChangeSet cs, LogBuilder lb) {
        for (Offer o : new ArrayList<>(ai.getOffers())) {
            if (!o.kind.envoy() || o.state != Offer.State.OPEN) continue;
            final Player h = ai.getGame().getPlayerByNationId(o.from);
            if (!(h instanceof ServerPlayer) || h.isDead()) {
                o.state = Offer.State.CANCELLED;
                continue;
            }
            answer(o, (ServerPlayer)h, outlook, cs, lb);
        }
    }

    /**
     * What goes into judging an envoy.
     *
     * @param o The envoy.
     * @param h The player.
     * @param outlook Our outlook.
     * @return The {@code EnvoyRules.Input}.
     */
    EnvoyRules.Input input(Offer o, Player h, Outlook outlook) {
        final EnvoyRules.Input in = new EnvoyRules.Input();
        in.kind = o.kind;
        in.gold = o.gold;
        final Stance stance = me().getStance(h);
        in.fits = fits(o.kind, stance);
        if (in.fits) {
            in.misfit = misfit(o.kind, h, o.against);
            in.fits = in.misfit == null;
        }
        final NationView v = outlook.view.others.get(h.getNationId());
        if (v != null) {
            in.trust = v.trust;
            in.opinion = v.opinion;
        }
        switch (o.kind) {
        case ENVOY_PEACE:
            in.value = DealValue.stance(outlook.ranked, h.getNationId(),
                                        Stance.PEACE, stance);
            break;
        case ENVOY_ALLIANCE:
            in.value = DealValue.stance(outlook.ranked, h.getNationId(),
                                        Stance.ALLIANCE, stance);
            break;
        case ENVOY_WAR:
            if (!in.fits) break;
            in.value = warValue(o.against, h, outlook);
            break;
        case ENVOY_LEAVE: {
            final Coalition co = AlliancePlanner.coalitionOf(ai,
                h.getNationId(), me().getNationId());
            in.value = (co == null) ? DealValue.NO_OPINION
                : -AlliancePlanner.buyoutPrice(ai, co, me());
            break;
        }
        default:
            in.value = 0;
            break;
        }
        final Commitments.Choice c = ai.getCommitments().get(h.getNationId());
        in.committed = c != null && c.kind == Action.Kind.DECLARE_WAR
            && now() - c.turn < Commitments.HOLD;
        return in;
    }

    /**
     * What joining the player's war on a nation is worth to us: our own
     * diplomat's score of that war, or, if it has none, a price that
     * falls the more we dislike the enemy.  Twice the price if we are
     * not his ally.
     *
     * @param against The enemy.
     * @param h The player.
     * @param outlook Our outlook.
     * @return The worth in gold.
     */
    int warValue(String against, Player h, Outlook outlook) {
        if (me().getStance(ai.getGame().getPlayerByNationId(against))
            == Stance.WAR) return 0; // fighting it already
        int v = DealValue.war(outlook.ranked, against);
        if (v == DealValue.NO_OPINION) {
            final NationView ev = outlook.view.others.get(against);
            v = EnvoyRules.warWithoutReason((ev == null) ? 0 : ev.opinion);
        }
        if (v < 0 && me().getStance(h) != Stance.ALLIANCE) v *= 2;
        return v;
    }

    private void answer(Offer o, ServerPlayer h, Outlook outlook,
                        ChangeSet cs, LogBuilder lb) {
        final EnvoyRules.Answer a = EnvoyRules.judge(input(o, h, outlook));
        lb.add("\n  Envoy from ", h.getName(), ": ", o, " -> ", a);
        logger.info(me().getName() + " answers the envoy of " + h.getName()
            + ": " + o + " -> " + a);
        switch (a.verdict) {
        case ACCEPT:
            o.state = Offer.State.DONE;
            o.paid = o.gold;
            gold(me(), o.gold, cs);
            carryOut(o.kind, h, o.gold, o.against, cs);
            break;
        case COUNTER:
            o.state = Offer.State.DECLINED;
            gold(h, o.gold, cs); // back to him
            final Offer t = new Offer("terms:" + me().getNationId() + ":"
                + h.getNationId() + ":" + now(), Offer.Kind.TERMS,
                me().getNationId(), o.kind.name(), 0, a.price, null, "",
                now(), 0);
            t.against = o.against;
            t.audience.add(h.getNationId());
            ai.getOffers().add(t); // told by OfferPlanner.tell
            message(cs, h, "envoy.counter." + o.kind.name(), a.price,
                    o.against);
            break;
        default:
            o.state = Offer.State.DECLINED;
            gold(h, o.gold, cs);
            message(cs, h, "envoy.refused." + a.reason.name(), o.gold,
                    o.against);
            break;
        }
    }

    /**
     * Carry out what an envoy asked, once agreed (and paid).
     *
     * @param kind The envoy kind.
     * @param h The player.
     * @param gold The gold paid.
     * @param cs A {@code ChangeSet} to update.
     */
    void carryOut(Offer.Kind kind, ServerPlayer h, int gold, String against,
                  ChangeSet cs) {
        final ServerPlayer me = (ServerPlayer)me();
        switch (kind) {
        case ENVOY_PEACE:
            if (me.getStance(h) == Stance.WAR
                || me.getStance(h) == Stance.CEASE_FIRE) {
                me.csChangeStance(Stance.PEACE, h, true, cs);
            }
            message(cs, h, "envoy.accepted.ENVOY_PEACE", gold);
            break;
        case ENVOY_ALLIANCE:
            if (me.getStance(h) == Stance.PEACE) {
                me.csChangeStance(Stance.ALLIANCE, h, true, cs);
            }
            message(cs, h, "envoy.accepted.ENVOY_ALLIANCE", gold);
            break;
        case ENVOY_GIFT:
            me.csModifyTension(h, -EnvoyRules.giftTension(gold), cs);
            ai.getMemories().add(new Memory(Memory.Kind.GIFT,
                                            h.getNationId(), now(), 1));
            message(cs, h, "envoy.accepted.ENVOY_GIFT", gold);
            break;
        case ENVOY_WAR: {
            final Player x = (against == null) ? null
                : ai.getGame().getPlayerByNationId(against);
            if (x == null || x.isDead()) break;
            if (me.getStance(x) != Stance.WAR) {
                me.csChangeStance(Stance.WAR, x, true, cs);
                // Hold the war like one of our own choosing.
                final ScoredAction sa = new ScoredAction(Action.war(against));
                sa.total = net.sf.freecol.common.diplomacy.Diplomat.THRESHOLD;
                ai.getCommitments().chose(against, sa, now());
            }
            message(cs, h, "envoy.accepted.ENVOY_WAR", gold, against);
            break;
        }
        case ENVOY_LEAVE: {
            final Coalition c = AlliancePlanner.coalitionOf(ai,
                h.getNationId(), me.getNationId());
            if (c != null) AlliancePlanner.of(ai, c).leave(c, me, cs);
            message(cs, h, "envoy.accepted.ENVOY_LEAVE", gold);
            break;
        }
        default:
            break;
        }
    }

    /**
     * The player answers our terms.
     *
     * @param o The {@code TERMS} offer.
     * @param h The player.
     * @param accept True if he takes them.
     * @param cs A {@code ChangeSet} to update.
     * @return True (the offer was handled).
     */
    public boolean answerTerms(Offer o, ServerPlayer h, boolean accept,
                               ChangeSet cs) {
        Offer.Kind kind;
        try {
            kind = Offer.Kind.valueOf(o.goods);
        } catch (IllegalArgumentException | NullPointerException e) {
            o.state = Offer.State.CANCELLED;
            return true;
        }
        if (!accept) {
            o.state = Offer.State.DECLINED;
            return true;
        }
        if (!fits(kind, me().getStance(h))
            || misfit(kind, h, o.against) != null) {
            o.state = Offer.State.EXPIRED;
            message(cs, h, "envoy.terms.over", o.gold);
            return true;
        }
        if (h.getGold() < o.gold) {
            o.state = Offer.State.DECLINED;
            message(cs, h, "envoy.terms.noGold", o.gold);
            return true;
        }
        o.state = Offer.State.DONE;
        o.takenBy = h.getNationId();
        o.takenTurn = now();
        o.paid = o.gold;
        gold(h, -o.gold, cs);
        gold(me(), o.gold, cs);
        carryOut(kind, h, o.gold, o.against, cs);
        return true;
    }


    // Helpers

    private static void gold(Player p, int amount, ChangeSet cs) {
        if (amount == 0) return;
        p.modifyGold(amount);
        cs.addPartial(See.only(p), p, "gold", String.valueOf(p.getGold()),
                      "score", String.valueOf(p.getScore()));
    }

    private void message(ChangeSet cs, Player to, String key, int gold) {
        message(cs, to, key, gold, null);
    }

    private void message(ChangeSet cs, Player to, String key, int gold,
                         String against) {
        final ModelMessage m = new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, key, me())
            .addStringTemplate("%nation%", me().getNationLabel())
            .addAmount("%gold%", gold);
        final Player x = (against == null) ? null
            : ai.getGame().getPlayerByNationId(against);
        if (x != null) m.addStringTemplate("%enemy%", x.getNationLabel());
        cs.addMessage(to, m);
    }

    /**
     * The player's envoy status with every computer nation he knows:
     * nation id, turns to wait, the turn the wait ends.
     *
     * @param human The player.
     * @param ais The computer nations.
     * @return Lines "nation:wait:readyTurn:coalition", coalition 0 when
     *     the nation is in no coalition against him, 1 a member, 2 the
     *     leader (8.2).
     */
    public static List<String> status(Player human,
        List<? extends net.sf.freecol.server.ai.AIPlayer> ais) {
        final List<String> ret = new ArrayList<>();
        for (net.sf.freecol.server.ai.AIPlayer a : ais) {
            final Player p = a.getPlayer();
            if (p.isREF() || !p.hasContacted(human)) continue;
            final List<Offer> book = (a instanceof EuropeanAIPlayer)
                ? ((EuropeanAIPlayer)a).getOffers()
                : (a instanceof net.sf.freecol.server.ai.NativeAIPlayer)
                ? ((net.sf.freecol.server.ai.NativeAIPlayer)a).getOffers()
                : null;
            if (book == null) continue;
            final int now = a.getGame().getTurn().getNumber();
            final int last = lastSent(book, human.getNationId());
            int co = 0;
            if (a instanceof EuropeanAIPlayer) {
                final Coalition c = AlliancePlanner.coalitionOf(
                    (EuropeanAIPlayer)a, human.getNationId(), p.getNationId());
                co = (c == null) ? 0
                    : (c.members.get(0).equals(p.getNationId())) ? 2 : 1;
            }
            ret.add(p.getNationId() + ":" + EnvoyRules.waitLeft(last, now)
                    + ":" + ((last < 0) ? -1 : last + EnvoyRules.WAIT)
                    + ":" + co);
        }
        return ret;
    }
}
