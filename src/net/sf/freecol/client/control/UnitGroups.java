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
package net.sf.freecol.client.control;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;


/**
 * Named groups of units: armies, wagon trains, fleets.
 *
 * A group exists only on the client.  To the game its members remain
 * separate units, so the rules, the AI, save games and multiplayer are
 * untouched.  What a group gives the player is one order for all its
 * members: a destination, a trade route, fortify or sentry.
 *
 * A unit is in at most one group.  The groups of each game are kept
 * in a small file in the user's data directory, named after the
 * game's unique identifier, so they survive saving and loading.
 */
public final class UnitGroups {

    private static final Logger logger = Logger.getLogger(UnitGroups.class.getName());

    private static final String DIRECTORY = "unitgroups";

    /** Unit identifiers by group name, in creation order. */
    private final Map<String, List<String>> groups = new LinkedHashMap<>();

    /** The game the groups were loaded for. */
    private UUID loaded = null;

    /** Where the group files go, or null for the user data directory. */
    private final File directory;


    /**
     * Create the unit groups, kept in the user's data directory.
     */
    public UnitGroups() {
        this(null);
    }

    /**
     * Create the unit groups, kept in a given directory.
     *
     * @param directory The directory for the group files.
     */
    UnitGroups(File directory) {
        this.directory = directory;
    }


    /**
     * Get the file that holds the groups of a game.
     *
     * @param uuid The unique identifier of the game.
     * @return The {@code File}, or null if there is nowhere to put it.
     */
    private File getFile(UUID uuid) {
        if (uuid == null) return null;
        File dir = this.directory;
        if (dir == null) {
            File base = FreeColDirectories.getUserDataDirectory();
            if (base == null) return null;
            dir = new File(base, DIRECTORY);
        }
        return new File(dir, uuid + ".properties");
    }

    /**
     * Make sure the groups in memory are those of the given game.
     *
     * @param game The current {@code Game}.
     */
    private synchronized void sync(Game game) {
        final UUID uuid = (game == null) ? null : game.getUUID();
        if (uuid == null || uuid.equals(this.loaded)) return;
        this.groups.clear();
        this.loaded = uuid;
        final File file = getFile(uuid);
        if (file == null || !file.isFile()) return;
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Could not read " + file, ioe);
            return;
        }
        // Keys are "<index>.<name>" so that creation order is kept.
        Map<Integer, String> order = new TreeMap<>();
        for (String key : props.stringPropertyNames()) {
            int dot = key.indexOf('.');
            if (dot <= 0) continue;
            try {
                order.put(Integer.parseInt(key.substring(0, dot)), key);
            } catch (NumberFormatException nfe) {
                continue;
            }
        }
        for (String key : order.values()) {
            List<String> ids = new ArrayList<>();
            for (String id : props.getProperty(key).split(",")) {
                if (!id.trim().isEmpty()) ids.add(id.trim());
            }
            if (!ids.isEmpty()) {
                this.groups.put(key.substring(key.indexOf('.') + 1), ids);
            }
        }
    }

    /** Write the groups of the current game to their file. */
    private synchronized void save() {
        final File file = getFile(this.loaded);
        if (file == null) return;
        Properties props = new Properties();
        int index = 0;
        for (Map.Entry<String, List<String>> e : this.groups.entrySet()) {
            props.setProperty(index++ + "." + e.getKey(),
                              String.join(",", e.getValue()));
        }
        try {
            File dir = file.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                logger.warning("Could not create " + dir);
                return;
            }
            try (OutputStream out = new FileOutputStream(file)) {
                props.store(out, "FreeCol unit groups");
            }
        } catch (IOException | SecurityException e) {
            logger.log(Level.WARNING, "Could not write " + file, e);
        }
    }

    /**
     * Is a unit still around and still ours?
     *
     * @param unit The {@code Unit} to check, may be null.
     * @param player The owning {@code Player}.
     * @return True if the unit can still be a group member.
     */
    private static boolean isLive(Unit unit, Player player) {
        return unit != null && !unit.isDisposed() && player.owns(unit);
    }


    /**
     * Get the names of all groups.
     *
     * @param game The current {@code Game}.
     * @return A list of group names, in creation order.
     */
    public synchronized List<String> getGroupNames(Game game) {
        sync(game);
        return new ArrayList<>(this.groups.keySet());
    }

    /**
     * Get the name of the group a unit is in.
     *
     * @param unit The {@code Unit} to look up.
     * @return The group name, or null if the unit is in no group.
     */
    public synchronized String getGroupName(Unit unit) {
        if (unit == null) return null;
        sync(unit.getGame());
        final String id = unit.getId();
        for (Map.Entry<String, List<String>> e : this.groups.entrySet()) {
            if (e.getValue().contains(id)) return e.getKey();
        }
        return null;
    }

    /**
     * Get the units in a group.  Units that have been lost since are
     * dropped from the group, and a group that is left with nothing
     * disappears.
     *
     * @param game The current {@code Game}.
     * @param player The {@code Player} who owns the group.
     * @param name The name of the group.
     * @return A list of member {@code Unit}s, empty if there is no
     *     such group.
     */
    public synchronized List<Unit> getMembers(Game game, Player player,
                                              String name) {
        sync(game);
        List<Unit> result = new ArrayList<>();
        final List<String> ids = (name == null) ? null : this.groups.get(name);
        if (ids == null) return result;
        List<String> live = new ArrayList<>();
        for (String id : ids) {
            Unit u = game.getFreeColGameObject(id, Unit.class);
            if (isLive(u, player)) {
                result.add(u);
                live.add(id);
            }
        }
        if (live.size() != ids.size()) {
            if (live.isEmpty()) {
                this.groups.remove(name);
            } else {
                this.groups.put(name, live);
            }
            save();
        }
        return result;
    }

    /**
     * Get the other members of the group a unit is in.
     *
     * @param unit The {@code Unit} to look up.
     * @return A list of the other member {@code Unit}s, empty if the
     *     unit is in no group.
     */
    public List<Unit> getCompanions(Unit unit) {
        final String name = getGroupName(unit);
        if (name == null) return new ArrayList<>();
        List<Unit> result = getMembers(unit.getGame(), unit.getOwner(), name);
        result.remove(unit);
        return result;
    }

    /**
     * Suggest a name for a new group.
     *
     * @param game The current {@code Game}.
     * @param prefix The start of the name, such as "Army".
     * @return A name that is not in use yet.
     */
    public synchronized String suggestName(Game game, String prefix) {
        sync(game);
        for (int i = 1; ; i++) {
            String name = prefix + " " + i;
            if (!this.groups.containsKey(name)) return name;
        }
    }

    /**
     * Put units in a group.  Units leave whatever group they were in.
     * If the group already exists the units are added to it.
     *
     * @param game The current {@code Game}.
     * @param name The name of the group.
     * @param units The {@code Unit}s to put in the group.
     */
    public synchronized void addToGroup(Game game, String name,
                                        List<Unit> units) {
        sync(game);
        if (name == null || units == null || units.isEmpty()) return;
        // Commas and line breaks would confuse the file format.
        name = name.replace(',', ' ').replace('\n', ' ').replace('=', ' ')
            .trim();
        if (name.isEmpty()) return;
        for (Unit u : units) removeQuietly(u.getId());
        List<String> ids = this.groups.computeIfAbsent(name,
            k -> new ArrayList<>());
        for (Unit u : units) ids.add(u.getId());
        save();
    }

    private void removeQuietly(String id) {
        List<String> empty = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : this.groups.entrySet()) {
            if (e.getValue().remove(id) && e.getValue().isEmpty()) {
                empty.add(e.getKey());
            }
        }
        for (String name : empty) this.groups.remove(name);
    }

    /**
     * Take a unit out of its group.
     *
     * @param unit The {@code Unit} to remove.
     * @return The name of the group it was in, or null if none.
     */
    public synchronized String leaveGroup(Unit unit) {
        final String name = getGroupName(unit);
        if (name == null) return null;
        removeQuietly(unit.getId());
        save();
        return name;
    }

    /**
     * Dissolve a group.  Its units are not affected otherwise.
     *
     * @param game The current {@code Game}.
     * @param name The name of the group.
     * @return True if there was such a group.
     */
    public synchronized boolean disband(Game game, String name) {
        sync(game);
        if (this.groups.remove(name) == null) return false;
        save();
        return true;
    }
}
