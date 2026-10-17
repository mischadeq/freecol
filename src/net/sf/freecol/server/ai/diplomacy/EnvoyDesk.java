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
        int last = -1;
        for (Offer o : ai.getOffers()) {
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
        default:
            return false;
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
        if (kind == null || !kind.envoy()) return "envoy.error.kind";
        if (me().isREF() || !me().isEuropean() || human.isAI()) {
            return "envoy.error.nation";
        }
        if (!me().hasContacted(human)) return "envoy.error.contact";
        if (EnvoyRules.waitLeft(lastSent(ai, human.getNationId()), now()) > 0) {
            return "envoy.error.wait";
        }
        if (!fits(kind, me().getStance(human))) return "envoy.error.fits";
        if (gold < 0 || gold > human.getGold()
            || (kind == Offer.Kind.ENVOY_GIFT && gold <= 0)) {
            return "envoy.error.gold";
        }
        final Offer o = new Offer("envoy:" + human.getNationId() + ":"
            + me().getNationId() + ":" + now(), kind, human.getNationId(),
            null, 0, gold, null, "", now(), 0);
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
        default:
            in.value = 0;
            break;
        }
        final Commitments.Choice c = ai.getCommitments().get(h.getNationId());
        in.committed = c != null && c.kind == Action.Kind.DECLARE_WAR
            && now() - c.turn < Commitments.HOLD;
        return in;
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
            carryOut(o.kind, h, o.gold, cs);
            break;
        case COUNTER:
            o.state = Offer.State.DECLINED;
            gold(h, o.gold, cs); // back to him
            final Offer t = new Offer("terms:" + me().getNationId() + ":"
                + h.getNationId() + ":" + now(), Offer.Kind.TERMS,
                me().getNationId(), o.kind.name(), 0, a.price, null, "",
                now(), 0);
            t.audience.add(h.getNationId());
            ai.getOffers().add(t); // told by OfferPlanner.tell
            message(cs, h, "envoy.counter." + o.kind.name(), a.price);
            break;
        default:
            o.state = Offer.State.DECLINED;
            gold(h, o.gold, cs);
            message(cs, h, "envoy.refused." + a.reason.name(), o.gold);
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
    void carryOut(Offer.Kind kind, ServerPlayer h, int gold, ChangeSet cs) {
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
        if (!fits(kind, me().getStance(h))) {
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
        carryOut(kind, h, o.gold, cs);
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
        cs.addMessage(to, new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, key, me())
            .addStringTemplate("%nation%", me().getNationLabel())
            .addAmount("%gold%", gold));
    }

    /**
     * The player's envoy status with every computer nation he knows:
     * nation id, turns to wait, the turn the wait ends.
     *
     * @param human The player.
     * @param ais The computer nations.
     * @return Lines "nation:wait:readyTurn".
     */
    public static List<String> status(Player human,
                                      List<EuropeanAIPlayer> ais) {
        final List<String> ret = new ArrayList<>();
        for (EuropeanAIPlayer a : ais) {
            final Player p = a.getPlayer();
            if (p.isREF() || !p.hasContacted(human)) continue;
            final int now = a.getGame().getTurn().getNumber();
            final int last = lastSent(a, human.getNationId());
            ret.add(p.getNationId() + ":" + EnvoyRules.waitLeft(last, now)
                    + ":" + ((last < 0) ? -1 : last + EnvoyRules.WAIT));
        }
        return ret;
    }
}
