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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.ProductionType;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.UnitType;


/**
 * The game rules as a small encyclopedia for the companion page:
 * goods with what they are made from and into, buildings, units and
 * founding fathers, with the texts the game itself uses.  Built from
 * the specification in play, so it matches the rules and mods of the
 * current game.  Market prices are the player's current ones.
 *
 * Must be called on the Swing event thread.
 */
final class CompanionColopedia {

    private CompanionColopedia() {}


    private static String desc(String id) {
        final String key = id + ".description";
        return (Messages.containsKey(key)) ? Messages.message(key) : "";
    }

    private static void goodsList(Json j, String key, List<AbstractGoods> list) {
        j.key(key).arr();
        for (AbstractGoods ag : list) {
            if (ag.getAmount() > 0) {
                j.arr().val(ag.getType().getId()).val(ag.getAmount()).endArr();
            }
        }
        j.endArr();
    }

    private static void names(Json j, String key, Set<String> names) {
        j.key(key).arr();
        for (String s : names) j.val(s);
        j.endArr();
    }

    /** What a building can produce and consume, attended or not. */
    private static List<ProductionType> production(BuildingType bt) {
        List<ProductionType> result = new ArrayList<>();
        result.addAll(bt.getAvailableProductionTypes(false));
        result.addAll(bt.getAvailableProductionTypes(true));
        return result;
    }

    /**
     * Build the encyclopedia.
     *
     * @param spec The {@code Specification} in play.
     * @param player The {@code Player}, for prices and fathers.
     * @return The encyclopedia as JSON.
     */
    static String build(Specification spec, Player player) {
        final Market market = player.getMarket();
        final List<BuildingType> buildings = spec.getBuildingTypeList();
        final List<UnitType> units = new ArrayList<>();
        for (UnitType ut : spec.getUnitTypeList()) {
            if (ut.hasAbility(Ability.REF_UNIT) || ut.hasAbility(Ability.NATIVE)
                || !ut.isAvailableTo(player)) continue;
            units.add(ut);
        }

        Json j = new Json().obj();
        j.put("manual", "https://freecol.org/docs/FreeCol.html");

        // Goods.
        j.key("goods").arr();
        for (GoodsType gt : spec.getGoodsTypeList()) {
            if (!gt.isStorable() && !gt.isLibertyType()
                && !gt.isImmigrationType() && !gt.isBuildingMaterial()) continue;
            Set<String> madeIn = new LinkedHashSet<>();
            Set<String> usedIn = new LinkedHashSet<>();
            for (BuildingType bt : buildings) {
                for (ProductionType pt : production(bt)) {
                    for (AbstractGoods ag : pt.getOutputList()) {
                        if (ag.getType() == gt) madeIn.add(Messages.getName(bt));
                    }
                    for (AbstractGoods ag : pt.getInputList()) {
                        if (ag.getType() == gt) usedIn.add(Messages.getName(bt));
                    }
                }
            }
            Set<String> experts = new LinkedHashSet<>();
            for (UnitType ut : units) {
                if (ut.getExpertProduction() == gt) experts.add(Messages.getName(ut));
            }
            Set<String> neededFor = new LinkedHashSet<>();
            List<BuildableType> buildables = new ArrayList<>(buildings);
            buildables.addAll(units);
            for (BuildableType b : buildables) {
                if (b.getRequiredAmountOf(gt) > 0) neededFor.add(Messages.getName(b));
            }
            j.obj().put("id", gt.getId()).put("name", Messages.getName(gt))
                .put("desc", desc(gt.getId()))
                .put("from", (gt.getInputType() == null) ? null
                    : gt.getInputType().getId())
                .put("into", (gt.getOutputType() == null) ? null
                    : gt.getOutputType().getId())
                .put("food", gt.isFoodType())
                .put("storable", gt.isStorable());
            if (market != null && gt.isStorable()) {
                j.put("sell", market.getPaidForSale(gt))
                    .put("buy", market.getCostToBuy(gt));
            }
            names(j, "madeIn", madeIn);
            names(j, "usedIn", usedIn);
            names(j, "experts", experts);
            names(j, "neededFor", neededFor);
            j.end();
        }
        j.endArr();

        // Buildings.
        j.key("buildings").arr();
        for (BuildingType bt : buildings) {
            List<AbstractGoods> outs = new ArrayList<>(), ins = new ArrayList<>();
            for (ProductionType pt : production(bt)) {
                for (AbstractGoods ag : pt.getOutputList()) {
                    if (outs.stream().noneMatch(o -> o.getType() == ag.getType())) outs.add(ag);
                }
                for (AbstractGoods ag : pt.getInputList()) {
                    if (ins.stream().noneMatch(o -> o.getType() == ag.getType())) ins.add(ag);
                }
            }
            j.obj().put("id", bt.getId()).put("name", Messages.getName(bt))
                .put("desc", desc(bt.getId()))
                .put("pop", bt.getRequiredPopulation())
                .put("workers", bt.getWorkPlaces())
                .put("from", (bt.getUpgradesFrom() == null) ? null
                    : Messages.getName(bt.getUpgradesFrom()))
                .put("to", (bt.getUpgradesTo() == null) ? null
                    : Messages.getName(bt.getUpgradesTo()));
            goodsList(j, "cost", bt.getRequiredGoodsList());
            goodsList(j, "makes", outs);
            goodsList(j, "uses", ins);
            j.end();
        }
        j.endArr();

        // Units.
        j.key("units").arr();
        for (UnitType ut : units) {
            j.obj().put("id", ut.getId()).put("name", Messages.getName(ut))
                .put("desc", desc(ut.getId()))
                .put("price", (ut.hasPrice()) ? ut.getPrice() : -1)
                .put("expert", (ut.getExpertProduction() == null) ? null
                    : ut.getExpertProduction().getId())
                .put("offence", ut.getBaseOffence())
                .put("defence", ut.getBaseDefence())
                .put("moves", ut.getMovement() / 3)
                .put("naval", ut.isNaval())
                .put("space", ut.getSpace());
            goodsList(j, "cost", ut.getRequiredGoodsList());
            j.end();
        }
        j.endArr();

        // Founding fathers.
        final FoundingFather current = player.getCurrentFather();
        j.key("congress").obj()
            .put("current", (current == null) ? null : Messages.getName(current))
            .put("remaining", player.getRemainingFoundingFatherCost())
            .end();
        j.key("fathers").arr();
        for (FoundingFather ff : spec.getFoundingFathers()) {
            j.obj().put("id", ff.getId()).put("name", Messages.getName(ff))
                .put("type", Messages.message(ff.getTypeKey()))
                .put("desc", desc(ff.getId()))
                .put("have", player.hasFather(ff))
                .put("current", ff == current).end();
        }
        j.endArr();
        return j.end().toString();
    }
}
