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

package net.sf.freecol.client.gui.panel;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.Timer;

import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitChangeType;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Turn;


/**
 * The pieces of the combat screens (mod): which battle messages get the
 * battle card, who won, the units named in a message, the strength of a
 * side split into its parts, and two small animated pictures (the
 * strength bars before an attack, the clash after it).
 */
public final class CombatView {

    /** Ink colours that sit well on the parchment. */
    public static final Color INK = new Color(0x3b, 0x2a, 0x1a),
        OURS = new Color(0x2f, 0x52, 0x8a), BONUS = new Color(0x4e, 0x7a, 0x34),
        PENALTY = new Color(0x9a, 0x33, 0x24), UNKNOWN = new Color(0x9a, 0x8e, 0x78),
        WIN = new Color(0x3c, 0x6e, 0x2a), LOSS = new Color(0x9a, 0x2a, 0x1c);
    /** Their bar: a brown, so red keeps meaning "penalty". */
    static final Color THEIRS = new Color(0x7d, 0x5a, 0x3c);
    /** Quieter ink for side notes. */
    public static final Color INK_MUTED = new Color(0x6b, 0x58, 0x40);

    /** How a battle went for the player. */
    public enum Outcome { VICTORY, DEFEAT, SKIRMISH }

    private CombatView() {}


    // Messages

    /**
     * Is a message the report of one battle (gets the battle card)?
     *
     * @param id The message's template id.
     * @param type The message type.
     * @return True if so.
     */
    public static boolean isBattle(String id, ModelMessage.MessageType type) {
        return type == ModelMessage.MessageType.COMBAT_RESULT && id != null
            && outcome(id) != null;
    }

    /**
     * Who won, from the message id: ".ours" is a defeat, ".enemy" a
     * victory, an evaded ship a skirmish.
     *
     * @param id The template id.
     * @return The outcome, or null if the message is no single battle.
     */
    public static Outcome outcome(String id) {
        if (id == null || !id.startsWith("combat.")) return null;
        final String k = id.substring("combat.".length());
        if (!k.matches("(unit|ship|colony|destroySettlement|raid\\.(building|goods|plunder)).*")
            && !k.equals("unitLoseAutoEquip")) return null;
        if (k.startsWith("shipEvaded")) return Outcome.SKIRMISH;
        if (k.equals("unitLoseAutoEquip")) return Outcome.DEFEAT;
        if (k.startsWith("raid.")) return Outcome.DEFEAT;
        if (k.contains(".ours")) return Outcome.DEFEAT;
        if (k.contains(".enemy")) return Outcome.VICTORY;
        return null;
    }

    /**
     * The unit type and role named in a unit label template (as made by
     * {@code Messages.getUnitLabel}), for its picture.
     *
     * @param t The template, may be null.
     * @return { type id, role id } (either may be null).
     */
    public static String[] unitIds(StringTemplate t) {
        final String[] ret = new String[2];
        find(t, ret, 0);
        return ret;
    }

    private static void find(StringTemplate t, String[] ret, int depth) {
        if (t == null || depth > 4) return;
        final String id = t.getId();
        if (id != null) {
            if (ret[0] == null && id.startsWith("model.unit.")) {
                // "model.unit.x.name", or "model.unit.x.<role>" for the
                // special role names ("Seasoned Scout").
                final String rest = id.substring("model.unit.".length());
                final int dot = rest.indexOf('.');
                ret[0] = "model.unit." + ((dot < 0) ? rest : rest.substring(0, dot));
                if (dot >= 0 && ret[1] == null) {
                    final String suffix = rest.substring(dot + 1);
                    if (!"name".equals(suffix)) ret[1] = "model.role." + suffix;
                }
            } else if (ret[1] == null && id.startsWith("model.role.")) {
                ret[1] = id.endsWith(".name")
                    ? id.substring(0, id.length() - ".name".length()) : id;
            }
        }
        for (SimpleEntry<String, StringTemplate> e : t.entryList()) {
            find(e.getValue(), ret, depth + 1);
        }
    }


    /**
     * A battle sentence without its opening ("Defeat at X: "), which
     * the card shows already.
     *
     * @param text The game's sentence.
     * @return The rest.
     */
    public static String withoutOpening(String text) {
        if (text == null) return "";
        final java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^(Victory|Defeat|Skirmish|Action) at [^:]{1,120}:\\s+")
            .matcher(text);
        if (!m.find()) return text;
        final String rest = text.substring(m.end());
        return (rest.isEmpty()) ? text
            : Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
    }

    // What is at stake

    /**
     * What happens to the attacker if it loses.
     *
     * @param u The attacking {@code Unit}.
     * @return The words.
     */
    public static StringTemplate stakeIfLose(Unit u) {
        return stake(u, "combat.stake.lose.");
    }

    /**
     * What happens to the defender if the attack wins.
     *
     * @param u The defending {@code Unit}.
     * @return The words.
     */
    public static StringTemplate stakeIfWin(Unit u) {
        return stake(u, "combat.stake.win.");
    }

    private static StringTemplate stake(Unit u, String prefix) {
        final String kind;
        StringTemplate role = null;
        final Role r = u.getRole();
        if (u.isNaval()) {
            kind = "ship";
        } else if (r != null && !r.isDefaultRole()) {
            final Role down = r.getDowngrade();
            if (down != null && !down.isDefaultRole()) {
                kind = "demoted";
                role = StringTemplate.key(down.getId() + ".name");
            } else {
                kind = "disarmed";
            }
        } else if (u.getSpecification().getUnitChange(UnitChangeType.DEMOTION,
                                                      u.getType()) != null) {
            kind = "damaged";
        } else {
            kind = "lost";
        }
        final StringTemplate t = StringTemplate.template(prefix + kind)
            .addStringTemplate("%unit%", u.getLabel());
        if (role != null) t.addStringTemplate("%role%", role);
        return t;
    }

    /**
     * The colour of a chance to win: good, even, bad.
     *
     * @param odds The chance, 0..1, or negative if unknown.
     * @return The colour.
     */
    public static Color chanceColor(double odds) {
        return (odds < 0) ? UNKNOWN : (odds >= 0.6) ? WIN
            : (odds <= 0.4) ? LOSS : INK;
    }


    // Strength

    /** A side's strength split into what it is made of. */
    public static final class Strength {
        /** The base (additive and multiplicative), what the percentage
            bonuses add, what the penalties take, the total. */
        public final double base, bonus, penalty, total;
        /** False if the base is unknown (a defender not in sight). */
        public final boolean known;

        Strength(double base, double bonus, double penalty, double total,
                 boolean known) {
            this.base = base;
            this.bonus = bonus;
            this.penalty = penalty;
            this.total = total;
            this.known = known;
        }
    }

    /**
     * Split a side's modifiers into base, bonus and penalty, scaled so
     * they add up to the game's own total.
     *
     * @param mods The modifiers.
     * @param turn The turn.
     * @param total The game's total ({@code applyModifiers}).
     * @return The {@code Strength}.
     */
    public static Strength strength(Collection<Modifier> mods, Turn turn,
                                    double total) {
        double add = 0, mult = 1, plus = 0, minus = 0;
        boolean known = true;
        for (Modifier m : mods) {
            final float v = m.getValue(turn);
            if (Float.compare(v, Modifier.UNKNOWN) == 0) {
                known = false;
                continue;
            }
            switch (m.getType()) {
            case ADDITIVE: add += v; break;
            case MULTIPLICATIVE: mult *= v; break;
            case PERCENTAGE:
                if (v >= 0) plus += v; else minus -= v;
                break;
            default: break;
            }
        }
        final double base = Math.max(0, add * mult);
        double bonus = base * plus / 100, penalty = base * minus / 100;
        if (known && base > 0) {
            // Keep the parts true to the game's total.
            final double raw = base + bonus - penalty, net = bonus - penalty;
            if (Math.abs(raw - total) > 1e-6 && Math.abs(net) > 1e-9) {
                final double k = (total - base) / net;
                if (k > 0) {
                    bonus *= k;
                    penalty *= k;
                }
            }
        }
        return new Strength(base, bonus, penalty, (known) ? total : 0, known);
    }

    /** "3.8" */
    static String number(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }


    // Pictures

    /** Smooth drawing on the parchment. */
    private static Graphics2D smooth(Graphics g) {
        final Graphics2D g2 = (Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    /**
     * Two strength bars, yours above theirs, growing in when shown:
     * the base, what bonuses add (green), what penalties take (red
     * stripes), the total at the end; an unknown side is a grey
     * hatched bar with a question mark.
     */
    public static final class StrengthBars extends JComponent {

        private final Strength ours, theirs;
        private final String ourName, theirName;
        private final Font font;
        private float t = 0;
        private final Timer timer;

        public StrengthBars(Strength ours, String ourName, Strength theirs,
                            String theirName, Font font, int width) {
            this.ours = ours;
            this.theirs = theirs;
            this.ourName = ourName;
            this.theirName = theirName;
            this.font = font;
            setOpaque(false);
            setPreferredSize(new Dimension(width, 2 * (font.getSize() + 18) + 6));
            this.timer = new Timer(25, ae -> {
                    t = Math.min(1f, t + 0.06f);
                    repaint();
                    if (t >= 1f) ((Timer)ae.getSource()).stop();
                });
            this.timer.setInitialDelay(150);
            this.timer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = smooth(g);
            g2.setFont(this.font);
            final FontMetrics fm = g2.getFontMetrics();
            final int labelW = Math.max(fm.stringWidth(ourName), fm.stringWidth(theirName)) + 12;
            final int numW = fm.stringWidth("00.0") + 10;
            final int x0 = labelW, w = Math.max(40, getWidth() - labelW - numW);
            final double max = Math.max(1, 1.08 * Math.max(
                ours.base + ours.bonus, (theirs.known) ? theirs.base + theirs.bonus : 0));
            final int rowH = (getHeight() - 6) / 2, barH = Math.max(10, rowH - 12);
            final float e = 1 - (1 - t) * (1 - t) * (1 - t); // ease out
            row(g2, fm, ourName, ours, OURS, 0, x0, w, rowH, barH, max, e);
            row(g2, fm, theirName, theirs, THEIRS, rowH + 6, x0, w, rowH, barH, max, e);
            g2.dispose();
        }

        private void row(Graphics2D g2, FontMetrics fm, String name, Strength s,
                         Color base, int y, int x0, int w, int rowH, int barH,
                         double max, float e) {
            final int by = y + (rowH - barH) / 2;
            g2.setColor(INK);
            g2.drawString(name, 0, by + barH / 2 + fm.getAscent() / 2 - 2);
            if (!s.known) {
                final int bw = (int)(w * 0.6 * e);
                g2.setColor(UNKNOWN);
                g2.setStroke(new BasicStroke(2f));
                for (int x = x0 - barH; x < x0 + bw; x += 7) {
                    g2.drawLine(Math.max(x0, x), by + Math.max(0, x0 - x) + barH,
                                Math.min(x0 + bw, x + barH), by + Math.max(0, x + barH - x0 - bw));
                }
                g2.drawRect(x0, by, bw, barH);
                g2.setColor(INK);
                g2.drawString("?", x0 + bw + 6, by + barH / 2 + fm.getAscent() / 2 - 2);
                return;
            }
            final int pb = (int)(w * s.base / max * e);
            final int pp = (int)(w * s.bonus / max * e);
            final int pm = (int)(w * s.penalty / max * e);
            g2.setColor(base);
            g2.fillRect(x0, by, pb, barH);
            g2.setColor(BONUS);
            g2.fillRect(x0 + pb, by, pp, barH);
            // The penalty takes the end off: red stripes over it.
            final int end = x0 + pb + pp;
            if (pm > 0) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
                g2.setColor(new Color(0xf3, 0xe6, 0xc8));
                g2.fillRect(end - pm, by, pm, barH);
                g2.setColor(PENALTY);
                g2.setStroke(new BasicStroke(2f));
                for (int x = end - pm - barH; x < end; x += 6) {
                    g2.drawLine(Math.max(end - pm, x), by + barH - Math.max(0, end - pm - x),
                                Math.min(end, x + barH), by + Math.max(0, x + barH - end));
                }
                g2.setComposite(AlphaComposite.SrcOver);
                g2.drawRect(end - pm, by, pm, barH);
            }
            g2.setColor(INK);
            g2.drawString(number(s.total * e), end + 6, by + barH / 2 + fm.getAscent() / 2 - 2);
        }
    }

    /**
     * The clash after a battle: the two units ride at each other, a
     * flash, and the loser falls back, greyed. About a second; a click
     * skips to the end.
     */
    public static final class Clash extends JComponent {

        private final Image left, right;
        private final boolean leftWins, draw;
        private float t = 0;
        private final Timer timer;

        /**
         * @param left Our unit's picture (may be null).
         * @param right Theirs (may be null).
         * @param outcome How it went for us.
         * @param width The width.
         */
        public Clash(Image left, Image right, Outcome outcome, int width) {
            this.left = left;
            this.right = right;
            this.leftWins = outcome == Outcome.VICTORY;
            this.draw = outcome == Outcome.SKIRMISH;
            final int h = Math.max(height(left), height(right)) + 16;
            setOpaque(false);
            setPreferredSize(new Dimension(width, h));
            this.timer = new Timer(20, ae -> {
                    t = Math.min(1f, t + 0.02f);
                    repaint();
                    if (t >= 1f) ((Timer)ae.getSource()).stop();
                });
            this.timer.setInitialDelay(200);
            this.timer.start();
            addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent me) {
                        t = 1f;
                        timer.stop();
                        repaint();
                    }
                });
        }

        private static int height(Image i) {
            return (i == null) ? 48 : i.getHeight(null);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Graphics2D g2 = smooth(g);
            final int w = getWidth(), h = getHeight(), mid = w / 2;
            // 0..0.45 ride in, 0.45..0.6 flash, 0.6..1 the loser falls back.
            final float in = Math.min(1f, t / 0.45f);
            final float ride = in * in;
            final float back = Math.max(0f, (t - 0.6f) / 0.4f);
            final int gap = 6 + (int)((1 - ride) * (mid * 0.55));
            int lx = mid - gap - width(left), rx = mid + gap;
            float la = 1f, ra = 1f, lrot = 0f, rrot = 0f;
            if (!draw && back > 0) {
                final int fall = (int)(back * 24);
                if (leftWins) { rx += fall; ra = 1 - 0.55f * back; rrot = 0.25f * back; }
                else { lx -= fall; la = 1 - 0.55f * back; lrot = -0.25f * back; }
            } else if (draw && back > 0) {
                lx -= (int)(back * 14);
                rx += (int)(back * 14);
            }
            image(g2, left, lx, h, la, lrot, false);
            image(g2, right, rx, h, ra, rrot, true);
            if (t > 0.42f && t < 0.66f) {
                final float f = 1 - Math.abs((t - 0.54f) / 0.12f);
                final int r = (int)(8 + 22 * f);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                                           Math.max(0f, Math.min(1f, f))));
                g2.setColor(new Color(0xf2, 0xb8, 0x3b));
                final int cy = h / 2;
                for (int i = 0; i < 8; i++) {
                    final double a = i * Math.PI / 4;
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawLine(mid, cy, mid + (int)(Math.cos(a) * r), cy + (int)(Math.sin(a) * r));
                }
                g2.setComposite(AlphaComposite.SrcOver);
            }
            g2.dispose();
        }

        private static int width(Image i) {
            return (i == null) ? 48 : i.getWidth(null);
        }

        private static void image(Graphics2D g2, Image img, int x, int h,
                                  float alpha, float rot, boolean flip) {
            if (img == null) return;
            final int iw = img.getWidth(null), ih = img.getHeight(null);
            final int y = h - ih - 4;
            final Graphics2D g = (Graphics2D)g2.create();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.rotate(rot, x + iw / 2.0, y + ih);
            if (flip) g.drawImage(img, x + iw, y, -iw, ih, null);
            else g.drawImage(img, x, y, null);
            g.dispose();
        }
    }
}
