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

package net.sf.freecol.tools.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.diplomacy.Estimates;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Unit;


/**
 * The facts of one saved turn that the replay compares from turn to
 * turn: per nation its figures, the stances and tensions between
 * nations, every colony and settlement, every armed unit and ship.
 * Plain data, so diffs can be tested without loading games.
 */
public final class Snapshot {

    public static final class Nation {
        public final String id;
        public final boolean european, ai;
        public double army, navy, output;
        public int gold, colonies, colonists, income;

        public Nation(String id, boolean european, boolean ai) {
            this.id = id;
            this.european = european;
            this.ai = ai;
        }
    }

    public static final class Place {
        public final String id, name, owner;
        public final int size;
        public final boolean nativeSettlement;
        /** Nation with a mission there, or null. */
        public final String missionary;

        public Place(String id, String name, String owner, int size,
                     boolean nativeSettlement, String missionary) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.size = size;
            this.nativeSettlement = nativeSettlement;
            this.missionary = missionary;
        }
    }

    public static final class Troop {
        public final String id, owner;
        public final boolean warship, damaged;

        public Troop(String id, String owner, boolean warship,
                     boolean damaged) {
            this.id = id;
            this.owner = owner;
            this.warship = warship;
            this.damaged = damaged;
        }
    }

    public final int turn;
    public final String label;
    public final Map<String, Nation> nations = new LinkedHashMap<>();
    /** "a|b" (ordered) to stance of a towards b. */
    public final Map<String, Stance> stances = new HashMap<>();
    /** "a|b" to tension of a towards b (computer players only). */
    public final Map<String, Integer> tensions = new HashMap<>();
    public final Map<String, Place> places = new LinkedHashMap<>();
    /** Armed land units and warships, by id. */
    public final Map<String, Troop> troops = new HashMap<>();
    /** History events as "nation|type|other|turn". */
    public final List<String> events = new ArrayList<>();


    public Snapshot(int turn, String label) {
        this.turn = turn;
        this.label = label;
    }

    /** "1669 spring", "1493". */
    public static String label(net.sf.freecol.common.model.Turn turn) {
        if (turn.getNumber() < net.sf.freecol.common.model.Turn
            .yearToTurn(net.sf.freecol.common.model.Turn.getSeasonYear())) {
            return String.valueOf(turn.getYear());
        }
        return turn.getYear() + ((turn.getSeason() == 0) ? " spring"
                                                         : " autumn");
    }

    public static String key(String a, String b) {
        return a + "|" + b;
    }

    public Stance stance(String a, String b) {
        Stance s = stances.get(key(a, b));
        return (s == null) ? Stance.UNCONTACTED : s;
    }

    public int tension(String a, String b) {
        Integer t = tensions.get(key(a, b));
        return (t == null) ? -1 : t;
    }

    /**
     * Take a snapshot of a game.
     *
     * @param game The {@code Game}.
     * @return The {@code Snapshot}.
     */
    public static Snapshot of(Game game) {
        final Specification spec = game.getSpecification();
        final double sp = Estimates.soldierPower(spec);
        final double fp = Estimates.frigatePower(spec);
        final net.sf.freecol.common.model.Turn turn = game.getTurn();
        Snapshot s = new Snapshot(turn.getNumber(), label(turn));
        List<Player> players = new ArrayList<>();
        for (Player p : game.getLivePlayerList()) {
            if (p.isREF() || p.isUnknownEnemy() || p.isUndead()) continue;
            players.add(p);
            Nation n = new Nation(p.getNationId(), p.isEuropean(), p.isAI());
            n.army = p.calculateStrength(false) / sp;
            n.navy = p.calculateStrength(true) / fp;
            n.gold = Math.max(0, p.getGold());
            if (p.isEuropean()) {
                for (Colony c : p.getColonyList()) {
                    n.colonies++;
                    n.colonists += c.getUnitCount();
                    n.output += Estimates.colonyValue(c, p.getMarket());
                    s.places.put(c.getId(), new Place(c.getId(), c.getName(),
                        n.id, c.getUnitCount(), false, null));
                }
                if (p.getMarket() != null) {
                    for (var gt : spec.getStorableGoodsTypeList()) {
                        var md = p.getMarket().getMarketData(gt);
                        if (md != null) n.income += md.getIncomeAfterTaxes();
                    }
                }
            } else {
                for (IndianSettlement is : p.getIndianSettlementList()) {
                    n.colonies++;
                    n.colonists += is.getUnitCount();
                    Unit m = is.getMissionary();
                    s.places.put(is.getId(), new Place(is.getId(),
                        is.getName(), n.id, is.getUnitCount(), true,
                        (m == null) ? null : m.getOwner().getNationId()));
                }
            }
            for (Unit u : (Iterable<Unit>)p.getUnits()::iterator) {
                boolean warship = u.isNaval() && u.isOffensiveUnit();
                if (warship || (!u.isNaval() && u.isOffensiveUnit())) {
                    s.troops.put(u.getId(), new Troop(u.getId(), n.id,
                        warship, u.isNaval() && u.isDamaged()));
                }
            }
            for (HistoryEvent h : p.getHistory()) {
                Player o = game.getFreeColGameObject(h.getPlayerId(),
                                                     Player.class);
                s.events.add(n.id + "|" + h.getEventType().name() + "|"
                    + ((o == null) ? "" : o.getNationId()) + "|"
                    + h.getTurn().getNumber());
            }
            s.nations.put(n.id, n);
        }
        for (Player p : players) {
            for (Player q : players) {
                if (p == q) continue;
                s.stances.put(key(p.getNationId(), q.getNationId()),
                              p.getStance(q));
                if (p.isAI() || !p.isEuropean()) {
                    s.tensions.put(key(p.getNationId(), q.getNationId()),
                                   p.getTension(q).getValue());
                }
            }
        }
        return s;
    }
}
