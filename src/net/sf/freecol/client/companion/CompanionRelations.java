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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HighSeas;
import net.sf.freecol.common.model.IndianNationType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Monarch;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.util.RandomChoice;


/**
 * The relations tab of the companion page: the native tribes, the
 * other European nations, and the homefront (the King, Europe, the
 * independence movement).
 *
 * Only what this client knows is shown, the same information as the
 * game's own reports: settlements on the known map, skills and wanted
 * goods once a unit has visited, and so on.
 *
 * Must be called on the Swing event thread.
 */
final class CompanionRelations {

    private CompanionRelations() {}


    // What each settlement wanted when we last looked, and since when.
    // Kept for the session; the game itself does not remember when the
    // player learned something.

    /** Wanted goods of each settlement, as a key, by settlement id. */
    private static final java.util.Map<String, String> wantedKey
        = new java.util.HashMap<>();

    /** The turn the wanted goods were first seen like this. */
    private static final java.util.Map<String, Integer> wantedSince
        = new java.util.HashMap<>();

    private static String gameKey = null;

    private static String key(IndianSettlement is) {
        StringBuilder sb = new StringBuilder();
        for (GoodsType gt : is.getWantedGoods()) {
            if (gt != null) sb.append(gt.getId()).append(',');
        }
        return sb.toString();
    }

    /**
     * Notice changes in what known native settlements want, and log
     * them.  Called with every snapshot.
     *
     * @param game The {@code Game}.
     * @param me The client {@code Player}.
     */
    static void observe(Game game, Player me) {
        final String gk = game.getUUID() + "/" + me.getId();
        final boolean fresh = !gk.equals(gameKey);
        if (fresh) {
            gameKey = gk;
            wantedKey.clear();
            wantedSince.clear();
        }
        final int turn = game.getTurn().getNumber();
        for (Tile t : game.getMap().getTileSet(Tile::hasSettlement)) {
            if (!(t.getSettlement() instanceof IndianSettlement)) continue;
            final IndianSettlement is = (IndianSettlement)t.getSettlement();
            final String k = key(is);
            if (k.isEmpty()) continue; // Not known yet
            final String before = wantedKey.put(is.getId(), k);
            if (k.equals(before)) continue;
            wantedSince.put(is.getId(), turn);
            if (fresh || before == null) continue; // Not a change
            java.util.Map<String, Integer> goods = new java.util.LinkedHashMap<>();
            List<String> names = new ArrayList<>();
            for (GoodsType gt : is.getWantedGoods()) {
                if (gt == null) continue;
                goods.put(gt.getId(), 0);
                names.add(Messages.getName(gt));
            }
            CompanionLog.post(turn, "natives", Messages.message(
                    net.sf.freecol.common.model.StringTemplate
                        .template("companion.log.wanted")
                        .addName("%settlement%", is.getName())
                        .addStringTemplate("%nation%",
                            is.getOwner().getNationLabel())
                        .addName("%goods%", String.join(", ", names))),
                t, goods);
        }
    }


    private static String stance(Player me, Player other) {
        final Stance s = me.getStance(other);
        return (s == null) ? "" : s.getKey().replace("stance.", "");
    }

    private static String stanceName(Player me, Player other) {
        final Stance s = me.getStance(other);
        return (s == null) ? "" : Messages.message(s.getNameKey());
    }

    /**
     * Build the relations.
     *
     * @param game The {@code Game}.
     * @param me The client {@code Player}.
     * @return The relations as JSON.
     */
    static String build(Game game, Player me) {
        Json j = new Json().obj();
        homefront(j, game, me);

        // Settlements on the known map, by owner.
        List<Settlement> known = new ArrayList<>();
        for (Tile t : game.getMap().getTileSet(Tile::hasSettlement)) {
            known.add(t.getSettlement());
        }

        List<Player> europeans = new ArrayList<>(), natives = new ArrayList<>();
        for (Player p : game.getLivePlayerList(me)) {
            if (p.isREF()) continue;
            if (p.isIndian()) natives.add(p); else if (p.isEuropean()) europeans.add(p);
        }
        final Comparator<Player> byName = Comparator.comparing(p ->
            Messages.message(p.getNationLabel()));
        europeans.sort(byName);
        natives.sort(byName);

        j.key("europeans").arr();
        for (Player p : europeans) {
            final boolean met = me.hasContacted(p);
            List<String> colonies = new ArrayList<>();
            for (Settlement s : known) {
                if (s.getOwner() == p) colonies.add(s.getName());
            }
            j.obj().put("name", Messages.message(p.getNationLabel()))
                .put("color", p.getNationColor())
                .put("met", met)
                .put("stance", stance(me, p))
                .put("stanceName", stanceName(me, p))
                .put("rebel", p.getPlayerType() == Player.PlayerType.REBEL
                    || p.getPlayerType() == Player.PlayerType.INDEPENDENT);
            j.key("colonies").arr();
            for (String c : colonies) j.val(c);
            j.endArr().end();
        }
        j.endArr();

        int unmet = 0;
        j.key("tribes").arr();
        for (Player p : natives) {
            if (!me.hasContacted(p)) {
                unmet++;
                continue;
            }
            j.obj().put("name", Messages.message(p.getNationLabel()))
                .put("color", p.getNationColor())
                .put("stance", stance(me, p))
                .put("stanceName", stanceName(me, p));
            profile(j, p);
            j.key("settlements").arr();
            String worst = null;
            for (Settlement s : known) {
                if (s.getOwner() != p || !(s instanceof IndianSettlement)) continue;
                final IndianSettlement is = (IndianSettlement)s;
                final Tension alarm = (is.hasContacted(me)) ? is.getAlarm(me) : null;
                final String level = (alarm == null) ? "unknown"
                    : alarm.getLevel().name().toLowerCase();
                if (alarm != null && (worst == null
                        || Tension.Level.valueOf(level.toUpperCase()).ordinal()
                        > Tension.Level.valueOf(worst.toUpperCase()).ordinal())) {
                    worst = level;
                }
                final UnitType skill = is.getLearnableSkill();
                final Unit missionary = is.getMissionary();
                final Player hated = is.getMostHated();
                j.obj().put("name", is.getName())
                    .put("x", is.getTile().getX()).put("y", is.getTile().getY())
                    .put("capital", is.isCapital())
                    .put("visited", is.hasVisited(me))
                    .put("alarm", level)
                    .put("alarmName", Messages.message(is.getAlarmLevelKey(me)))
                    .put("skill", (skill == null) ? null : Messages.getName(skill))
                    .put("mission", (missionary == null) ? null
                        : (missionary.getOwner() == me) ? "ours"
                        : Messages.message(missionary.getOwner().getNationLabel()))
                    .put("hated", (hated == null) ? null
                        : Messages.message(hated.getNationLabel()))
                    .put("wantedSince", wantedSince.getOrDefault(is.getId(), -1));
                j.key("wanted").arr();
                for (GoodsType gt : is.getWantedGoods()) {
                    if (gt != null) j.val(gt.getId());
                }
                j.endArr().end();
            }
            j.endArr();
            j.put("worst", worst).end();
        }
        j.endArr();
        j.put("unmet", unmet);
        return j.end().toString();
    }

    private static String lastPart(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    /** What the rules say about a tribe: settlements, temper, skills. */
    private static void profile(Json j, Player p) {
        if (!(p.getNationType() instanceof IndianNationType)) return;
        final IndianNationType nt = (IndianNationType)p.getNationType();
        j.put("settlementType", Messages.message(nt.getSettlementTypeKey(true)))
            .put("aggression", lastPart(nt.getAggression().getKey()))
            .put("number", lastPart(nt.getNumberOfSettlements().getKey()));
        List<RandomChoice<UnitType>> skills = new ArrayList<>(nt.getSkills());
        skills.sort(Comparator.comparingInt(
            (RandomChoice<UnitType> rc) -> rc.getProbability()).reversed());
        j.key("skills").arr();
        for (RandomChoice<UnitType> rc : skills) {
            j.arr().val(Messages.getName(rc.getObject()))
                .val(rc.getProbability()).endArr();
        }
        j.endArr();
    }

    private static void homefront(Json j, Game game, Player me) {
        j.key("home").obj()
            .put("nation", Messages.message(me.getNationLabel()))
            .put("tax", me.getTax())
            .put("gold", me.getGold())
            .put("score", me.getScore())
            .put("sol", me.getSoL())
            .put("playerType", me.getPlayerType().name().toLowerCase());

        // The Royal Expeditionary Force, as the King's report shows it.
        final Monarch monarch = me.getMonarch();
        j.key("ref").arr();
        if (monarch != null && monarch.getExpeditionaryForce() != null) {
            for (AbstractUnit au : monarch.getExpeditionaryForce().getUnitList()) {
                j.arr().val(Messages.message(au.getLabel()))
                    .val(au.getNumber()).endArr();
            }
        }
        j.endArr();

        // Boycotts: goods that can not be traded in Europe until the
        // arrears are paid.
        j.key("boycotts").arr();
        for (GoodsType gt : game.getSpecification().getStorableGoodsTypeList()) {
            final int arrears = me.getArrears(gt);
            if (arrears > 0) j.arr().val(gt.getId()).val(arrears).endArr();
        }
        j.endArr();

        final FoundingFather ff = me.getCurrentFather();
        j.put("father", (ff == null) ? null : Messages.getName(ff))
            .put("fatherBells", me.getRemainingFoundingFatherCost())
            .put("fathers", me.getFatherCount())
            .put("immigration", me.getImmigration())
            .put("immigrationRequired", me.getImmigrationRequired());

        // Europe: who waits on the docks, who is at sea.
        final Europe europe = me.getEurope();
        int docks = 0, ships = 0, toEurope = 0, toAmerica = 0;
        for (Unit u : me.getUnitSet()) {
            if (u.isInEurope()) {
                if (u.isNaval()) ships++; else docks++;
            } else if (u.getLocation() instanceof HighSeas) {
                if (u.getDestination() instanceof Europe) toEurope++;
                else toAmerica++;
            }
        }
        j.put("recruitPrice", (europe == null) ? -1
                : europe.getCurrentRecruitPrice())
            .put("docks", docks).put("shipsInEurope", ships)
            .put("toEurope", toEurope).put("toAmerica", toAmerica);

        // Our colonies, for the overall picture.
        int colonists = 0;
        for (Colony c : me.getColonyList()) colonists += c.getUnitCount();
        j.put("colonies", me.getColonyList().size())
            .put("colonists", colonists);
        j.end();
    }
}
