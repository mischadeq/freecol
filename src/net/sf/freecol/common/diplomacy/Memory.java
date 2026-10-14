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

package net.sf.freecol.common.diplomacy;


/**
 * Something a nation remembers about another, with a weight that fades
 * to nothing over a number of turns.
 */
public final class Memory {

    /**
     * The kinds of memory: opinion weight, turns it lasts, trust change
     * (trust is remembered twice as long as the feeling).
     */
    public enum Kind {
        /** They declared war on us. */
        WAR(-30, 30, 0),
        /** They took one of our colonies. */
        COLONY_TAKEN(-60, 60, 0),
        /** We made peace. */
        PEACE(10, 20, 0),
        /** We lost units (in a war with them). */
        UNIT_LOST(-5, 10, 0),
        /** They damaged one of our ships. */
        SHIP_DAMAGED(-3, 10, 0),
        /** They destroyed one of our settlements (tribes). */
        SETTLEMENT_DESTROYED(-60, 80, 0),
        /** A peace treaty on probation: no feeling, but if it holds
            {@link Memories#PROBATION} turns it becomes DEAL_KEPT, if
            they break it, DEAL_BROKEN. */
        TREATY(0, Memories.PROBATION, 0),
        /** A peace or alliance with them that held. */
        DEAL_KEPT(10, 40, 15),
        /** They delivered what they promised, but late. */
        DEAL_LATE(-2, 15, -5),
        /** They attacked their own ally (we saw it: alliances are public). */
        BETRAYER(-10, 30, -15),
        /** They broke a fresh peace or attacked their ally (us). */
        DEAL_BROKEN(-40, 60, -40),
        /** They gave us something for nothing. */
        GIFT(5, 15, 5),
        /** They sold to us or bought from us. */
        TRADE(3, 20, 3),
        /** They took a colony of our enemy. */
        HELPED_IN_WAR(15, 30, 10),
        /** They turned down our offer. */
        REFUSED_OFFER(-3, 10, -2);

        public final int weight;
        public final int fade;
        public final int trust;

        Kind(int weight, int fade, int trust) {
            this.weight = weight;
            this.fade = fade;
            this.trust = trust;
        }

        /** Turns the trust change lasts. */
        public int trustFade() {
            return 2 * fade;
        }

        /** Turns before it is forgotten altogether. */
        public int lasts() {
            return (trust == 0) ? fade : trustFade();
        }
    }

    public final Kind kind;
    /** The nation it is about (id). */
    public final String about;
    /** The turn it happened. */
    public final int turn;
    /** How many times (e.g. units lost that turn). */
    public final int count;


    public Memory(Kind kind, String about, int turn, int count) {
        this.kind = kind;
        this.about = about;
        this.turn = turn;
        this.count = count;
    }

    /**
     * What this memory is worth now.
     *
     * @param now The current turn.
     * @return The faded weight (0 once forgotten, or for a future turn).
     */
    public double value(int now) {
        final int age = now - turn;
        if (age < 0 || age >= kind.fade) return 0;
        return kind.weight * count * (1.0 - age / (double)kind.fade);
    }

    /**
     * What this memory does to trust now.
     *
     * @param now The current turn.
     * @return The faded trust change.
     */
    public double trust(int now) {
        final int age = now - turn;
        final int fade = kind.trustFade();
        if (kind.trust == 0 || age < 0 || age >= fade) return 0;
        return kind.trust * count * (1.0 - age / (double)fade);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Memory)) return false;
        Memory m = (Memory)o;
        return kind == m.kind && about.equals(m.about) && turn == m.turn
            && count == m.count;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, about, turn, count);
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + " " + Action.shortId(about)
            + " t" + turn + ((count > 1) ? " x" + count : "");
    }
}
