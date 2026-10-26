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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.io.FreeColDirectories;


/**
 * A small web server inside the client that serves the companion
 * page: a live, read-only view of the game for a second monitor.
 *
 * It listens on the local machine only (127.0.0.1), so nothing else
 * on the network can reach it.  The page itself lives in
 * {@code data/companion/} and asks for {@code /api/state} about once
 * a second.  Nothing the page does can change the game.
 */
public final class CompanionServer {

    private static final Logger logger = Logger.getLogger(CompanionServer.class.getName());

    /** How long to wait for the game to answer, in milliseconds. */
    private static final long TIMEOUT = 3000;

    /** The one running server, if any. */
    private static CompanionServer instance = null;

    private final FreeColClient fcc;
    private final CompanionState state;
    private final File root;
    private final HttpServer server;
    private final int port;


    private CompanionServer(FreeColClient fcc, int port) throws IOException {
        this.fcc = fcc;
        this.port = port;
        this.state = new CompanionState(fcc);
        this.root = new File(FreeColDirectories.getDataDirectory(), "companion");
        this.server = HttpServer.create(new InetSocketAddress(
                InetAddress.getLoopbackAddress(), port), 0);
        this.server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "FreeColCompanion");
                    t.setDaemon(true);
                    return t;
                }));
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    /**
     * Start the server if the options ask for it.  Never throws: if
     * the port is taken, the game goes on without the page.
     *
     * @param fcc The {@code FreeColClient}.
     */
    public static synchronized void startIfEnabled(FreeColClient fcc) {
        if (instance != null) return;
        final ClientOptions co = fcc.getClientOptions();
        if (co == null || !co.getBoolean(ClientOptions.COMPANION_PAGE)) return;
        final int port = co.getInteger(ClientOptions.COMPANION_PORT);
        try {
            instance = new CompanionServer(fcc, port);
            logger.info("Companion page at " + getUrl());
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Companion page could not start on port "
                + port, e);
        }
    }

    /** When the page last asked for the game state. */
    private static volatile long lastPoll = 0L;

    /** The page asks every second or so; this long without means closed. */
    private static final long OPEN_MILLIS = 30_000L;

    /**
     * Is the companion page open in a browser right now?
     *
     * @return True if the page asked for the game state recently.
     */
    public static boolean isPageOpen() {
        return System.currentTimeMillis() - lastPoll < OPEN_MILLIS;
    }

    /**
     * Record per-turn figures for the page's history charts, if the
     * page is running.  Called at the start of each turn.
     */
    public static synchronized void recordTurn() {
        if (instance == null) return;
        try {
            CompanionNations.record(instance.fcc);
        } catch (RuntimeException re) {
            logger.log(Level.WARNING, "Nation record failed", re);
        }
        try {
            archiveTurn(instance.fcc);
        } catch (RuntimeException re) {
            logger.log(Level.WARNING, "Archive record failed", re);
        }
    }

    /**
     * Keep this turn in the archive (expansion replay, long charts),
     * and the first time read the game's old autosaves.
     *
     * @param fcc The {@code FreeColClient}.
     */
    private static void archiveTurn(FreeColClient fcc) {
        final net.sf.freecol.common.model.Game game = fcc.getGame();
        final net.sf.freecol.common.model.Player me = fcc.getMyPlayer();
        if (game == null || me == null || game.getMap() == null) return;
        final CompanionArchive a = CompanionArchive.of(game, me);
        java.util.Map<String, net.sf.freecol.common.model.NationSummary> ns
            = new java.util.HashMap<>();
        for (net.sf.freecol.common.model.Player p : game.getLiveEuropeanPlayerList()) {
            final net.sf.freecol.common.model.NationSummary s = me.getNationSummary(p);
            if (s != null) ns.put(p.getNationId(), s);
        }
        a.put(a.frame(game, me, false, ns));
        a.fillFromSaves(game, me, fcc.getClientOptions()
            .getText(ClientOptions.AUTO_SAVE_PREFIX));
    }

    /**
     * Get the address of the page.
     *
     * @return The URL, or null if the server is not running.
     */
    public static synchronized String getUrl() {
        return (instance == null) ? null
            : "http://localhost:" + instance.port + "/";
    }


    /** Run something on the event thread and wait for the answer. */
    private <T> T onEdt(Callable<T> c) throws Exception {
        FutureTask<T> task = new FutureTask<>(c);
        SwingUtilities.invokeLater(task);
        return task.get(TIMEOUT, TimeUnit.MILLISECONDS);
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            final String path = ex.getRequestURI().getPath();
            if (!"GET".equals(ex.getRequestMethod())) {
                send(ex, 405, "text/plain", "Read only".getBytes());
            } else if ("/api/state".equals(path)) {
                lastPoll = System.currentTimeMillis();
                final long since = parseSince(ex.getRequestURI().getRawQuery());
                String json;
                try {
                    json = onEdt(() -> this.state.build(since));
                } catch (Exception e) {
                    // The game is busy, the page asks again shortly.
                    json = "{\"busy\":true}";
                }
                send(ex, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            } else if ("/api/nations".equals(path)) {
                String json;
                try {
                    json = onEdt(() -> (this.fcc.getGame() == null
                            || this.fcc.getMyPlayer() == null) ? "{}"
                        : CompanionNations.build(this.fcc, this.fcc.getGame(),
                                                 this.fcc.getMyPlayer(), true));
                } catch (Exception e) {
                    json = "{}";
                }
                send(ex, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            } else if ("/api/independence".equals(path)) {
                String json;
                try {
                    json = onEdt(() -> (this.fcc.getGame() == null
                            || this.fcc.getMyPlayer() == null) ? "{}"
                        : CompanionIndependence.build(this.fcc.getGame(),
                                                      this.fcc.getMyPlayer()));
                } catch (Exception e) {
                    json = "{}";
                }
                send(ex, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            } else if ("/api/market".equals(path)) {
                String json;
                try {
                    json = onEdt(() -> (this.fcc.getGame() == null
                            || this.fcc.getMyPlayer() == null) ? "{}"
                        : CompanionMarket.build(this.fcc.getGame(),
                                                this.fcc.getMyPlayer()));
                } catch (Exception e) {
                    json = "{}";
                }
                send(ex, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            } else if ("/api/relations".equals(path)) {
                String json;
                try {
                    json = onEdt(() -> (this.fcc.getGame() == null
                            || this.fcc.getMyPlayer() == null) ? "{}"
                        : CompanionRelations.build(this.fcc, this.fcc.getGame(),
                                                   this.fcc.getMyPlayer()));
                } catch (Exception e) {
                    json = "{}";
                }
                send(ex, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            } else if ("/api/colopedia".equals(path)) {
                String json;
                try {
                    json = onEdt(() -> (this.fcc.getGame() == null
                            || this.fcc.getMyPlayer() == null) ? "{}"
                        : CompanionColopedia.build(
                            this.fcc.getGame().getSpecification(),
                            this.fcc.getMyPlayer()));
                } catch (Exception e) {
                    json = "{}";
                }
                send(ex, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            } else if ("/api/offers".equals(path)) {
                String json;
                try {
                    json = onEdt(() -> (this.fcc.getGame() == null
                            || this.fcc.getMyPlayer() == null) ? "{}"
                        : CompanionOffers.build(this.fcc,
                            CompanionOffers.known(this.fcc,
                                this.fcc.getMyPlayer()),
                            CompanionOffers.loans(this.fcc,
                                this.fcc.getMyPlayer()),
                            this.fcc.getGame(), this.fcc.getMyPlayer()));
                } catch (Exception e) {
                    json = "{}";
                }
                send(ex, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            } else if ("/api/expansion".equals(path)) {
                String json;
                try {
                    json = onEdt(() -> (this.fcc.getGame() == null
                            || this.fcc.getMyPlayer() == null) ? "{}"
                        : CompanionArchive.of(this.fcc.getGame(),
                            this.fcc.getMyPlayer()).json(this.fcc.getGame(),
                                parseSince(ex.getRequestURI().getRawQuery()) > 0
                                ? (int)parseSince(ex.getRequestURI().getRawQuery())
                                : -1));
                } catch (Exception e) {
                    json = "{}";
                }
                send(ex, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            } else if ("/api/map".equals(path)) {
                String json;
                try {
                    json = onEdt(this.state::getMapJson);
                } catch (Exception e) {
                    json = "{}";
                }
                send(ex, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            } else if (path.startsWith("/icon/")) {
                // /icon/goods/<id>.png or /icon/unit/<key>.png
                String[] parts = path.split("/");
                byte[] png = null;
                if (parts.length == 4 && parts[3].endsWith(".png")) {
                    final String kind = parts[2];
                    final String key = URLDecoder.decode(parts[3]
                        .substring(0, parts[3].length() - 4), "UTF-8");
                    try {
                        png = onEdt(() -> this.state.getIcon(kind, key));
                    } catch (Exception e) {
                        png = null;
                    }
                }
                if (png == null) {
                    send(ex, 404, "text/plain", "No icon".getBytes());
                } else {
                    ex.getResponseHeaders().add("Cache-Control", "max-age=3600");
                    send(ex, 200, "image/png", png);
                }
            } else {
                serveFile(ex, path);
            }
        } catch (RuntimeException re) {
            logger.log(Level.WARNING, "Companion request failed", re);
            send(ex, 500, "text/plain", "Error".getBytes());
        } finally {
            ex.close();
        }
    }

    private static long parseSince(String query) {
        if (query == null) return 0;
        for (String kv : query.split("&")) {
            if (kv.startsWith("since=")) {
                try {
                    return Long.parseLong(kv.substring(6));
                } catch (NumberFormatException nfe) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private void serveFile(HttpExchange ex, String path) throws IOException {
        if ("/".equals(path)) path = "/index.html";
        // Only plain file names from the companion folder.
        final String name = path.substring(1);
        if (!name.matches("[A-Za-z0-9_-]+\\.(html|js|css|png|svg)")) {
            send(ex, 404, "text/plain", "Not found".getBytes());
            return;
        }
        final File file = new File(this.root, name);
        if (!file.isFile()) {
            send(ex, 404, "text/plain", ("Not found: " + file).getBytes());
            return;
        }
        final String type = (name.endsWith(".html")) ? "text/html; charset=utf-8"
            : (name.endsWith(".js")) ? "text/javascript; charset=utf-8"
            : (name.endsWith(".css")) ? "text/css; charset=utf-8"
            : (name.endsWith(".svg")) ? "image/svg+xml"
            : "image/png";
        ex.getResponseHeaders().add("Cache-Control", "no-cache");
        send(ex, 200, type, Files.readAllBytes(file.toPath()));
    }

    private static void send(HttpExchange ex, int code, String type,
                             byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }
}
