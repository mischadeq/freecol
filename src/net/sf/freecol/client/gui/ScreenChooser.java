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

package net.sf.freecol.client.gui;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.Preferences;


/**
 * Picks the monitor the game runs on, for set-ups with more than one.
 *
 * FreeCol used to choose a monitor once at start-up and went full
 * screen on that one, even after the window had been moved to another
 * monitor.  This class finds the monitor a window is really on, gives
 * the usable area of a monitor, and remembers the last monitor used so
 * the next start opens there.
 */
public final class ScreenChooser {

    private static final Logger logger = Logger.getLogger(ScreenChooser.class.getName());

    /** Preferences key for the remembered monitor. */
    private static final String KEY = "gameScreen";

    /** The id last written, to avoid writing on every move event. */
    private static String lastRemembered = null;

    private ScreenChooser() {}


    // Pure helpers (unit tested)

    /**
     * Which screen holds the largest part of a window?
     *
     * @param window The window bounds.
     * @param screens The screen bounds.
     * @return The index of the best screen, or -1 if the window is on
     *     none of them.
     */
    public static int bestScreen(Rectangle window, List<Rectangle> screens) {
        int best = -1;
        long bestArea = 0;
        for (int i = 0; i < screens.size(); i++) {
            Rectangle r = window.intersection(screens.get(i));
            long area = (r.isEmpty()) ? 0 : (long)r.width * r.height;
            if (area > bestArea) {
                bestArea = area;
                best = i;
            }
        }
        if (best < 0) { // Off all screens: take the one with the nearest centre
            double cx = window.getCenterX(), cy = window.getCenterY();
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < screens.size(); i++) {
                Rectangle s = screens.get(i);
                double dx = s.getCenterX() - cx, dy = s.getCenterY() - cy;
                double d = dx * dx + dy * dy;
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
        }
        return best;
    }

    /**
     * The usable part of a screen: its bounds minus the taskbar.
     *
     * @param screen The screen bounds.
     * @param insets The screen insets (taskbar etc), may be null.
     * @return The work area.
     */
    public static Rectangle workArea(Rectangle screen, Insets insets) {
        if (insets == null) return new Rectangle(screen);
        return new Rectangle(screen.x + insets.left, screen.y + insets.top,
            screen.width - insets.left - insets.right,
            screen.height - insets.top - insets.bottom);
    }

    /**
     * Place a window size on a screen, centred, keeping it inside.
     *
     * @param size The wanted window bounds (only the size is used
     *     when the location is 0,0).
     * @param area The work area of the screen.
     * @return The placed bounds.
     */
    public static Rectangle placeOn(Rectangle size, Rectangle area) {
        if (area.contains(size)) return new Rectangle(size);
        int w = Math.min(size.width, area.width);
        int h = Math.min(size.height, area.height);
        return new Rectangle(area.x + (area.width - w) / 2,
                             area.y + (area.height - h) / 2, w, h);
    }


    // Device helpers

    private static GraphicsDevice[] devices() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getScreenDevices();
        } catch (HeadlessException he) {
            return new GraphicsDevice[0];
        }
    }

    /**
     * The usable area of a monitor (without the taskbar).
     *
     * @param gd The {@code GraphicsDevice}.
     * @return The work area.
     */
    public static Rectangle workArea(GraphicsDevice gd) {
        Insets insets = null;
        try {
            insets = Toolkit.getDefaultToolkit()
                .getScreenInsets(gd.getDefaultConfiguration());
        } catch (HeadlessException he) {}
        return workArea(gd.getDefaultConfiguration().getBounds(), insets);
    }

    /**
     * The monitor holding most of a window.
     *
     * @param w The {@code Window}.
     * @return The device, or null if unknown.
     */
    public static GraphicsDevice deviceFor(Window w) {
        if (w == null || !w.isShowing()) {
            return (w == null || w.getGraphicsConfiguration() == null) ? null
                : w.getGraphicsConfiguration().getDevice();
        }
        GraphicsDevice[] gds = devices();
        List<Rectangle> screens = new ArrayList<>();
        for (GraphicsDevice gd : gds) {
            screens.add(gd.getDefaultConfiguration().getBounds());
        }
        int i = bestScreen(w.getBounds(), screens);
        return (i < 0) ? null : gds[i];
    }

    /**
     * Remember a monitor for the next start.
     *
     * @param gd The {@code GraphicsDevice} to remember.
     */
    public static void remember(GraphicsDevice gd) {
        if (gd == null) return;
        String id = gd.getIDstring();
        if (id == null || id.equals(lastRemembered)) return;
        lastRemembered = id;
        try {
            Preferences.userNodeForPackage(ScreenChooser.class).put(KEY, id);
        } catch (Exception e) {
            logger.fine("Could not remember monitor: " + e.getMessage());
        }
    }

    /**
     * The monitor remembered from last time, if it is still there.
     *
     * @return The device, or null.
     */
    public static GraphicsDevice rememberedDevice() {
        String id;
        try {
            id = Preferences.userNodeForPackage(ScreenChooser.class)
                .get(KEY, null);
        } catch (Exception e) {
            return null;
        }
        if (id == null) return null;
        for (GraphicsDevice gd : devices()) {
            if (id.equals(gd.getIDstring())) {
                lastRemembered = id;
                return gd;
            }
        }
        return null;
    }
}
