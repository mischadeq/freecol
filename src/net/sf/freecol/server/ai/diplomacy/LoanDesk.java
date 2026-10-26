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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.freecol.common.diplomacy.CreditRecord;
import net.sf.freecol.common.diplomacy.Loan;
import net.sf.freecol.common.diplomacy.LoanRules;
import net.sf.freecol.common.diplomacy.Memory;
import net.sf.freecol.common.diplomacy.NationCharacter;
import net.sf.freecol.common.diplomacy.Offer;
import net.sf.freecol.common.diplomacy.Relations;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeSet.See;
import net.sf.freecol.common.networking.OfferMessage;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ai.AIMain;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.ai.EuropeanAIPlayer;
import net.sf.freecol.server.model.CreditRecords;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * Ship loans (mod), the borrower's side: a computer nation that lost
 * its ships and cannot pay for one asks a nation that can lend (the
 * player first of all, by a dialog), signs, pays back each turn before
 * shopping, pays off early when rich, and in default loses the ship.
 * Also the player's answers and the computer lenders.  The rules are
 * in {@link Loan}, {@link LoanRules} and {@link CreditRecord}.
 */
public final class LoanDesk {

    private static final Logger logger = Logger.getLogger(LoanDesk.class.getName());

    /** The "everyone said no" key in the asks map. */
    private static final String ALL = "*";

    private final EuropeanAIPlayer ai;


    public LoanDesk(EuropeanAIPlayer ai) {
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


    // Every loan in the game

    /**
     * Every loan of every computer nation (they are the borrowers).
     *
     * @param aiMain The {@code AIMain}.
     * @return The loans (live objects).
     */
    public static List<Loan> all(AIMain aiMain) {
        final List<Loan> ret = new ArrayList<>();
        for (Player p : aiMain.getGame().getLiveEuropeanPlayerList()) {
            final AIPlayer a = aiMain.getAIPlayer(p);
            if (a instanceof EuropeanAIPlayer) {
                ret.addAll(((EuropeanAIPlayer)a).getLoans());
            }
        }
        return ret;
    }

    /** Loans a nation has lent and that are still being paid. */
    public static int running(AIMain aiMain, String lender) {
        int n = 0;
        for (Loan l : all(aiMain)) {
            if (lender.equals(l.lender) && l.state.running()) n++;
        }
        return n;
    }

    /** A loan by id, or null. */
    public static Loan find(AIMain aiMain, String id) {
        for (Loan l : all(aiMain)) if (l.id.equals(id)) return l;
        return null;
    }


    // The borrower's turn

    /**
     * This turn's work: payments, defaults, seizures, and a request if
     * we need a ship.  Before shopping, so what is due is paid first.
     *
     * @param lb A {@code LogBuilder} to log to.
     */
    public void plan(LogBuilder lb) {
        final Player me = me();
        if (me.isREF() || !me.isEuropean() || me.isDead()) return;
        final ChangeSet cs = new ChangeSet();
        final int now = now();
        voidDead();
        for (Iterator<Loan> it = ai.getLoans().iterator(); it.hasNext();) {
            final Loan l = it.next();
            if (l.over() && now - Math.max(l.closed, l.signed) > LoanRules.KEEP) {
                it.remove();
            }
        }
        // A request the player left unanswered counts as a no.
        for (Offer o : ai.getOffers()) {
            if (o.kind == Offer.Kind.LOAN && o.state == Offer.State.OPEN
                && o.stale(now)) {
                o.state = Offer.State.EXPIRED;
                refused(o.audience.get(0));
            }
        }
        for (Loan l : new ArrayList<>(ai.getLoans())) {
            if (!l.over()) follow(l, cs, lb);
        }
        CreditRecords.of(me).inDefault = inDefault();
        ask(cs, lb);
        ((ServerGame)game()).sendToAll(cs);
    }

    /** A borrower that is gone: its loans are void. */
    private void voidDead() {
        for (Player p : game().getPlayers(x -> x.isEuropean() && x.isDead())
                 .collect(java.util.stream.Collectors.toList())) {
            final AIPlayer a = ai.getAIMain().getAIPlayer(p);
            if (!(a instanceof EuropeanAIPlayer)) continue;
            for (Loan l : ((EuropeanAIPlayer)a).getLoans()) {
                if (!l.over()) l.end(Loan.State.VOID, now());
            }
        }
    }

    /** One of our loans, this turn. */
    private void follow(Loan l, ChangeSet cs, LogBuilder lb) {
        final int now = now();
        final Player lender = game().getPlayerByNationId(l.lender);
        if (lender == null || lender.isDead()) {
            l.end(Loan.State.VOID, now);
            return;
        }
        final boolean war = me().getStance(lender) == Stance.WAR;
        if (war && l.state == Loan.State.ACTIVE) {
            l.freeze();
            message(cs, lender, "loan.frozen", l, l.balance);
        } else if (!war && l.state == Loan.State.FROZEN) {
            if (peaceCancels(l, lender)) {
                cancel(l, lender, cs);
                return;
            }
            l.thaw();
        }
        switch (l.state) {
        case SEIZURE_PENDING:
            trySeize(l, lender, cs);
            return;
        case SEIZED:
            // A claim left: paid when we are rich, else the lender
            // (a computer) presses it.
            if (l.claim() > 0 && !war) payClaim(l, lender, cs);
            if (l.claim() > 0 && lender.isAI()) pressClaim(l, lender, lb);
            return;
        case DEFAULT:
            if (!lender.isAI() && l.unanswered(now)) {
                l.waitFor(now);
                message(cs, lender, "loan.waiting", l, l.claim());
            } else if (lender.isAI()) {
                l.state = Loan.State.SEIZURE_PENDING;
                trySeize(l, lender, cs);
                return;
            }
            break;
        case WAITING:
            if (l.waitOver(now)) {
                l.state = Loan.State.DEFAULT;
                askLender(l, lender, cs);
            }
            break;
        default:
            break;
        }
        if (!l.collectible(war)) return;
        // Gold above the reserve, not counting this payment's own promise.
        final int spendable = net.sf.freecol.common.diplomacy.Treasury
            .spendable(me().getGold(), ai.reserve() - l.due());
        if (l.paysOffEarly(spendable)) {
            final int gold = l.payOff(now);
            transfer(lender, gold, cs);
            final CreditRecord r = CreditRecords.of(me());
            r.early++;
            remember(lender, Memory.Kind.LOAN_PAID, me(), 8);
            everyone(Memory.Kind.GOOD_PAYER, lender);
            message(cs, lender, "loan.paidEarly", l, gold);
            lb.add("\n  Loan paid off early: ", l);
            return;
        }
        final int due = l.due();
        if (spendable >= due && me().getGold() >= due) {
            final boolean cure = l.state == Loan.State.WAITING;
            l.pay(now);
            transfer(lender, due, cs);
            remember(lender, Memory.Kind.LOAN_PAID, me(), 1);
            if (l.state == Loan.State.PAID) {
                CreditRecords.of(me()).repaid++;
                remember(lender, Memory.Kind.LOAN_PAID, me(), 5);
                everyone(Memory.Kind.GOOD_PAYER, lender);
                message(cs, lender, "loan.paid", l, due);
            } else if (cure && l.cured()) {
                l.state = Loan.State.ACTIVE;
                l.asked = -1;
                CreditRecords.of(me()).mark(CreditRecord.Mark.CURED, l.lender);
                message(cs, lender, "loan.cured", l, due);
            } else {
                message(cs, lender, "loan.installment", l, due);
            }
            lb.add("\n  Loan payment ", due, ": ", l);
            return;
        }
        if (l.miss()) {
            defaulted(l, lender, cs);
        } else {
            message(cs, lender, "loan.missed", l, due);
        }
        lb.add("\n  Loan payment missed: ", l);
    }

    /** Gold from us to the lender. */
    private void transfer(Player lender, int gold, ChangeSet cs) {
        gold(me(), -gold, cs);
        gold(lender, gold, cs);
    }

    /** Is one of our loans in default now? */
    private boolean inDefault() {
        for (Loan l : ai.getLoans()) {
            if (!l.over() && (l.state.inDefault()
                              || (l.state == Loan.State.SEIZED && l.claim() > 0))) {
                return true;
            }
        }
        return false;
    }

    /** Three payments missed: on the record, and the lender decides. */
    private void defaulted(Loan l, Player lender, ChangeSet cs) {
        CreditRecords.of(me()).defaulted(now(), l.lender);
        remember(lender, Memory.Kind.LOAN_DEFAULT, me(), 1);
        everyone(Memory.Kind.BAD_PAYER, lender);
        if (lender.isAI()) { // no mercy this round: the ship
            l.state = Loan.State.SEIZURE_PENDING;
            trySeize(l, lender, cs);
        } else {
            askLender(l, lender, cs);
        }
    }

    /** Ask the player what to do about our default: a dialog. */
    private void askLender(Loan l, Player lender, ChangeSet cs) {
        final int now = now();
        l.asked = now;
        final Unit ship = ship(l);
        final Offer o = new Offer("loandefault:" + l.id + ":" + now,
            Offer.Kind.LOAN_DEFAULT, me().getNationId(), l.shipType,
            l.claim(), shipWorth(l, ship), l.id, where(ship), now, 0);
        o.delivered = l.missed;
        o.audience.add(lender.getNationId());
        o.told.add(lender.getNationId());
        ai.getOffers().add(o);
        cs.add(See.only(lender), new OfferMessage(o));
        message(cs, lender, "loan.default", l, l.claim());
    }

    /** Where the ship is, in words for the dialog. */
    private static String where(Unit ship) {
        if (ship == null || ship.isDisposed()) return "-";
        if (ship.isInEurope()) return "Europe";
        if (ship.getTile() != null) {
            return (ship.getTile().getSettlement() != null)
                ? ship.getTile().getSettlement().getName()
                : "(" + ship.getTile().getX() + ", " + ship.getTile().getY() + ")";
        }
        return "at sea";
    }

    /** The collateral ship, if it is still ours. */
    private Unit ship(Loan l) {
        final Unit u = (l.shipId == null) ? null
            : game().getFreeColGameObject(l.shipId, Unit.class);
        return (u == null || u.isDisposed() || u.getOwner() != me()) ? null : u;
    }

    /** What the ship is worth off the debt now. */
    private int shipWorth(Loan l, Unit ship) {
        if (ship == null) return 0;
        final int price = me().getEuropeanPurchasePrice(new AbstractUnit(
            ship.getType(), Specification.DEFAULT_ROLE_ID, 1));
        return (price <= 0 || price == Integer.MAX_VALUE) ? 0
            : Loan.seizeValue(price, ship.isDamaged());
    }

    /**
     * The ship goes to the lender the first turn it is docked in
     * Europe; passengers stay on our dock, goods go with it.  Gone (sunk,
     * captured): the whole debt stays a claim.
     */
    void trySeize(Loan l, Player lender, ChangeSet cs) {
        final Unit ship = ship(l);
        final int now = now();
        if (ship == null) {
            l.seize(0, now);
            message(cs, lender, "loan.shipGone", l, l.claim());
            return;
        }
        if (!ship.isInEurope() || lender.getEurope() == null) return;
        final Europe europe = me().getEurope();
        for (Unit u : new ArrayList<>(ship.getUnitList())) {
            u.setLocation(europe);
        }
        final int worth = shipWorth(l, ship);
        if (!((ServerPlayer)me()).csChangeOwner(ship, lender, null,
                                                lender.getEurope(), cs)) {
            l.seize(0, now);
            message(cs, lender, "loan.shipGone", l, l.claim());
            return;
        }
        ship.setMovesLeft(0);
        cs.add(See.only(lender), (FreeColGameObject)lender.getEurope());
        l.seize(worth, now);
        CreditRecords.of(me()).seized++;
        // Hard but fair, unless we are proud.
        ai.getMemories().add(new Memory(Memory.Kind.LOAN_SEIZED,
            lender.getNationId(), now,
            (ai.character() == NationCharacter.CONQUEROR) ? 2 : 1));
        message(cs, lender, "loan.seized", l, worth);
    }

    /** A claim left after a seizure, paid in full when we are rich. */
    private void payClaim(Loan l, Player lender, ChangeSet cs) {
        final int claim = l.claim();
        if (ai.spendable() < claim + Loan.CUSHION) return;
        transfer(lender, claim, cs);
        l.balance = 0;
        l.rentOwed = 0;
        l.closed = now();
        message(cs, lender, "loan.claimPaid", l, claim);
    }

    /** A computer lender presses its claim as a grievance. */
    private void pressClaim(Loan l, Player lender, LogBuilder lb) {
        final AIPlayer la = ai.getAIMain().getAIPlayer(lender);
        if (la instanceof EuropeanAIPlayer) {
            new GrievancePlanner((EuropeanAIPlayer)la).pressDebt(me(),
                l.claim(), lb);
        }
    }

    /**
     * Peace again between borrower and lender (computer nations): the
     * lender buys peace with the debt if it was losing; a proud borrower
     * that won has it cancelled.  Else the payments go on.
     */
    private boolean peaceCancels(Loan l, Player lender) {
        if (!lender.isAI()) return false; // the player's peace terms decide
        final AIPlayer la = ai.getAIMain().getAIPlayer(lender);
        if (la instanceof EuropeanAIPlayer
            && ((EuropeanAIPlayer)la).losingWar()) return true;
        return insistsOnCancel(lender);
    }

    /**
     * Do we, the borrower, want the debt cancelled to make peace: a
     * ruthless or proud nation that won the war (the stronger side, not
     * losing).
     *
     * @param lender The lender.
     * @return True if so.
     */
    public boolean insistsOnCancel(Player lender) {
        return ai.character() == NationCharacter.CONQUEROR && !ai.losingWar()
            && me().calculateStrength(false) > lender.calculateStrength(false);
    }

    /** The debt to a lender we would ask to cancel in peace terms, or 0. */
    public int debtToCancel(Player lender) {
        if (!insistsOnCancel(lender)) return 0;
        for (Loan l : ai.getLoans()) {
            if (!l.over() && l.lender.equals(lender.getNationId())
                && l.state == Loan.State.FROZEN) return l.claim();
        }
        return 0;
    }

    /** The debt is cancelled in a peace deal. */
    private void cancel(Loan l, Player lender, ChangeSet cs) {
        final int gone = l.claim();
        l.end(Loan.State.CANCELLED, now());
        message(cs, lender, "loan.cancelled", l, gone);
    }

    /**
     * Peace with the player on our terms, the debt cancelled.
     *
     * @param lender The player.
     * @param cs A {@code ChangeSet} to update.
     */
    public void cancelForPeace(Player lender, ChangeSet cs) {
        for (Loan l : ai.getLoans()) {
            if (!l.over() && l.lender.equals(lender.getNationId())) {
                cancel(l, lender, cs);
            }
        }
    }


    // Asking for a loan

    /** The cheapest transport we may buy in Europe, or null. */
    private UnitType cheapestShip() {
        UnitType best = null;
        int bestPrice = Integer.MAX_VALUE;
        for (UnitType ut : game().getSpecification().getUnitTypeList()) {
            if (!ut.hasAbility(Ability.NAVAL_UNIT) || ut.getSpace() <= 0
                || !ut.isAvailableTo(me()) || !ut.hasPrice()) continue;
            final int p = price(ut);
            if (p > 0 && p < bestPrice) {
                best = ut;
                bestPrice = p;
            }
        }
        return best;
    }

    private int price(UnitType ut) {
        final int p = me().getEuropeanPurchasePrice(new AbstractUnit(ut,
            Specification.DEFAULT_ROLE_ID, 1));
        return (p <= 0 || p == Integer.MAX_VALUE) ? -1 : p;
    }

    /** Units waiting on our dock in Europe. */
    private int waiting() {
        final Europe e = me().getEurope();
        return (e == null) ? 0
            : (int)e.getUnits().filter(u -> !u.isNaval()).count();
    }

    /** No transport ship that can sail. */
    private boolean noTransport() {
        for (Unit u : me().getUnitSet()) {
            if (u.isNaval() && u.getType().getSpace() > 0 && !u.isDamaged()) {
                return false;
            }
        }
        return true;
    }

    /** No ship at all. */
    private boolean noShips() {
        for (Unit u : me().getUnitSet()) if (u.isNaval()) return false;
        return true;
    }

    /** No ship, a colony or a load waiting: the ceiling goes up. */
    private boolean desperate() {
        return noShips() && (!me().getColonyList().isEmpty() || waiting() > 0);
    }

    /**
     * Do we need a loan now (before looking at lenders)?
     *
     * @return The ship type to buy, or null if not.
     */
    public UnitType needsLoan() {
        final Player me = me();
        if (me.getPlayerType() != Player.PlayerType.COLONIAL
            || me.getEurope() == null) return null;
        if (me.getColonyList().isEmpty() && waiting() == 0) return null;
        if (!noTransport()) return null;
        final int now = now();
        for (Loan l : ai.getLoans()) {
            if (!l.over()) return null;
            if (now - l.closed < LoanRules.COOLDOWN) return null;
        }
        final UnitType ut = cheapestShip();
        if (ut == null) return null;
        return (LoanRules.principal(price(ut), ai.spendable()) > 0) ? ut : null;
    }

    /** A lender said no: not asked again for a while. */
    private void refused(String lender) {
        ai.getLoanAsks().put(lender, now());
    }

    /** The nations we could ask, as we see them. */
    private List<LoanRules.Lender> lenders() {
        final Player me = me();
        final AIMain aiMain = ai.getAIMain();
        final int now = now();
        final CreditRecord credit = CreditRecords.of(me);
        final List<LoanRules.Lender> ret = new ArrayList<>();
        for (Player p : game().getLiveEuropeanPlayerList()) {
            if (p == me) continue;
            final Integer no = ai.getLoanAsks().get(p.getNationId());
            if (no != null && now - no < LoanRules.REFUSAL_TURNS) continue;
            final AIPlayer pa = aiMain.getAIPlayer(p);
            final boolean human = !p.isAI();
            if (!human && !(pa instanceof EuropeanAIPlayer)) continue;
            final LoanRules.Lender l = new LoanRules.Lender(p.getNationId(), human);
            l.contact = me.hasContacted(p);
            l.atWar = me.getStance(p) == Stance.WAR;
            l.ref = p.isREF();
            l.dead = p.isDead();
            l.running = running(aiMain, p.getNationId());
            l.blacklisted = credit.burned.contains(p.getNationId());
            l.liked = Relations.opinion(me.getTension(p).getValue(),
                ai.getMemories().list(), p.getNationId(), now);
            if (human) {
                l.gold = p.getGold(); // public (Foreign Affairs report)
            } else {
                final EuropeanAIPlayer la = (EuropeanAIPlayer)pa;
                l.gold = la.spendable();
                l.opinion = Relations.opinion(p.getTension(me).getValue(),
                    la.getMemories().list(), me.getNationId(), now);
                l.character = la.character();
                l.rival = rivals(la, me);
            }
            ret.add(l);
        }
        return ret;
    }

    /**
     * Is the borrower the lender's rival: a coalition of the lender's
     * against it, or at war with the lender's ally?
     */
    private boolean rivals(EuropeanAIPlayer lender, Player borrower) {
        for (net.sf.freecol.common.diplomacy.Coalition c
                 : AlliancePlanner.allCoalitions(lender)) {
            if (c.target.equals(borrower.getNationId())
                && c.members.contains(lender.getPlayer().getNationId())) {
                return true;
            }
        }
        for (Player p : game().getLiveEuropeanPlayerList()) {
            if (lender.getPlayer().getStance(p) == Stance.ALLIANCE
                && borrower.getStance(p) == Stance.WAR) return true;
        }
        return false;
    }

    /** Our gold, fleet and credit in a line for the player's dialog. */
    private String info() {
        final CreditRecord r = CreditRecords.of(me());
        final int now = now();
        final List<CreditRecord.Default> ds = r.recentDefaults(now);
        int ships = 0;
        for (Unit u : me().getUnitSet()) if (u.isNaval()) ships++;
        return "gold=" + me().getGold() + " ships=" + ships
            + " standing=" + r.standing(now).name() + " repaid=" + r.repaid
            + " early=" + r.early + " defaults=" + ds.size()
            + " last=" + ((ds.isEmpty()) ? -1
                          : now - ds.get(ds.size() - 1).turn)
            + " seized=" + r.seized
            + " desperate=" + ((desperate()) ? 1 : 0);
    }

    /** Ask for a loan if we need one: the best candidate first. */
    private void ask(ChangeSet cs, LogBuilder lb) {
        final int now = now();
        final Integer wait = ai.getLoanAsks().get(ALL);
        if (wait != null && now < wait) return;
        for (Offer o : ai.getOffers()) {
            if (o.kind == Offer.Kind.LOAN && o.state == Offer.State.OPEN) {
                return; // the player has not answered yet
            }
        }
        final UnitType ut = needsLoan();
        if (ut == null) return;
        final int price = price(ut);
        final int principal = LoanRules.principal(price, ai.spendable());
        final CreditRecord.Standing standing = CreditRecords.standing(me());
        final List<LoanRules.Lender> cands = LoanRules.candidates(lenders(),
            principal, standing, false);
        if (cands.isEmpty()) {
            // Everyone we asked lately said no: wait a while.
            boolean asked = false;
            for (Map.Entry<String, Integer> e : ai.getLoanAsks().entrySet()) {
                asked |= !ALL.equals(e.getKey())
                    && now - e.getValue() < LoanRules.REFUSAL_TURNS;
            }
            if (asked) ai.getLoanAsks().put(ALL, now + LoanRules.REFUSAL_TURNS);
            return;
        }
        for (LoanRules.Lender c : cands) {
            final Player p = game().getPlayerByNationId(c.id);
            if (c.human) {
                final Offer o = new Offer("loan:" + me().getNationId() + ":"
                    + c.id + ":" + now, Offer.Kind.LOAN, me().getNationId(),
                    ut.getId(), principal, price, null, info(), now,
                    Loan.DEFAULT_TERM);
                o.delivered = Loan.DEFAULT_RENT;
                o.audience.add(c.id);
                o.told.add(c.id);
                ai.getOffers().add(o);
                cs.add(See.only(p), new OfferMessage(o));
                message(cs, p, "loan.request", null, principal);
                lb.add("\n  Asks ", p.getDebugName(), " for a loan of ",
                       principal);
                return;
            }
            // A computer lender answers at once, at its own rent.
            final EuropeanAIPlayer la = (EuropeanAIPlayer)ai.getAIMain()
                .getAIPlayer(p);
            final int rent = LoanRules.lenderRent(la.character(), standing);
            final int ceiling = LoanRules.ceiling(ai.character(), desperate(),
                                                  standing, false);
            if (rent > ceiling) {
                refused(c.id);
                lb.add("\n  Loan from ", p.getDebugName(), " too dear: ", rent);
                continue;
            }
            if (sign(p, principal, rent, Loan.DEFAULT_TERM, ut, cs) != null) {
                lb.add("\n  Borrows ", principal, " from ", p.getDebugName());
                return;
            }
            refused(c.id);
        }
    }

    /**
     * Sign a loan: the lender's gold to us, and the ship bought in our
     * Europe at once.  All or nothing.
     *
     * @return The new {@code Loan}, or null if it fell through.
     */
    Loan sign(Player lender, int principal, int rent, int term, UnitType ut,
              ChangeSet cs) {
        final Player me = me();
        final int now = now();
        final int price = price(ut);
        final int lenderGold = (lender.isAI())
            ? ((EuropeanAIPlayer)ai.getAIMain().getAIPlayer(lender)).spendable()
            : lender.getGold();
        if (price <= 0 || lenderGold < principal || me.getEurope() == null) {
            return null;
        }
        final boolean desperate = desperate();
        lender.modifyGold(-principal);
        me.modifyGold(principal);
        if (me.getGold() < price) {
            me.modifyGold(-principal);
            lender.modifyGold(principal);
            return null;
        }
        me.modifyGold(-price);
        final List<Unit> made = ((ServerPlayer)me).createUnits(List.of(
            new AbstractUnit(ut, Specification.DEFAULT_ROLE_ID, 1)),
            me.getEurope(), ai.getAIRandom());
        if (made == null || made.isEmpty()) {
            me.modifyGold(price - principal);
            lender.modifyGold(principal);
            return null;
        }
        gold(lender, 0, cs); // the player's gold line now
        final Loan l = new Loan("loan:" + me.getNationId() + ":"
            + lender.getNationId() + ":" + now, lender.getNationId(),
            me.getNationId(), principal, rent, term, now, ut.getId());
        l.shipId = made.get(0).getId();
        ai.getLoans().add(l);
        ai.getLoanAsks().remove(ALL);
        // What it leaves with us.
        final String id = lender.getNationId();
        ai.getMemories().add(new Memory(Memory.Kind.LOAN_GIVEN, id, now,
                                        (desperate) ? 2 : 1));
        final int squeeze = LoanRules.squeeze(l.rent);
        if (squeeze > 0) {
            ai.getMemories().add(new Memory(Memory.Kind.LOAN_SQUEEZED, id,
                                            now, squeeze));
        } else if (l.rent < LoanRules.GIFT_RENT) {
            ai.getMemories().add(new Memory(Memory.Kind.GIFT, id, now, 1));
        }
        message(cs, lender, "loan.signed", l, principal);
        return l;
    }


    // The player's answers

    /**
     * The player answers a loan request, our counter, or the question
     * after our default.
     *
     * @param o The {@code Offer}.
     * @param human The player.
     * @param accept True to accept.
     * @param amount For a request, rent * 100 + term; for a default, 0
     *     seize, 1 forgive, 2 wait.
     * @param cs A {@code ChangeSet} to update.
     * @return True (the offer was ours).
     */
    public boolean answer(Offer o, ServerPlayer human, boolean accept,
                          int amount, ChangeSet cs) {
        if (o.kind == Offer.Kind.LOAN_DEFAULT) {
            return answerDefault(o, human, (accept) ? Math.max(0, amount) : 2,
                                 cs);
        }
        final int now = now();
        o.takenBy = human.getNationId();
        o.takenTurn = now;
        if (!accept) {
            o.state = Offer.State.DECLINED;
            refused(human.getNationId());
            return true;
        }
        final UnitType ut = game().getSpecification().getUnitType(o.goods);
        if (ut == null || needsLoan() == null) {
            o.state = Offer.State.EXPIRED; // we found the gold meanwhile
            message(cs, human, "loan.noLonger", null, o.amount);
            return true;
        }
        final boolean counter = o.id.endsWith(Offer.COUNTER);
        final int rent = (counter) ? o.delivered
            : Loan.clampRent((amount < 0) ? Loan.DEFAULT_RENT : amount / 100);
        final int term = (counter) ? o.deadline
            : Loan.clampTerm((amount < 0) ? Loan.DEFAULT_TERM : amount % 100);
        final int ceiling = LoanRules.ceiling(ai.character(), desperate(),
            CreditRecords.standing(me()), true);
        if (!counter && rent > ceiling) {
            // Too dear: our highest rent, a dialog now.
            o.state = Offer.State.DECLINED;
            final Offer c = new Offer(o.id + Offer.COUNTER, Offer.Kind.LOAN,
                me().getNationId(), o.goods, o.amount, o.gold, null,
                o.placeName, now, term);
            c.delivered = ceiling;
            c.audience.add(human.getNationId());
            c.told.add(human.getNationId());
            ai.getOffers().add(c);
            cs.add(See.only(human), new OfferMessage(c));
            message(cs, human, "loan.counter", null, ceiling);
            return true;
        }
        final Loan l = sign(human, o.amount, rent, term, ut, cs);
        o.state = (l == null) ? Offer.State.CANCELLED : Offer.State.DONE;
        if (l == null) message(cs, human, "loan.failed", null, o.amount);
        return true;
    }

    /** The player decides what to do about our default. */
    private boolean answerDefault(Offer o, ServerPlayer human, int choice,
                                  ChangeSet cs) {
        o.state = Offer.State.DONE;
        o.takenBy = human.getNationId();
        o.takenTurn = now();
        final Loan l = find(ai.getAIMain(), o.place);
        if (l == null || l.over() || l.state != Loan.State.DEFAULT
            || !human.getNationId().equals(l.lender)) return true;
        final int now = now();
        switch (choice) {
        case 1: // forgive
            final int gone = l.claim();
            l.forgive(now);
            CreditRecords.of(me()).mark(CreditRecord.Mark.FORGIVEN, l.lender);
            ai.getMemories().add(new Memory(Memory.Kind.LOAN_FORGIVEN,
                human.getNationId(), now, 1));
            message(cs, human, "loan.forgiven", l, gone);
            break;
        case 2: // wait
            l.waitFor(now);
            message(cs, human, "loan.waiting", l, l.claim());
            break;
        default: // seize
            l.state = Loan.State.SEIZURE_PENDING;
            if (ship(l) != null && !ship(l).isInEurope()) {
                message(cs, human, "loan.seizePending", l, l.claim());
            }
            trySeize(l, human, cs);
            break;
        }
        CreditRecords.of(me()).inDefault = inDefault();
        return true;
    }


    // Helpers

    /** Gold changes, and the player's gold line at once. */
    private static void gold(Player p, int amount, ChangeSet cs) {
        if (amount != 0) p.modifyGold(amount);
        if (!p.isAI()) {
            cs.addPartial(See.only(p), p, "gold", String.valueOf(p.getGold()),
                          "score", String.valueOf(p.getScore()));
        }
    }

    /** A memory of a computer lender (or other nation) about someone. */
    private void remember(Player who, Memory.Kind k, Player about, int count) {
        final AIPlayer a = ai.getAIMain().getAIPlayer(who);
        if (a instanceof EuropeanAIPlayer) {
            ((EuropeanAIPlayer)a).getMemories().add(new Memory(k,
                about.getNationId(), now(), count));
        }
    }

    /** Every nation that knows us hears of it (not the lender). */
    private void everyone(Memory.Kind k, Player lender) {
        for (Player p : game().getLiveEuropeanPlayerList()) {
            if (p == me() || p == lender || p.isREF()
                || !p.hasContacted(me())) continue;
            remember(p, k, me(), 1);
        }
    }

    /** A line in the player's turn report (computer lenders: none). */
    private void message(ChangeSet cs, Player to, String key, Loan l,
                         int gold) {
        if (to == null || to.isAI()) return;
        final ModelMessage mm = new ModelMessage(
            ModelMessage.MessageType.FOREIGN_DIPLOMACY, key, me())
            .addStringTemplate("%nation%", me().getNationLabel())
            .addAmount("%gold%", gold)
            .addAmount("%balance%", (l == null) ? 0 : l.claim())
            .addAmount("%missed%", (l == null) ? 0 : l.missed)
            .addAmount("%turns%", Loan.WAIT_TURNS)
            .addName("%rent%", (l == null) ? rentText(gold)
                     : rentText(l.rent));
        cs.addMessage(to, mm);
    }

    /** Rent in tenths as "2.5%". */
    public static String rentText(int rent) {
        return rent / 10 + "." + rent % 10 + "%";
    }
}
