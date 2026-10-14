/*
 * FreeCol companion page.  Read-only: it asks the game for its state
 * once a second and draws it.  Nothing here can change the game.
 */
(function () {
  "use strict";

  const POLL_MS = 1000;
  const $ = (id) => document.getElementById(id);

  // ---------------------------------------------------------------- state

  let state = null;          // Latest /api/state
  let mapData = null;        // Latest /api/map
  let mapRev = null;
  let session = null;
  let lastLogId = 0;
  const seenLog = new Set();   // Log entry ids already shown
  let unread = 0;

  // Map view: S = pixels per tile width, (ox, oy) = screen offset.
  const view = { S: 20, ox: 0, oy: 0, auto: true };
  let terrain = null;        // Offscreen canvas with the terrain
  let terrainT = 24;         // Its tile width in pixels
  const anims = {};          // Unit id -> {pts, t0, dur}
  let lastUnits = {};        // Unit id -> unit, from the previous state
  let pulses = [];           // Highlights: {x, y, t0}
  let hits = [];             // Things under the mouse: {x, y, r, html, colony}
  const icons = {};          // URL -> Image

  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* ignore */ } }
  };

  // ---------------------------------------------------------------- helpers

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    }[c]));
  }
  function goodsUrl(id) { return "/icon/goods/" + encodeURIComponent(id) + ".png"; }
  function unitUrl(key) { return "/icon/unit/" + encodeURIComponent(key) + ".png"; }
  function gi(id, title) {
    return '<img class="gi" data-g="' + esc(id) + '" src="' + goodsUrl(id) + '" alt="" title="' +
      esc(title || goodsName(id)) + '">';
  }
  function goodsName(id) {
    if (state && state.goods) {
      for (const g of state.goods) if (g.g === id) return g.name;
    }
    return id.replace(/^model\.goods\./, "");
  }
  function image(url) {
    let img = icons[url];
    if (!img) {
      img = new Image();
      img.src = url;
      icons[url] = img;
    }
    return img.complete && img.naturalWidth > 0 ? img : null;
  }
  function turns(n) { return n === 1 ? "1 turn" : n + " turns"; }
  function signed(n) { return (n > 0 ? "+" : "") + n; }

  // Tile centre in tile-width units.
  function cx(x, y) { return x + (y & 1) * 0.5 + 0.5; }
  function cy(x, y) { return y / 4 + 0.25; }
  function sx(x, y) { return cx(x, y) * view.S + view.ox; }
  function sy(x, y) { return cy(x, y) * view.S + view.oy; }

  // ---------------------------------------------------------------- polling

  async function poll() {
    try {
      const r = await fetch("/api/state?since=" + lastLogId, { cache: "no-store" });
      if (!r.ok) throw new Error(r.status);
      const s = await r.json();
      if (s.busy) { schedule(); return; }
      if (s.session !== session) {
        // FreeCol was restarted: start the log afresh.
        session = s.session;
        lastLogId = 0;
        seenLog.clear();
        $("logList").innerHTML = "";
        if (state !== null) { schedule(0); return; }
      }
      online(s);
      if (s.inGame) {
        if (s.mapRev !== mapRev) await loadMap(s.mapRev);
        update(s);
      }
      addLog(s.log || []);
    } catch (e) {
      offline("Waiting for FreeCol",
        "Start FreeCol on this computer. This page connects by itself.");
    }
    schedule();
  }
  function schedule(ms) { setTimeout(poll, ms == null ? POLL_MS : ms); }

  function online(s) {
    if (!s.inGame) {
      offline("No game loaded",
        "FreeCol is running. Start or load a game and this page fills in.");
    } else {
      $("offline").hidden = true;
    }
  }
  function offline(title, text) {
    $("offlineTitle").textContent = title;
    $("offlineText").textContent = text;
    $("offline").hidden = false;
  }

  async function loadMap(rev) {
    const r = await fetch("/api/map", { cache: "no-store" });
    const m = await r.json();
    if (!m.tiles) return;
    const first = mapData === null;
    mapData = m;
    mapRev = rev;
    drawTerrain();
    if (first) view.auto = true;
  }

  // ---------------------------------------------------------------- update

  let lastColoniesJson = "", lastGoodsJson = "", lastAlertsJson = "";

  function update(s) {
    const prev = state;
    state = s;

    // Header
    $("date").textContent = s.date + "  ·  Turn " + s.turn;
    $("nation").textContent = s.nation;
    $("swatch").style.background = s.color;
    $("gold").textContent = s.gold.toLocaleString();
    $("tax").textContent = s.tax + "%";
    const nAl = s.alerts.filter((a) => a.lvl !== "info").length;
    $("nAlerts").textContent = nAl;
    $("chipAlerts").classList.toggle("hot", nAl > 0);
    $("nPending").textContent = s.pending;
    $("chipPending").classList.toggle("hot", s.pending > 0);
    $("nIdle").textContent = s.idle;
    $("turnState").textContent = s.myTurn ? "Your turn" : "Other players' turn";

    startAnimations(prev, s);
    if (!$("log").hidden && drawerTab === "rel") loadRelations(false);
    // New turn: prices and Congress change, refresh an open Colopedia.
    if (pedia && pediaTurn !== s.turn && !$("pedia").hidden && !$("log").hidden) renderPedia();

    const cj = JSON.stringify(s.colonies);
    if (cj !== lastColoniesJson) { lastColoniesJson = cj; renderColonies(s.colonies); }
    const gj = JSON.stringify(s.goods);
    if (gj !== lastGoodsJson) { lastGoodsJson = gj; renderGoods(s.goods); }
    const aj = JSON.stringify(s.alerts);
    if (aj !== lastAlertsJson) { lastAlertsJson = aj; renderAlerts(s.alerts); }
    renderLegend(s.routes);
    renderRoutes(s.routes);
    if (s.marketRev !== marketRev) { marketRev = s.marketRev; loadMarket(); }
    // The nation figures need the server, so only ask during our turn.
    if (s.turn !== natTurn && s.myTurn) loadNations(s.turn);
    if (shownWidgets().includes("expansion") && (s.turn !== expTurn || (exp && exp.reading))) loadExpansion(s.turn);
    if (shownWidgets().includes("offers") && (s.turn !== offTurn || Date.now() - offAt > 10000)) loadOffers(s.turn);
    if (shownWidgets().includes("independence") && s.myTurn &&
        (s.turn !== indepTurn || Date.now() - indepAt > 10000)) loadIndependence(s.turn);
    if (view.auto) fitView();
  }

  // ---------------------------------------------------------------- alerts

  let showAllAlerts = false;
  const ALERTS_SHOWN = 8;

  function renderAlerts(alerts) {
    const ul = $("alerts");
    if (!alerts.length) {
      ul.innerHTML = '<li class="none">All quiet. Nothing needs your attention.</li>';
      return;
    }
    const list = showAllAlerts ? alerts : alerts.slice(0, ALERTS_SHOWN);
    ul.innerHTML = list.map((a, i) =>
      '<li class="' + a.lvl + (a.x >= 0 ? " link" : "") + '" data-i="' + i + '">' +
      '<span class="dot ' + a.lvl + '"></span>' +
      (a.g ? gi(a.g) : "") + '<span>' + esc(a.text) + '</span></li>').join("") +
      (alerts.length > ALERTS_SHOWN
        ? '<li class="none" style="border:0;background:none;padding:0"><button class="more" id="moreAlerts">' +
          (showAllAlerts ? "Show fewer" : "Show all " + alerts.length) + '</button></li>'
        : "");
    ul.querySelectorAll("li.link").forEach((li) => {
      li.addEventListener("click", () => {
        const a = list[+li.dataset.i];
        pulse(a.x, a.y);
        flashColonyAt(a.x, a.y);
      });
    });
    const more = $("moreAlerts");
    if (more) more.addEventListener("click", () => {
      showAllAlerts = !showAllAlerts;
      renderAlerts(alerts);
    });
  }

  // ---------------------------------------------------------------- colonies

  function renderColonies(colonies) {
    const box = $("colonies");
    if (!colonies.length) {
      box.innerHTML = '<p class="m">No colonies yet.</p>';
      return;
    }
    box.innerHTML = colonies.map(card).join("");
    renderNetwork(colonies);
    box.querySelectorAll(".card h3").forEach((h) => {
      h.addEventListener("click", () => {
        const c = colonies.find((k) => k.id === h.parentNode.dataset.id);
        if (c) { pulse(c.x, c.y); centreOn(c.x, c.y); }
      });
    });
  }

  function card(c) {
    const produces = c.produces.length
      ? c.produces.map((p, i) => '<span class="it">' + gi(p[0]) +
          (i === 0 ? esc(goodsName(p[0])) + " " : "") + signed(p[1]) + "</span>").join("")
      : '<span class="m">-</span>';
    const needs = c.needs.length
      ? c.needs.map((n) => '<span class="it ' + n.lvl + '" title="' + esc(n.tip) + '">' +
          gi(n.g, n.tip) + (n.t >= 0 ? '<span class="t">' + n.t + "t</span>" : "") +
          "</span>").join("")
      : '<span class="m">-</span>';
    const stock = c.stock.length
      ? '<div class="stock">' + c.stock.map((s) => {
          const pct = s.free ? 100 : Math.min(100, Math.round(100 * s.n / c.cap));
          return '<div class="bar ' + s.lvl + '" title="' + esc(goodsName(s.g)) + ": " +
            s.n + (s.free ? "" : " of " + c.cap) + '">' + gi(s.g) +
            '<div class="track"><div class="fill" style="width:' + pct + '%"></div></div>' +
            '<span class="n">' + s.n + "</span></div>";
        }).join("") + "</div>"
      : '<span class="m">empty</span>';
    const b = c.build;
    const build = '<div class="build ' + b.lvl + '"><div><span class="name">' + esc(b.name) +
      '</span>' + (b.text ? ' <span class="txt">· ' + esc(b.text) + "</span>" : "") + "</div>" +
      (b.pct >= 0 ? '<div class="track"><div class="fill" style="width:' + b.pct + '%"></div></div>' : "") +
      "</div>";
    const incoming = c.incoming.length
      ? c.incoming.map((d) => '<span class="it" title="' + esc(d.unit + ", " + d.route) + '">' +
          gi(d.g) + d.n + ' <span class="m">in ' + turns(d.t) + "</span></span>").join("")
      : "";
    const g = c.gov;
    let liberty = "";
    if (g) {
      // One pip per colonist: rebels gold, tories grey, tories past the
      // limit red; at the limit an empty red pip marks the next one.
      let pips = "";
      for (let i = 0; i < g.rebels; i++) pips += '<i class="pip reb"></i>';
      for (let i = 0; i < g.tories; i++) pips += '<i class="pip ' + (i >= g.limit ? "bad" : "tor") + '"></i>';
      if (g.tories === g.limit && g.sol < g.good) pips += '<i class="pip edge" title="One more tory: −1 on all production"></i>';
      const gain = g.made - g.use;
      const toGood = (c.advice || []).find((a) => a.kind === "toGood");
      liberty = '<div class="liberty" title="' + esc((toGood ? toGood.text + " " : "") +
          g.rebels + " rebels, " + g.tories + " tories; " + g.limit + " tories allowed") + '">' +
        '<span class="pips">' + pips + "</span>" +
        '<span class="bells ' + (gain > 0 ? "ok" : "bad") + '">' + gi("model.goods.bells", "Bells") +
        g.made + '<span class="m">/' + g.use + "</span></span>" +
        (g.toGood > 0 ? '<span class="m">' + g.toGood + " to " + g.good + "%</span>" : "") + "</div>";
    }
    const chips = (c.advice || []).filter((a) => a.brief).map((a) =>
      '<span class="chip2 ' + a.lvl + '" title="' + esc(a.text) + '">' + (a.g ? gi(a.g) : "") +
      esc(a.brief) + "</span>").join("");
    return '<div class="card" data-id="' + esc(c.id) + '">' +
      '<h3>' + esc(c.name) + ' <span class="m">' + c.size + ' colonists · store ' + c.cap +
      '</span><span class="sol" title="Sons of Liberty">SoL ' + c.sol + '%</span></h3>' +
      '<div class="grid">' +
      '<span class="k">Makes</span><div class="items">' + produces + "</div>" +
      '<span class="k">Needs</span><div class="items">' + needs + "</div>" +
      '<span class="k">Stock</span>' + stock +
      '<span class="k">Building</span>' + build +
      (incoming ? '<span class="k">Arriving</span><div class="items">' + incoming + "</div>" : "") +
      (liberty ? '<span class="k">Liberty</span>' + liberty : "") +
      (chips ? '<span class="k">Advice</span><div class="chips">' + chips + "</div>" : "") +
      "</div></div>";
  }

  // ---------------------------------------------------------------- network

  // Colonies placed as on the map, arrows for what trade routes carry
  // between them, coloured by whether the supplier keeps up.
  const NET_C = { ok: "#7d8794", none: "#f2a93b", short: "#ff5d61" };
  const NET_RANK = { ok: 0, none: 1, short: 2 };

  function renderNetwork(colonies) {
    const svg = $("net");
    const W = svg.getBoundingClientRect().width || 700;
    const nodes = {}, edges = {};
    colonies.forEach((c) => { nodes[c.id] = { c, px: cx(c.x, c.y), py: cy(c.x, c.y) * 2, r: 15 + Math.min(12, c.size) }; });
    // Places outside the colonies (Europe) sit in a column on the right.
    const outside = [];
    colonies.forEach((c) => (c.sends || []).forEach((d) => {
      const to = d.toId || ("@" + d.to);
      if (!nodes[to] && !outside.includes(to)) outside.push(to);
      const k = c.id + ">" + to;
      (edges[k] = edges[k] || { from: c.id, to, goods: [] }).goods.push(d);
    }));
    const ids = Object.keys(nodes);
    $("netSec").hidden = !Object.keys(edges).length;
    if (!Object.keys(edges).length) return;
    // Fit the map positions into the box, same scale both ways.
    const xs = ids.map((i) => nodes[i].px), ys = ids.map((i) => nodes[i].py);
    const x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys), y1 = Math.max(...ys);
    // Each axis stretched on its own: the layout keeps north, south,
    // east and west, and uses the whole box.
    const padX = 110, padY = 46, right = outside.length ? 150 : 0;
    const H = Math.max(260, Math.min(440, 140 + ids.length * 50));
    const bw = W - 2 * padX - right, bh = H - 2 * padY;
    const place = (v, a, b, size) => (b - a < 0.3) ? size / 2 : (v - a) / (b - a) * size;
    ids.forEach((i) => {
      const n = nodes[i];
      n.X = padX + place(n.px, x0, x1, bw);
      n.Y = padY + place(n.py, y0, y1, bh);
    });
    outside.forEach((o, k) => {
      nodes[o] = { name: o.slice(1), X: W - 70, Y: padY + (k + 0.5) * (H - 2 * padY) / outside.length, r: 18, outside: true };
    });
    svg.setAttribute("viewBox", "0 0 " + W + " " + H);
    svg.style.height = H + "px";

    let s = '<defs>' + Object.keys(NET_C).map((st) =>
      '<marker id="ah-' + st + '" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">' +
      '<path d="M0,0 L10,5 L0,10 z" fill="' + NET_C[st] + '"/></marker>').join("") + "</defs>";
    const labels = [];
    for (const k of Object.keys(edges)) {
      const e = edges[k], a = nodes[e.from], b = nodes[e.to];
      const st = e.goods.reduce((w, d) => NET_RANK[d.st] > NET_RANK[w] ? d.st : w, "ok");
      const dx = b.X - a.X, dy = b.Y - a.Y, len = Math.hypot(dx, dy) || 1;
      const ux = dx / len, uy = dy / len;
      // Bend every arrow to its left, so A→B and B→A do not overlap.
      const bend = Math.min(60, len * 0.22);
      const mx = (a.X + b.X) / 2 - uy * bend, my = (a.Y + b.Y) / 2 + ux * bend;
      const sx = a.X + ux * a.r, sy = a.Y + uy * a.r, ex = b.X - ux * (b.r + 3), ey = b.Y - uy * (b.r + 3);
      s += '<path d="M' + sx.toFixed(1) + "," + sy.toFixed(1) + " Q" + mx.toFixed(1) + "," + my.toFixed(1) + " " +
        ex.toFixed(1) + "," + ey.toFixed(1) + '" fill="none" stroke="' + NET_C[st] + '" stroke-width="' + (st === "ok" ? 2 : 2.5) +
        '"' + (st === "none" ? ' stroke-dasharray="6 4"' : "") + ' marker-end="url(#ah-' + st + ')"/>';
      // Label on the curve's middle: an icon per goods, the shortfall it covers.
      const lx = 0.25 * sx + 0.5 * mx + 0.25 * ex, ly = 0.25 * sy + 0.5 * my + 0.25 * ey;
      labels.push({ x: lx, y: ly, e, nx: -uy, ny: ux });
    }
    // Labels must not cover each other or a colony: push each one
    // outwards, away from its arrow's other side, until it is clear.
    const placed = Object.keys(nodes).map((i) => ({ x: nodes[i].X - nodes[i].r, y: nodes[i].Y - nodes[i].r,
      w: 2 * nodes[i].r, h: 2 * nodes[i].r }));
    const hit = (r) => placed.some((p) => r.x < p.x + p.w && p.x < r.x + r.w && r.y < p.y + p.h && p.y < r.y + r.h);
    for (const l of labels) {
      l.items = l.e.goods.map((d) => ({ d, w: 20 + (d.need > 0 ? 26 : 0) }));
      l.w = l.items.reduce((t, i) => t + i.w, 0) + 6;
      for (let k = 0; k < 20; k++) {
        const r = { x: l.x - l.w / 2 - 3, y: l.y - 15, w: l.w + 6, h: 30 };
        if (!hit(r)) { placed.push(r); break; }
        l.x += l.nx * 8; l.y += l.ny * 8;
      }
    }
    for (const l of labels) {
      const items = l.items, w = l.w;
      let x = l.x - w / 2 + 3;
      s += '<rect x="' + (l.x - w / 2) + '" y="' + (l.y - 12) + '" width="' + w + '" height="24" rx="12" fill="#1b2027" stroke="#2f3742"/>';
      for (const it of items) {
        const d = it.d;
        s += '<g class="netg"><title>' + esc(goodsName(d.g) + " → " + d.to + " (" + d.route + ")" +
            (d.need > 0 ? ": " + d.to + " lacks " + d.need + " a turn" : "") +
            (d.st === "short" ? "; the supplier makes too little" : d.st === "none" ? "; nothing to send" : "")) + "</title>" +
          '<image href="' + goodsUrl(d.g) + '" x="' + x + '" y="' + (l.y - 9) + '" width="18" height="18"/>' +
          (d.need > 0 ? '<text x="' + (x + 20) + '" y="' + (l.y + 4) + '" font-size="11" fill="' +
            (d.st === "ok" ? "#e8e3d8" : NET_C[d.st]) + '">' + d.need + "/t</text>" : "") + "</g>";
        x += it.w;
      }
    }
    // Nodes on top.
    for (const id of Object.keys(nodes)) {
      const n = nodes[id];
      if (n.outside) {
        s += '<rect x="' + (n.X - 34) + '" y="' + (n.Y - 14) + '" width="68" height="28" rx="6" fill="#222832" stroke="#5a6573"/>' +
          '<text x="' + n.X + '" y="' + (n.Y + 4) + '" text-anchor="middle" font-size="12" fill="#e8e3d8">' + esc(n.name) + "</text>";
        continue;
      }
      const c = n.c, g = c.gov || { sol: 0, tories: 0, limit: 99, bonus: 0 };
      const ring = g.bonus < 0 ? "#ff5d61" : (g.tories >= g.limit && g.sol < (g.good || 50)) ? "#f2a93b" : "#5a6573";
      // Rebels as a gold arc around the node.
      const arc = (pct) => {
        if (pct <= 0) return "";
        if (pct >= 100) return '<circle cx="' + n.X + '" cy="' + n.Y + '" r="' + (n.r + 4) + '" fill="none" stroke="#e0ac48" stroke-width="3"/>';
        const a1 = -Math.PI / 2 + 2 * Math.PI * pct / 100, R = n.r + 4;
        return '<path d="M' + n.X + "," + (n.Y - R) + " A" + R + "," + R + " 0 " + (pct > 50 ? 1 : 0) + " 1 " +
          (n.X + R * Math.cos(a1)).toFixed(1) + "," + (n.Y + R * Math.sin(a1)).toFixed(1) + '" fill="none" stroke="#e0ac48" stroke-width="3"/>';
      };
      const stuck = (c.advice || []).filter((a) => a.kind === "deadEnd");
      s += '<g class="netnode" data-x="' + c.x + '" data-y="' + c.y + '"><title>' + esc(c.name + ": " + c.size +
          " colonists, rebels " + g.sol + "%" + (g.bonus < 0 ? ", tory penalty" : "")) + "</title>" +
        '<circle cx="' + n.X + '" cy="' + n.Y + '" r="' + n.r + '" fill="#222832" stroke="' + ring + '" stroke-width="2"/>' +
        arc(g.sol) +
        '<text x="' + n.X + '" y="' + (n.Y + 5) + '" text-anchor="middle" font-size="14" font-weight="600" fill="#e8e3d8">' + c.size + "</text>" +
        '<text x="' + (n.X + n.r + 8) + '" y="' + (n.Y + 5) + '" font-size="13" fill="#e8e3d8">' + esc(c.name) + "</text>";
      // Goods that arrive and go no further pile up beside the node.
      stuck.forEach((a, k) => {
        const bx = n.X + n.r + 8 + k * 22, by = n.Y + 12;
        s += '<g><title>' + esc(a.text) + '</title><rect x="' + (bx - 2) + '" y="' + (by - 2) + '" width="22" height="22" rx="4" fill="#2b2216" stroke="#f2a93b"/>' +
          '<image href="' + goodsUrl(a.g) + '" x="' + bx + '" y="' + by + '" width="18" height="18"/></g>';
      });
      s += "</g>";
    }
    svg.innerHTML = s;
    svg.querySelectorAll(".netnode").forEach((el) => el.addEventListener("click", () => {
      const x = +el.dataset.x, y = +el.dataset.y;
      pulse(x, y); centreOn(x, y); flashColonyAt(x, y);
    }));
  }

  function flashColonyAt(x, y) {
    if (!state) return;
    const c = state.colonies.find((k) => k.x === x && k.y === y);
    if (!c) return;
    const el = document.querySelector('.card[data-id="' + CSS.escape(c.id) + '"]');
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "nearest" });
    el.classList.add("flash");
    setTimeout(() => el.classList.remove("flash"), 1800);
  }

  // ---------------------------------------------------------------- goods

  function renderGoods(goods) {
    $("goods").innerHTML = goods.length ? goods.map((g) =>
      '<div class="grow">' + gi(g.g) + '<span>' + esc(g.name) + '</span>' +
      '<span class="num">' + g.total + '</span>' +
      '<span class="num ' + (g.net > 0 ? "up" : g.net < 0 ? "down" : "m") + '">' +
      signed(g.net) + "</span>" + spark(g.hist) + "</div>").join("")
      : '<p class="m">Nothing in store yet.</p>';
  }

  function spark(h) {
    const W = 110, H = 22;
    if (!h || h.length < 2) {
      return '<svg viewBox="0 0 ' + W + " " + H + '"><title>The trend line starts after the next turn</title>' +
        '<line x1="2" y1="11" x2="' + (W - 2) + '" y2="11" stroke="#3a434f" stroke-dasharray="3 3"/></svg>';
    }
    const max = Math.max(...h), min = Math.min(...h), span = Math.max(1, max - min);
    const pts = h.map((v, i) => (i * (W - 4) / (h.length - 1) + 2).toFixed(1) + "," +
      (H - 3 - (v - min) * (H - 6) / span).toFixed(1)).join(" ");
    const up = h[h.length - 1] >= h[0];
    return '<svg viewBox="0 0 ' + W + " " + H + '"><polyline fill="none" stroke="' +
      (up ? "#49c487" : "#ff5d61") + '" stroke-width="1.8" points="' + pts + '"/></svg>';
  }

  // ---------------------------------------------------------------- market

  const SELL_C = "#b5832a", BUY_C = "#5a93e6"; // Validated for the dark surface
  let market = null, marketRev = null, selG = store.get("fc.marketGoods");

  async function loadMarket() {
    try {
      const r = await fetch("/api/market", { cache: "no-store" });
      const m = await r.json();
      if (!m.goods) return;
      market = m;
      renderMarket();
    } catch (e) { /* Try again with the next change */ }
  }

  function turnLabel(t) {
    const y = market && market.turns[String(t)];
    return y ? y + " (turn " + t + ")" : "turn " + t;
  }

  function renderMarket() {
    const m = market;
    if (!m.goods.length) {
      $("marketSummary").innerHTML = '<span class="m">No prices yet.</span>';
      return;
    }
    $("marketSummary").innerHTML =
      '<span><span class="lbl">Tax</span> <b>' + m.tax + "%</b> <span class=\"m\">on sales only</span></span>" +
      '<span><span class="lbl">Taxes paid</span> <b>' + m.taxesPaid.toLocaleString() + "</b></span>" +
      '<span><span class="lbl">Trade balance</span> <b class="' + (m.balance >= 0 ? "up" : "down") + '">' +
      signed(m.balance) + '</b> <span class="m">sales after tax minus purchases</span></span>';
    if (!selG || !m.goods.some((g) => g.g === selG)) {
      const best = m.goods.slice().sort((a, b) => Math.abs(b.sales) - Math.abs(a.sales))[0];
      selG = best.g;
    }
    // Table, also the accessible view of the chart.
    $("marketTable").innerHTML =
      '<div class="mrow mh"><span></span><span>Goods</span><span class="num">Sell</span><span class="num">Buy</span>' +
      '<span class="num" title="Change in the sell price over the last 10 turns">10-turn</span>' +
      '<span class="num" title="Net amount you sold (negative: bought)">You sold</span><span class="num">Tax paid</span></div>' +
      m.goods.map((g) => {
        const old = g.hist.filter((h) => h[0] >= m.turn - 10)[0] || g.hist[0];
        const d = g.sell - old[2];
        return '<div class="mrow' + (g.g === selG ? " sel" : "") + '" data-g2="' + esc(g.g) + '">' + gi(g.g) +
          "<span>" + esc(g.name) + (g.boycott ? ' <span class="tag alarm">boycott</span>' : "") + "</span>" +
          '<span class="num">' + g.sell + '</span><span class="num">' + g.buy + "</span>" +
          '<span class="num ' + (d > 0 ? "up" : d < 0 ? "down" : "m") + '">' + (d === 0 ? "\u2013" : signed(d)) + "</span>" +
          '<span class="num">' + (g.sales || '<span class="m">0</span>') + '</span><span class="num">' +
          (g.taxes || '<span class="m">0</span>') + "</span></div>";
      }).join("");
    $("marketTable").querySelectorAll(".mrow[data-g2]").forEach((el) => el.addEventListener("click", () => {
      selG = el.dataset.g2;
      store.set("fc.marketGoods", selG);
      renderMarket();
    }));
    drawPriceChart(m.goods.find((g) => g.g === selG));
  }

  function drawPriceChart(g) {
    const svg = $("mchart");
    $("mchartHead").innerHTML = gi(g.g) + " <b>" + esc(g.name) + "</b>" +
      '<span class="mleg"><i style="background:' + SELL_C + '"></i>You sell for</span>' +
      '<span class="mleg"><i class="dash" style="border-color:' + BUY_C + '"></i>You buy for</span>';
    const W = svg.getBoundingClientRect().width || 600, H = 220;
    const pad = { l: 34, r: 74, t: 12, b: 26 };
    svg.setAttribute("viewBox", "0 0 " + W + " " + H);
    svg.setAttribute("aria-label", "Price of " + g.name + " in Europe by turn");
    const h = g.hist;
    const t0 = h[0][0], t1 = Math.max(h[h.length - 1][0], market.turn, t0 + 1);
    const ymax = Math.max(4, ...h.map((p) => Math.max(p[1], p[2]))) * 1.15;
    const X = (t) => pad.l + (t - t0) / (t1 - t0) * (W - pad.l - pad.r);
    const Y = (v) => H - pad.b - v / ymax * (H - pad.t - pad.b);
    // A price holds until it changes: draw steps, extended to this turn.
    const step = (i) => {
      let d = "";
      h.forEach((p, k) => {
        d += (k === 0 ? "M" : "H" + X(p[0]).toFixed(1) + "V") + (k === 0 ? X(p[0]).toFixed(1) + "," : "") + Y(p[i]).toFixed(1);
      });
      return d + "H" + X(t1).toFixed(1);
    };
    let s = "";
    // Round tick steps: 1, 2 or 5 times a power of ten.
    const raw = ymax / 4, mag = Math.pow(10, Math.floor(Math.log10(raw)));
    const stepV = [1, 2, 5, 10].map((f) => f * mag).find((v) => v >= raw);
    for (let v = 0; v <= ymax; v += stepV) {
      s += '<line x1="' + pad.l + '" x2="' + (W - pad.r) + '" y1="' + Y(v) + '" y2="' + Y(v) + '" stroke="#2f3742" stroke-width="1"/>' +
        '<text x="' + (pad.l - 6) + '" y="' + (Y(v) + 4) + '" text-anchor="end" fill="#98a2ad" font-size="11">' + v + "</text>";
    }
    const xt = Math.min(6, t1 - t0);
    for (let k = 0; k <= xt; k++) {
      const t = Math.round(t0 + (t1 - t0) * k / xt);
      const lab = (market.turns[String(t)] || "turn " + t);
      s += '<text x="' + X(t) + '" y="' + (H - 8) + '" text-anchor="middle" fill="#98a2ad" font-size="11">' + esc(lab) + "</text>";
    }
    s += '<path d="' + step(1) + '" fill="none" stroke="' + BUY_C + '" stroke-width="2" stroke-dasharray="6 4"/>';
    s += '<path d="' + step(2) + '" fill="none" stroke="' + SELL_C + '" stroke-width="2"/>';
    // Direct labels at the right end.
    const last = h[h.length - 1];
    let yb = Y(last[1]), ys = Y(last[2]);
    if (Math.abs(yb - ys) < 14) { if (yb < ys) yb = ys - 14; else ys = yb - 14; }
    s += '<text x="' + (X(t1) + 6) + '" y="' + (ys + 4) + '" fill="#e8e3d8" font-size="12">sell ' + last[2] + "</text>";
    s += '<text x="' + (X(t1) + 6) + '" y="' + (yb + 4) + '" fill="#e8e3d8" font-size="12">buy ' + last[1] + "</text>";
    s += '<line id="mcross" x1="0" x2="0" y1="' + pad.t + '" y2="' + (H - pad.b) + '" stroke="#98a2ad" stroke-width="1" visibility="hidden"/>';
    s += '<rect x="' + pad.l + '" y="' + pad.t + '" width="' + (W - pad.l - pad.r) + '" height="' + (H - pad.t - pad.b) +
      '" fill="transparent" id="mhit"/>';
    if (h.length === 1) {
      s += '<text x="' + ((pad.l + W - pad.r) / 2) + '" y="' + (pad.t + 16) + '" text-anchor="middle" fill="#98a2ad" font-size="12">' +
        "History builds up turn by turn while you play</text>";
    }
    svg.innerHTML = s;
    const hit = svg.querySelector("#mhit"), cross = svg.querySelector("#mcross"), tip = $("mtip");
    hit.addEventListener("mousemove", (e) => {
      const r = svg.getBoundingClientRect();
      const mx = (e.clientX - r.left) * W / r.width;
      const t = Math.round(t0 + (mx - pad.l) / (W - pad.l - pad.r) * (t1 - t0));
      let p = h[0];
      for (const q of h) if (q[0] <= t) p = q;
      cross.setAttribute("x1", X(t)); cross.setAttribute("x2", X(t));
      cross.setAttribute("visibility", "visible");
      tip.innerHTML = "<b>" + esc(turnLabel(t)) + '</b><div class="row">Sell ' + p[2] + " \u00b7 buy " + p[1] + "</div>";
      tip.hidden = false;
      tip.style.left = Math.min(r.width - tip.offsetWidth - 4, (e.clientX - r.left) + 12) + "px";
      tip.style.top = "8px";
    });
    hit.addEventListener("mouseleave", () => { tip.hidden = true; cross.setAttribute("visibility", "hidden"); });
  }

  // ---------------------------------------------------------------- nations

  // Columns in display order.  low: a lower value is better.
  const NAT_COLS = [
    { m: "colonies",  grp: "Expansion", label: "Colonies", tip: "Number of colonies" },
    { m: "colonists", grp: "Expansion", label: "Colonists", tip: "Colonists working in their colonies, counted on the map" },
    { m: "avgSize",   grp: "Expansion", label: "Per colony", tip: "Average colonists per colony", div: 10 },
    { m: "units",     grp: "Expansion", label: "Units", tip: "All units: colonists in and outside colonies, soldiers, ships, wagons" },
    { m: "output",    grp: "Economy",   label: "Output", tip: "Gold per turn their colonies make beyond what they use: yours exact, theirs estimated (their colonists × what one of yours makes)" },
    { m: "gold",      grp: "Economy",   label: "Gold", tip: "Gold in the treasury" },
    { m: "tax",       grp: "Economy",   label: "Tax", tip: "Tax rate in Europe (lower is better)", low: true, pct: true },
    { m: "military",  grp: "Military",  label: "Army", tip: "Land strength: the attack power of soldiers, dragoons and artillery" },
    { m: "naval",     grp: "Military",  label: "Navy", tip: "Sea strength: the attack power of the ships" },
    { m: "sol",       grp: "Politics",  label: "Rebels", tip: "Share of colonists that supports independence", pct: true },
    { m: "fathers",   grp: "Politics",  label: "Fathers", tip: "Founding Fathers in Congress" }
  ];
  const NAT_GRPS = ["Expansion", "Economy", "Military", "Politics"];
  const ME_C = "#b5832a", OTHER_C = "#69737f";
  let nations = null, natTurn = null, natBusy = false;
  let natSel = store.get("fc.natMeasure") || "colonists";

  async function loadNations(turn) {
    if (natBusy) return;
    natBusy = true;
    try {
      const r = await fetch("/api/nations", { cache: "no-store" });
      const n = await r.json();
      if (n.nations) { nations = n; natTurn = turn; renderNations(); }
    } catch (e) { /* Try again next poll */ }
    natBusy = false;
  }

  function natIndex(m) { return nations.measures.indexOf(m); }
  function natVal(n, c) { const v = n.v[natIndex(c.m)]; return v < 0 ? null : (c.div ? v / c.div : v); }
  function natFmt(c, v) {
    if (v == null) return "";
    if (c.div) return v.toFixed(1);
    return v.toLocaleString() + (c.pct ? "%" : "");
  }
  function ordinal(n) { return "#" + n; }

  // Competition ranking ("1, 2, 2, 4") of the nations that have a value.
  function natRanks(c) {
    const vals = nations.nations.map((n) => natVal(n, c));
    return vals.map((v) => v == null ? null :
      1 + vals.filter((w) => w != null && (c.low ? w < v : w > v)).length);
  }

  function renderNations() {
    renderNatCards();
    renderMilitary();
    const N = nations.nations;
    const ranks = {};
    for (const c of NAT_COLS) ranks[c.m] = natRanks(c);
    // A group score is the average rank over its measures; the
    // groups are then ranked on that.
    const grpRank = {};
    for (const g of NAT_GRPS) {
      const cols = NAT_COLS.filter((c) => c.grp === g);
      const avg = N.map((n, i) => {
        const r = cols.map((c) => ranks[c.m][i]).filter((x) => x != null);
        return r.length ? r.reduce((a, b) => a + b, 0) / r.length : null;
      });
      const counted = avg.filter((a) => a != null).length;
      grpRank[g] = avg.map((a) => a == null ? null :
        { r: 1 + avg.filter((b) => b != null && b < a - 1e-9).length, of: counted });
    }
    const meI = N.findIndex((n) => n.me);

    // Summary: where do I stand?
    const ahead = [], behind = [];
    for (const c of NAT_COLS) {
      const r = ranks[c.m][meI], of = ranks[c.m].filter((x) => x != null).length;
      if (r == null || of < 2) continue;
      if (ranks[c.m].every((x) => x == null || x === 1)) continue; // All level
      if (r === 1) ahead.push(c.label.toLowerCase());
      else behind.push("<b>" + c.label.toLowerCase() + "</b> (#" + r + " of " + of + ")");
    }
    const others = N.filter((n) => !n.me);
    const otherAvg = others.length
      ? others.reduce((a, n) => a + n.v[natIndex("colonists")], 0) /
        Math.max(1, others.reduce((a, n) => a + n.knownColonies, 0)) : 0;
    const me = N[meI];
    $("natSummary").innerHTML = NAT_GRPS.map((g) => {
      const x = grpRank[g][meI];
      return '<span class="nchip"><span class="lbl">' + g + "</span> " +
        (x ? "<b>" + ordinal(x.r) + '</b> <span class="m">of ' + x.of + "</span>" : '<span class="m">?</span>') + "</span>";
    }).join("") +
      '<div class="nline">' +
      (me && me.knownColonies ? "Your colonies average <b>" + (me.v[natIndex("colonists")] / me.knownColonies).toFixed(1) +
        "</b> colonists, the others <b>" + otherAvg.toFixed(1) + "</b>. " : "") +
      (ahead.length ? "Ahead in <b>" + ahead.join(", ") + "</b>. " : "") +
      (behind.length ? "Behind in " + behind.join(", ") + "." : "") +
      "</div>";

    // Table
    const maxOf = {};
    for (const c of NAT_COLS) maxOf[c.m] = Math.max(0, ...N.map((n) => natVal(n, c) || 0));
    const maxSize = Math.max(1, ...N.map((n) => n.sizes[0] || 0));
    let h = '<table><thead><tr class="grp"><th></th>';
    for (const g of NAT_GRPS) {
      h += '<th colspan="' + (NAT_COLS.filter((c) => c.grp === g).length + (g === "Expansion" ? 1 : 0)) + '">' + g + "</th>";
    }
    h += '</tr><tr><th class="nname">Nation</th>';
    for (const c of NAT_COLS) {
      h += '<th class="num' + (c.m === natSel ? " sel" : "") + '" data-m="' + c.m + '" title="' + esc(c.tip) + '">' + c.label + "</th>";
      if (c.m === "avgSize") h += '<th title="Their colonies by number of colonists, biggest first">Sizes</th>';
    }
    h += "</tr></thead><tbody>";
    const order = N.map((n, i) => i).sort((a, b) => {
      const c = NAT_COLS.find((x) => x.m === natSel);
      const ra = ranks[c.m][a], rb = ranks[c.m][b];
      return (ra == null) - (rb == null) || ra - rb || N[a].name.localeCompare(N[b].name);
    });
    for (const i of order) {
      const n = N[i];
      h += '<tr class="' + (n.me ? "me" : "") + (n.met ? "" : " unmet") + '"><td class="nname">' +
        '<i class="sw" style="background:' + esc(n.color) + '"></i>' + esc(n.name) +
        (n.me ? ' <span class="m">(you)</span>' : n.met ? "" : ' <span class="m">not met</span>') + "</td>";
      for (const c of NAT_COLS) {
        const v = natVal(n, c), r = ranks[c.m][i];
        if (v == null) {
          const why = ["sol", "fathers", "tax"].includes(c.m) ? "Needs Jan de Witt in your Congress" : "Not known";
          h += '<td class="num m" title="' + why + '">?</td>';
        } else {
          const w = maxOf[c.m] > 0 ? Math.round(100 * (c.low ? 1 : v / maxOf[c.m])) : 0;
          const level = ranks[c.m].every((x) => x == null || x === 1);
          h += '<td class="num' + (c.m === natSel ? " sel" : "") + '"><span class="rk' + (r === 1 && !level ? " first" : "") + '">' + (level ? "" : r) + "</span>" +
            natFmt(c, v) + (c.low ? "" : '<span class="bar"><i style="width:' + w + "%;background:" + (n.me ? ME_C : OTHER_C) + '"></i></span>') + "</td>";
        }
        if (c.m === "avgSize") {
          h += '<td class="sizes" title="' + esc(n.sizes.length ? n.sizes.join(", ") : "none known") + '"><div class="szs">' +
            n.sizes.slice(0, 24).map((s) => '<i style="height:' + Math.max(2, Math.round(22 * s / maxSize)) + "px;background:" +
              (n.me ? ME_C : OTHER_C) + '"></i>').join("") + "</div></td>";
        }
      }
      h += "</tr>";
    }
    h += "</tbody></table>";
    $("natTable").innerHTML = h;
    $("natTable").querySelectorAll("th[data-m]").forEach((th) => th.addEventListener("click", () => {
      natSel = th.dataset.m;
      store.set("fc.natMeasure", natSel);
      renderNations();
    }));

    $("natNote").innerHTML = "Click a column to sort and chart it. The small number is the rank. " +
      "Other nations' figures come from the game's Foreign Affairs report" +
      (nations.deWitt ? "." : "; their rebels, fathers and tax need Jan de Witt in your Congress.") +
      " Colonists and sizes are counted in the colonies you can see on the map. Army and navy are strength scores, not head counts." +
      " Turns before the page ran come from the game's autosaves." +
      (nations.reading ? " <b>Reading old autosaves: " + nations.reading[0] + " of " + nations.reading[1] + "</b>." : "");
    drawNationChart();
  }

  function drawNationChart() {
    const c = NAT_COLS.find((x) => x.m === natSel);
    const k = natIndex(c.m), svg = $("nchart");
    $("nchartHead").innerHTML = "<b>" + esc(c.label) + '</b> <span class="m">' + esc(c.tip) + "</span>" +
      '<span class="mleg"><i style="background:' + ME_C + '"></i>You</span>' +
      '<span class="mleg"><i style="background:' + OTHER_C + '"></i>Others</span>';
    const turns = Object.keys(nations.history).map(Number).sort((a, b) => a - b);
    const series = nations.nations.map((n) => ({
      n, pts: turns.map((t) => {
        const row = nations.history[String(t)][n.id];
        const v = row ? row[k] : -1;
        return v < 0 ? null : [t, c.div ? v / c.div : v];
      }).filter((p) => p)
    })).filter((s) => s.pts.length);
    const W = svg.getBoundingClientRect().width || 600, H = 200;
    const pad = { l: 44, r: 110, t: 12, b: 26 };
    svg.setAttribute("viewBox", "0 0 " + W + " " + H);
    svg.setAttribute("aria-label", c.label + " of each nation by turn");
    if (!series.length) { svg.innerHTML = ""; return; }
    const t0 = turns[0], t1 = Math.max(turns[turns.length - 1], t0 + 1);
    const ymax = Math.max(c.div ? 2 : 4, ...series.flatMap((s) => s.pts.map((p) => p[1]))) * 1.15;
    const X = (t) => pad.l + (t - t0) / (t1 - t0) * (W - pad.l - pad.r);
    const Y = (v) => H - pad.b - v / ymax * (H - pad.t - pad.b);
    let s = "";
    const raw = ymax / 4, mag = Math.pow(10, Math.floor(Math.log10(raw)));
    const stepV = [1, 2, 5, 10].map((f) => f * mag).find((v) => v >= raw);
    for (let v = 0; v <= ymax; v += stepV) {
      s += '<line x1="' + pad.l + '" x2="' + (W - pad.r) + '" y1="' + Y(v) + '" y2="' + Y(v) + '" stroke="#2f3742" stroke-width="1"/>' +
        '<text x="' + (pad.l - 6) + '" y="' + (Y(v) + 4) + '" text-anchor="end" fill="#98a2ad" font-size="11">' +
        (stepV < 1 ? v.toFixed(1) : Math.round(v).toLocaleString()) + "</text>";
    }
    const xt = Math.min(6, t1 - t0);
    for (let q = 0; q <= xt; q++) {
      const t = Math.round(t0 + (t1 - t0) * q / xt);
      s += '<text x="' + X(t) + '" y="' + (H - 8) + '" text-anchor="' + (q === xt ? "end" : q === 0 ? "start" : "middle") +
        '" fill="#98a2ad" font-size="11">' + esc(nations.turns[String(t)] || "turn " + t) + "</text>";
    }
    // Others first, so my line is drawn on top.
    const ordered = series.filter((x) => !x.n.me).concat(series.filter((x) => x.n.me));
    const labels = [];
    for (const x of ordered) {
      const d = x.pts.map((p, i) => (i ? "L" : "M") + X(p[0]).toFixed(1) + "," + Y(p[1]).toFixed(1)).join("");
      const col = x.n.me ? ME_C : OTHER_C;
      s += '<path d="' + d + '" fill="none" stroke="' + col + '" stroke-width="' + (x.n.me ? 2.5 : 1.5) + '"/>';
      if (x.pts.length === 1) s += '<circle cx="' + X(x.pts[0][0]) + '" cy="' + Y(x.pts[0][1]) + '" r="3" fill="' + col + '"/>';
      const last = x.pts[x.pts.length - 1];
      // Nations ending on the same value share one label.
      const same = labels.find((l) => !l.me && !x.n.me && Math.abs(l.y0 - Y(last[1])) < 1 && l.x === X(last[0]));
      if (same) same.names.push(x.n.name);
      else labels.push({ y0: Y(last[1]), y: Y(last[1]), x: X(last[0]), names: [x.n.name], me: x.n.me });
    }
    // Spread the end labels so they do not overlap, and keep them inside.
    labels.sort((a, b) => a.y - b.y);
    for (let i = 1; i < labels.length; i++) labels[i].y = Math.max(labels[i].y, labels[i - 1].y + 13);
    const over = labels.length ? labels[labels.length - 1].y - (H - pad.b) : 0;
    if (over > 0) for (const l of labels) l.y -= over;
    for (const l of labels) {
      s += '<text x="' + (l.x + 6) + '" y="' + (l.y + 4) + '" fill="' + (l.me ? "#e8e3d8" : "#98a2ad") + '" font-size="12"' +
        (l.me ? ' font-weight="600"' : "") + ">" + (l.names.length > 1 ? "<title>" + esc(l.names.join(", ")) + "</title>" : "") +
        esc(l.names.length > 1 ? l.names.length + " others" : l.names[0]) + "</text>";
    }
    s += '<line id="ncross" x1="0" x2="0" y1="' + pad.t + '" y2="' + (H - pad.b) + '" stroke="#98a2ad" stroke-width="1" visibility="hidden"/>';
    s += '<rect x="' + pad.l + '" y="' + pad.t + '" width="' + (W - pad.l - pad.r) + '" height="' + (H - pad.t - pad.b) + '" fill="transparent" id="nhit"/>';
    if (turns.length === 1) {
      s += '<text x="' + ((pad.l + W - pad.r) / 2) + '" y="' + (pad.t + 16) + '" text-anchor="middle" fill="#98a2ad" font-size="12">' +
        "The trend builds up turn by turn while you play</text>";
    }
    svg.innerHTML = s;
    const hit = svg.querySelector("#nhit"), cross = svg.querySelector("#ncross"), tip = $("ntip");
    hit.addEventListener("mousemove", (e) => {
      const r = svg.getBoundingClientRect();
      const mx = (e.clientX - r.left) * W / r.width;
      const want = t0 + (mx - pad.l) / (W - pad.l - pad.r) * (t1 - t0);
      let t = turns[0];
      for (const q of turns) if (Math.abs(q - want) < Math.abs(t - want)) t = q;
      cross.setAttribute("x1", X(t)); cross.setAttribute("x2", X(t));
      cross.setAttribute("visibility", "visible");
      const rows = series.map((x) => {
        const p = x.pts.find((q) => q[0] === t);
        return p ? { name: x.n.name, v: p[1], me: x.n.me } : null;
      }).filter((x) => x).sort((a, b) => c.low ? a.v - b.v : b.v - a.v);
      tip.innerHTML = "<b>" + esc(nations.turns[String(t)] || "turn " + t) + "</b>" + rows.map((x) =>
        '<div class="row' + (x.me ? " me" : "") + '">' + esc(x.name) + " <b>" + natFmt(c, x.v) + "</b></div>").join("");
      tip.hidden = false;
      tip.style.left = Math.min(r.width - tip.offsetWidth - 4, (e.clientX - r.left) + 12) + "px";
      tip.style.top = "8px";
    });
    hit.addEventListener("mouseleave", () => { tip.hidden = true; cross.setAttribute("visibility", "hidden"); });
  }

  // ---------------------------------------------------------------- log

  function logClass(e) {
    if (e.kind === "turn") return "turn";
    if (e.kind === "load" || e.kind === "unload" || e.kind === "arrive") return e.kind;
    if (e.kind === "natives") return "natives";
    if (/missing_goods|warning|combat|unit_lost|demands|government_efficiency/.test(e.kind)) return "warn";
    if (e.kind === "lost") return "alarm";
    return "";
  }

  // The log is grouped by turn, newest turn first, each turn with its
  // heading on top and its newest entry just below the heading.
  function turnGroup(turn, text) {
    const ol = $("logList");
    let head = ol.querySelector('li.turn[data-turn="' + turn + '"]');
    if (!head) {
      head = document.createElement("li");
      head.className = "turn";
      head.dataset.turn = turn;
      head.textContent = text || "Turn " + turn;
      // Turns arrive in order, so a new turn goes on top.
      ol.insertBefore(head, ol.firstChild);
    } else if (text) {
      head.textContent = text;
    }
    return head;
  }

  function addLog(entries) {
    if (!entries.length) return;
    const ol = $("logList");
    const open = !$("log").hidden && drawerTab === "log";
    for (const e of entries) {
      lastLogId = Math.max(lastLogId, e.id);
      if (seenLog.has(e.id)) continue;
      seenLog.add(e.id);
      if (e.kind === "turn") { turnGroup(e.turn, e.text); continue; }
      const head = turnGroup(e.turn, null);
      const li = document.createElement("li");
      li.className = logClass(e) + (e.x >= 0 ? " link" : "") + (state ? " new" : "");
      let html = esc(e.text);
      if (e.goods && e.goods.length) {
        html += ' <span class="g">' + e.goods.map((g) =>
          '<span class="' + (g[1] < 0 ? "neg" : "pos") + '">' + gi(g[0]) +
          (g[1] === 0 ? "" : g[1] < 0 ? "−" + -g[1] : "+" + g[1]) + "</span>").join("") + "</span>";
      }
      li.innerHTML = html;
      if (e.x >= 0) li.addEventListener("click", () => { pulse(e.x, e.y); centreOn(e.x, e.y); });
      ol.insertBefore(li, head.nextSibling);
      if (!open) unread++;
    }
    while (ol.children.length > 500) ol.removeChild(ol.lastChild);
    showUnread();
  }

  function showUnread() {
    const u = $("unread");
    u.hidden = unread === 0;
    u.textContent = unread > 99 ? "99+" : unread;
  }

  // The sidebar has two tabs: the log and the Colopedia.
  let drawerTab = ["pedia", "rel"].includes(store.get("fc.tab")) ? store.get("fc.tab") : "log";

  function setDrawer(open, tab) {
    if (tab) drawerTab = tab;
    $("log").hidden = !open;
    document.body.classList.toggle("logOpen", open);
    $("logList").hidden = drawerTab !== "log";
    $("pedia").hidden = drawerTab !== "pedia";
    $("rel").hidden = drawerTab !== "rel";
    document.querySelectorAll(".dtab").forEach((b) =>
      b.classList.toggle("on", b.dataset.tab === drawerTab));
    $("logToggle").classList.toggle("on", open && drawerTab === "log");
    $("pediaToggle").classList.toggle("on", open && drawerTab === "pedia");
    $("relToggle").classList.toggle("on", open && drawerTab === "rel");
    if (open && drawerTab === "log") { unread = 0; showUnread(); }
    if (open && drawerTab === "pedia") renderPedia();
    if (open && drawerTab === "rel") loadRelations(true);
    store.set("fc.logOpen", open ? "1" : "0");
    store.set("fc.tab", drawerTab);
    setTimeout(resize, 220);
  }
  function toggleDrawer(tab) {
    const showing = !$("log").hidden && drawerTab === tab;
    setDrawer(!showing, tab);
  }

  // ---------------------------------------------------------------- relations

  let relLoaded = 0, relJson = "";

  async function loadRelations(force) {
    if (!force && performance.now() - relLoaded < 5000) return;
    relLoaded = performance.now();
    try {
      const r = await fetch("/api/relations", { cache: "no-store" });
      const t = await r.text();
      if (t !== relJson) { relJson = t; renderRelations(JSON.parse(t)); }
    } catch (e) { /* Try again later */ }
  }

  const MOOD = { happy: "ok", content: "ok", displeased: "warn", angry: "alarm", hateful: "alarm" };
  const STANCE = { peace: "ok", alliance: "info", ceaseFire: "warn", war: "alarm", uncontacted: "m" };
  function badge(text, cls) { return '<span class="tag ' + (cls || "m") + '">' + esc(text) + "</span>"; }
  function swatch(c) { return '<i class="sw" style="background:' + esc(c) + '"></i>'; }
  function cap(s) { return s ? s.charAt(0).toUpperCase() + s.slice(1) : ""; }

  function renderRelations(d) {
    const box = $("rel");
    if (!d.home) { box.innerHTML = '<p class="m">No game loaded.</p>'; return; }
    const h = d.home;
    let html = "<h4>Homefront</h4>";
    const status = h.playerType === "colonial" ? "Colony of the Crown"
      : h.playerType === "rebel" ? "At war for independence" : cap(h.playerType);
    html += '<div class="pe"><div class="peh"><b>' + esc(h.nation) + "</b> " + badge(status, h.playerType === "colonial" ? "m" : "warn") +
      '<span class="price">score ' + h.score + "</span></div><dl>" +
      row("King's tax", h.tax + "%") +
      row("Rebels", h.sol + '% <span class="m">(Sons of Liberty across your colonies; 50%+ to declare independence)</span>') +
      row("Colonies", h.colonies + " with " + h.colonists + " colonists") +
      row("Congress", h.fathers + " fathers" + (h.father ? ", next: <b>" + esc(h.father) + "</b>, " + h.fatherBells + " bells to go" : "")) +
      row("Immigration", h.immigration + " / " + h.immigrationRequired + ' crosses <div class="track"><div class="fill" style="width:' +
        Math.min(100, Math.round(100 * h.immigration / Math.max(1, h.immigrationRequired))) + '%"></div></div>') +
      row("Europe", (h.docks ? h.docks + " waiting on the docks, " : "") + (h.shipsInEurope ? h.shipsInEurope + " ship(s) in port, " : "") +
        (h.toEurope ? h.toEurope + " sailing to Europe, " : "") + (h.toAmerica ? h.toAmerica + " sailing to America, " : "") +
        (h.recruitPrice >= 0 ? "recruiting costs " + h.recruitPrice : "")) +
      row("Boycotts", h.boycotts.length ? h.boycotts.map((b) => '<span class="it">' + gi(b[0]) + "pay " + b[1] + " to lift</span>").join(" ")
        : '<span class="m">none</span>') +
      row("King's army", h.ref.length ? h.ref.map((r) => esc(r[0])).join(", ") : '<span class="m">unknown</span>') +
      "</dl></div>";

    html += "<h4>European nations</h4>";
    html += d.europeans.map((e) =>
      '<div class="pe"><div class="peh">' + swatch(e.color) + "<b>" + esc(e.name) + "</b> " +
      (e.met ? badge(e.stanceName || e.stance, STANCE[e.stance]) : badge("not met yet", "m")) +
      (e.rebel ? badge("rebels", "warn") : "") +
      '<span class="price">' + e.colonies.length + " known " + (e.colonies.length === 1 ? "colony" : "colonies") + "</span></div>" +
      (e.colonies.length ? '<p class="m">' + e.colonies.map(esc).join(", ") + "</p>" : "") + "</div>").join("") ||
      '<p class="m">No other Europeans.</p>';

    html += "<h4>Native tribes</h4>";
    html += d.tribes.map((t) => {
      const skills = (t.skills || []).slice(0, 4).map((s) => esc(s[0])).join(", ");
      const rows = t.settlements.map((s) =>
        '<div class="set' + (s.x >= 0 ? " link" : "") + '" data-x="' + s.x + '" data-y="' + s.y + '">' +
        '<div class="sn">' + (s.capital ? '<span title="Capital">\u2605</span> ' : "") + esc(s.name) +
        " " + badge(s.alarmName, MOOD[s.alarm] || "m") +
        (s.mission ? badge(s.mission === "ours" ? "\u271d our mission" : "\u271d " + s.mission + " mission", s.mission === "ours" ? "ok" : "m") : "") +
        "</div>" +
        '<div class="sd"><span class="m">Teaches</span> ' + (s.skill ? esc(s.skill) : '<span class="m">? visit to find out</span>') +
        ' \u00b7 <span class="m">Wants</span> ' + (s.wanted.length ? s.wanted.map((g, i) =>
          '<span class="it" title="' + ["pays +50%", "pays +25%", "pays +10%"][i] + '">' + gi(g) + "</span>").join("") +
          (s.wantedSince >= 0 ? ' <span class="m" title="Wanted goods change over time; a visit updates them">(as of turn ' + s.wantedSince + ")</span>" : "")
          : '<span class="m">? visit to find out</span>') +
        ' \u00b7 <span class="m">Most hated:</span> ' + (s.hated ? esc(s.hated) : "none") + "</div></div>").join("");
      return '<div class="pe"><div class="peh">' + swatch(t.color) + "<b>" + esc(t.name) + "</b> " +
        badge(t.stanceName || t.stance, STANCE[t.stance]) + (t.worst ? badge("mood: " + t.worst, MOOD[t.worst]) : "") +
        '<span class="price">' + esc(t.settlementType || "") + "</span></div>" +
        '<p class="m">' + cap(t.aggression) + " aggression \u00b7 " +
        ({ low: "few", average: "a fair number of", high: "many" }[t.number] || t.number) + " settlements" +
        (skills ? " \u00b7 usually teach: " + skills : "") + "</p>" +
        (rows || '<p class="m">None of their settlements found yet.</p>') + "</div>";
    }).join("") || '<p class="m">You have not met any tribe yet.</p>';
    if (d.unmet) html += '<p class="m">' + d.unmet + (d.unmet === 1 ? " tribe" : " tribes") + " not met yet.</p>";
    html += '<p class="m">Wanted goods: the first pays 50% more, the second 25%, the third 10%. Click an icon for the Colopedia, a settlement to show it on the map. See also claude_docs/tribes.md.</p>';

    const keep = box.scrollTop;
    box.innerHTML = html;
    box.scrollTop = keep;
    box.querySelectorAll(".set.link").forEach((el) => el.addEventListener("click", () => {
      const x = +el.dataset.x, y = +el.dataset.y;
      pulse(x, y); centreOn(x, y);
    }));
  }

  // ---------------------------------------------------------------- colopedia

  let pedia = null, pediaTurn = null, pediaSection = "start", pediaFocus = null;

  async function loadPedia() {
    try {
      const r = await fetch("/api/colopedia", { cache: "no-store" });
      const p = await r.json();
      if (p.goods) { pedia = p; pediaTurn = state ? state.turn : null; }
    } catch (e) { /* Try again next time */ }
  }

  function pGoods(id) { return pedia && pedia.goods.find((g) => g.id === id); }
  function gl(id) {
    const g = pGoods(id);
    return '<a class="gl" data-g="' + esc(id) + '">' + gi(id) + esc(g ? g.name : goodsName(id)) + "</a>";
  }
  function costs(list) {
    return list.length ? list.map((c) => '<span class="it">' + gi(c[0]) + c[1] + "</span>").join(" ")
      : '<span class="m">-</span>';
  }
  function names(arr) { return arr && arr.length ? arr.map(esc).join(", ") : '<span class="m">-</span>'; }
  function row(k, v) { return '<dt>' + k + '</dt><dd>' + v + '</dd>'; }

  function goodsEntry(g) {
    let chain = "";
    if (g.from || g.into) {
      chain = '<div class="chain">' + (g.from ? gl(g.from) + " \u2192 " : "") +
        "<b>" + esc(g.name) + "</b>" + (g.into ? " \u2192 " + gl(g.into) : "") + "</div>";
    }
    return '<div class="pe" data-id="' + esc(g.id) + '"><div class="peh">' + gi(g.id) +
      " <b>" + esc(g.name) + "</b>" +
      (g.sell != null ? '<span class="price" title="Price per unit in Europe right now">Europe: sell ' +
        g.sell + " \u00b7 buy " + g.buy + "</span>" : "") + "</div>" +
      (g.desc ? "<p>" + esc(g.desc) + "</p>" : "") + chain + "<dl>" +
      (g.madeIn.length ? row("Made in", names(g.madeIn)) : "") +
      (g.experts.length ? row("Expert", names(g.experts)) : "") +
      (g.usedIn.length ? row("Used in", names(g.usedIn)) : "") +
      (g.neededFor.length ? row("Needed for", names(g.neededFor)) : "") + "</dl></div>";
  }

  function buildingEntry(b) {
    return '<div class="pe" data-id="' + esc(b.id) + '"><div class="peh"><b>' + esc(b.name) + "</b>" +
      (b.pop > 0 ? '<span class="price">' + b.pop + "+ colonists</span>" : "") + "</div>" +
      (b.desc ? "<p>" + esc(b.desc) + "</p>" : "") + "<dl>" +
      row("Costs", costs(b.cost)) +
      (b.makes.length ? row("Makes", costs(b.makes)) : "") +
      (b.uses.length ? row("Uses", costs(b.uses)) : "") +
      (b.workers > 0 ? row("Workers", b.workers) : "") +
      (b.from ? row("Upgrade of", esc(b.from)) : "") +
      (b.to ? row("Upgrades to", esc(b.to)) : "") + "</dl></div>";
  }

  function unitEntry(u) {
    return '<div class="pe" data-id="' + esc(u.id) + '"><div class="peh"><b>' + esc(u.name) + "</b>" +
      '<span class="price">' + (u.price >= 0 ? u.price.toLocaleString() + " gold in Europe" : "not for sale") +
      "</span></div>" + (u.desc ? "<p>" + esc(u.desc) + "</p>" : "") + "<dl>" +
      row("Combat", "attack " + u.offence + " \u00b7 defence " + u.defence) +
      row("Moves", u.moves + (u.naval && u.space ? " \u00b7 holds " + u.space : "")) +
      (u.expert ? row("Expert in", gl(u.expert)) : "") +
      (u.cost.length ? row("To build", costs(u.cost)) : "") + "</dl></div>";
  }

  function fatherEntry(f) {
    const tag = f.have ? '<span class="tag ok">in Congress</span>'
      : f.current ? '<span class="tag warn">being elected</span>' : "";
    return '<div class="pe' + (f.have ? " have" : "") + '" data-id="' + esc(f.id) + '"><div class="peh"><b>' +
      esc(f.name) + "</b> " + tag + '<span class="price">' + esc(f.type) + "</span></div>" +
      (f.desc ? "<p>" + esc(f.desc) + "</p>" : "") + "</div>";
  }

  const GUIDE = [
    ["The loop", "Colonists work in colonies and on the land around them. What they make is stored in the colony, used to build, or carried to Europe and sold. Gold buys more colonists, tools and ships. Grow, then declare independence and win the war that follows."],
    ["1. Food first", "Every colonist eats 2 food. A colony with a food surplus grows: at 200 food a new colonist is born. Put a farmer or a fisherman (needs Docks) on the best food tile. Watch for negative food in the colony cards."],
    ["2. Make money", "Put colonists on a cash crop (furs, tobacco, sugar, cotton) or, better, process it (furs into coats, sugar into rum). Ship it to Europe and sell. Prices drop as you sell more of the same good."],
    ["3. Lumber, then hammers", "A carpenter turns lumber into hammers; hammers (and for some things tools) build buildings, wagons and ships. Pick a build in every colony. Good first builds: Docks on the coast, a Lumber mill, a Stockade only if threatened (you cannot abandon a colony with one)."],
    ["4. Tools and pioneers", "A colonist with tools is a pioneer: clearing forest, ploughing and roads each use 20 tools. Roads make wagons faster and ploughed fields give more food. A blacksmith makes tools from ore; an armoury makes muskets from tools."],
    ["5. Bells and Sons of Liberty", "Statesmen in the town hall make bells. Bells raise the colony's Sons of Liberty: at 50% every worker makes +1, at 100% +2. Large colonies full of Tories get penalties. Bells also elect Founding Fathers, permanent bonuses (see Fathers)."],
    ["6. Crosses", "A preacher in the church makes crosses. Enough crosses and a new colonist appears on the docks in Europe; bring him over by ship."],
    ["7. Experts and schools", "An expert (master blacksmith, expert farmer...) makes about twice as much. A Schoolhouse lets an expert teach a free colonist; harder skills need a college or university."],
    ["8. Wagons and trade routes", "Wagon trains carry 2 loads over land. Give them a trade route (Orders menu) to move goods between colonies automatically. This page shows the routes on the map and warns when a supply runs short."],
    ["9. Defence", "Soldiers are colonists with 50 muskets; dragoons also have 50 horses. Fortify them in colonies. Horses breed in colonies with spare food and at least 2 horses."],
    ["10. The King", "The King raises taxes from time to time. Refusing means a boycott of that good in Europe. Late in the game, with enough Sons of Liberty overall, declare independence (Game menu) and beat the Royal Expeditionary Force."]
  ];

  function startPage() {
    let h = "";
    if (pedia.congress && pedia.congress.current) {
      h += '<div class="pe"><div class="peh"><b>Continental Congress</b></div><p>' +
        esc(pedia.congress.current) + " is being elected: " + pedia.congress.remaining +
        " more bells needed.</p></div>";
    }
    if (state && state.colonies) {
      const idle = state.colonies.filter((c) => c.build.lvl === "alarm").map((c) => c.name);
      const tips = [];
      if (idle.length) tips.push("Pick something to build in " + idle.join(", ") + ": open the colony and click the building site.");
      if (state.pending > 0) tips.push(state.pending + " units still have go-to orders this turn: the Execute Orders button (fast-forward sign, bottom right) carries them out.");
      if (state.idle > 0) tips.push(state.idle + " units are waiting for orders. The game shows them one by one; press Space to skip one, F to fortify, S to sentry.");
      if (tips.length) h += '<div class="pe tips"><div class="peh"><b>Right now in your game</b></div><ul>' +
        tips.map((t) => "<li>" + esc(t) + "</li>").join("") + "</ul></div>";
    }
    h += GUIDE.map((g) => '<div class="pe"><div class="peh"><b>' + esc(g[0]) + "</b></div><p>" + esc(g[1]) + "</p></div>").join("");
    h += '<p class="m">More: the <a href="' + esc(pedia.manual) + '" target="_blank" rel="noopener">FreeCol manual</a>, ' +
      "and the Colopedia menu in the game. Click any goods icon on this page to look it up.</p>";
    return h;
  }

  function renderPedia() {
    const body = $("pediaBody");
    document.querySelectorAll(".ptab").forEach((b) => b.classList.toggle("on", b.dataset.sec === pediaSection));
    if (!pedia || (state && pediaTurn !== state.turn)) {
      if (!pedia) body.innerHTML = '<p class="m">Loading the rules from the game\u2026</p>';
      loadPedia().then(() => { if (pedia && pediaTurn !== null) renderPedia(); });
      if (!pedia) return;
    }
    const keep = body.scrollTop;
    const q = $("pediaSearch").value.trim().toLowerCase();
    const hit = (e) => (e.name + " " + (e.desc || "") + " " + (e.type || "")).toLowerCase().includes(q);
    const part = (title, html) => html ? "<h4>" + title + "</h4>" + html : "";
    let html;
    if (q) {
      html = part("Goods", pedia.goods.filter(hit).map(goodsEntry).join("")) +
        part("Buildings", pedia.buildings.filter(hit).map(buildingEntry).join("")) +
        part("Units", pedia.units.filter(hit).map(unitEntry).join("")) +
        part("Founding Fathers", pedia.fathers.filter(hit).map(fatherEntry).join(""));
      if (!html) html = '<p class="m">Nothing found.</p>';
    } else if (pediaSection === "goods") {
      html = pedia.goods.map(goodsEntry).join("");
    } else if (pediaSection === "buildings") {
      html = pedia.buildings.map(buildingEntry).join("");
    } else if (pediaSection === "units") {
      html = pedia.units.map(unitEntry).join("");
    } else if (pediaSection === "fathers") {
      const types = [...new Set(pedia.fathers.map((f) => f.type))];
      html = types.map((t) => part(t, pedia.fathers.filter((f) => f.type === t).map(fatherEntry).join(""))).join("");
    } else {
      html = startPage();
    }
    body.innerHTML = html;
    if (pediaFocus) {
      const el = body.querySelector('[data-id="' + CSS.escape(pediaFocus) + '"]');
      pediaFocus = null;
      if (el) {
        body.scrollTop = el.offsetTop - body.offsetTop - 8;
        el.classList.add("flash");
        setTimeout(() => el.classList.remove("flash"), 1800);
      }
    } else {
      body.scrollTop = keep;
    }
  }

  function openPedia(section, id) {
    pediaSection = section;
    pediaFocus = id || null;
    $("pediaSearch").value = "";
    setDrawer(true, "pedia");
  }

  // ---------------------------------------------------------------- map

  const canvas = $("map");
  const ctx = canvas.getContext("2d");

  // Neighbours across the four edges of a diamond: NE, SE, SW, NW.
  function neighbour(x, y, dir) {
    const odd = y & 1;
    switch (dir) {
      case 0: return [x + odd, y - 1];
      case 1: return [x + odd, y + 1];
      case 2: return [x - 1 + odd, y + 1];
      case 3: return [x - 1 + odd, y - 1];
      case 4: return [x, y - 2];   // N
      case 5: return [x + 1, y];   // E
      case 6: return [x, y + 2];   // S
      case 7: return [x - 1, y];   // W
    }
  }
  function tileAt(x, y) {
    if (!mapData || x < 0 || y < 0 || x >= mapData.w || y >= mapData.h) return 0;
    return mapData.tiles[y * mapData.w + x];
  }

  // FreeCol's minimap colours are loud; tone them down for a calm map.
  function mix(a, b, t) {
    const p = (h) => [1, 3, 5].map((i) => parseInt(h.substr(i, 2), 16));
    const x = p(a), y = p(b);
    return "rgb(" + x.map((v, i) => Math.round(v + (y[i] - v) * t)).join(",") + ")";
  }
  function tone(p) {
    if (!p) return "#444";
    if (!p.tone) p.tone = p.land ? mix(p.c, "#1a1a12", 0.22) : mix(p.c, "#0f2a40", 0.78);
    return p.tone;
  }

  function drawTerrain() {
    const m = mapData;
    terrainT = Math.max(8, Math.min(32, Math.floor(4000 / (m.w + 1))));
    const T = terrainT;
    const c = document.createElement("canvas");
    c.width = Math.ceil((m.w + 0.5) * T);
    c.height = Math.ceil((m.h + 1) * T / 4);
    const g = c.getContext("2d");
    g.fillStyle = "#0b0d10";
    g.fillRect(0, 0, c.width, c.height);
    const diamond = (x, y) => {
      const px = (x + (y & 1) * 0.5) * T, py = y * T / 4;
      g.beginPath();
      g.moveTo(px + T / 2, py);
      g.lineTo(px + T, py + T / 4);
      g.lineTo(px + T / 2, py + T / 2);
      g.lineTo(px, py + T / 4);
      g.closePath();
    };
    for (let y = 0; y < m.h; y++) {
      for (let x = 0; x < m.w; x++) {
        const v = m.tiles[y * m.w + x];
        if (!v) continue;
        const p = m.palette[(v & 255) - 1];
        diamond(x, y);
        g.fillStyle = tone(p);
        g.fill();
        g.strokeStyle = tone(p);
        g.lineWidth = 0.6;
        g.stroke();
      }
    }
    // Rivers and roads as lines between neighbours.
    const link = (bit, color, width) => {
      g.strokeStyle = color;
      g.lineWidth = width;
      g.lineCap = "round";
      g.beginPath();
      for (let y = 0; y < m.h; y++) {
        for (let x = 0; x < m.w; x++) {
          const v = m.tiles[y * m.w + x];
          if (!(v & bit)) continue;
          const ax = cx(x, y) * T, ay = cy(x, y) * T;
          let alone = true;
          for (let d = 0; d < 8; d++) {
            if (bit === 512 && d >= 4) break; // Rivers only flow diagonally
            const [nx, ny] = neighbour(x, y, d);
            if (!(tileAt(nx, ny) & bit)) continue;
            alone = false;
            if (ny < y || (ny === y && nx < x)) continue; // Draw each link once
            g.moveTo(ax, ay);
            g.lineTo(cx(nx, ny) * T, cy(nx, ny) * T);
          }
          if (alone) { g.moveTo(ax - 0.5, ay); g.lineTo(ax + 0.5, ay); }
        }
      }
      g.stroke();
    };
    link(512, "rgba(70,140,230,.9)", Math.max(1, T / 10));
    link(256, "rgba(120,80,40,.95)", Math.max(1, T / 12));
    // Borders between owners.
    for (let y = 0; y < m.h; y++) {
      for (let x = 0; x < m.w; x++) {
        const v = m.tiles[y * m.w + x];
        const o = v >> 10;
        if (!o) continue;
        const owner = m.owners[o - 1];
        const px = (x + (y & 1) * 0.5) * T, py = y * T / 4;
        const pts = [[px + T / 2, py], [px + T, py + T / 4], [px + T / 2, py + T / 2], [px, py + T / 4]];
        g.strokeStyle = owner.c;
        g.lineWidth = owner.me ? Math.max(1.5, T / 9) : Math.max(1, T / 14);
        for (let d = 0; d < 4; d++) {
          const [nx, ny] = neighbour(x, y, d);
          if ((tileAt(nx, ny) >> 10) === o) continue;
          const a = pts[d], b = pts[(d + 1) % 4];
          g.beginPath();
          g.moveTo(a[0], a[1]);
          g.lineTo(b[0], b[1]);
          g.stroke();
        }
      }
    }
    terrain = c;
  }

  function resize() {
    const r = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.round(r.width * dpr);
    canvas.height = Math.round(r.height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    if (view.auto) fitView();
  }

  function interestBounds() {
    const pts = [];
    if (state) {
      for (const c of state.colonies) pts.push([c.x, c.y]);
      for (const u of state.units) {
        pts.push([u.x, u.y]);
        if (u.path) for (const p of u.path.slice(-1)) pts.push(p);
      }
      for (const r of state.routes) for (const s of r.stops) if (s.x >= 0) pts.push([s.x, s.y]);
    }
    if (!pts.length && mapData) {
      for (let y = 0; y < mapData.h; y++) {
        for (let x = 0; x < mapData.w; x++) if (mapData.tiles[y * mapData.w + x]) pts.push([x, y]);
      }
    }
    if (!pts.length) return null;
    let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity;
    for (const [x, y] of pts) {
      x0 = Math.min(x0, cx(x, y)); x1 = Math.max(x1, cx(x, y));
      y0 = Math.min(y0, cy(x, y)); y1 = Math.max(y1, cy(x, y));
    }
    // A margin of about three tiles all round.
    return [x0 - 3, y0 - 1.5, x1 + 3, y1 + 1.5];
  }

  function fitView() {
    const b = interestBounds();
    if (!b) return;
    const r = canvas.getBoundingClientRect();
    const S = Math.max(6, Math.min(80, Math.min(r.width / (b[2] - b[0]), r.height / (b[3] - b[1]))));
    view.S = S;
    view.ox = r.width / 2 - (b[0] + b[2]) / 2 * S;
    view.oy = r.height / 2 - (b[1] + b[3]) / 2 * S;
  }

  function centreOn(x, y) {
    const r = canvas.getBoundingClientRect();
    view.auto = false;
    view.S = Math.max(view.S, 28);
    view.ox = r.width / 2 - cx(x, y) * view.S;
    view.oy = r.height / 2 - cy(x, y) * view.S;
  }

  function pulse(x, y) {
    if (x < 0) return;
    pulses.push({ x, y, t0: performance.now() });
  }

  // Unit movement: slide along the path the unit was expected to take.
  function startAnimations(prev, s) {
    const now = performance.now();
    const next = {};
    for (const u of s.units) {
      next[u.id] = u;
      const old = lastUnits[u.id];
      if (!old || (old.x === u.x && old.y === u.y)) continue;
      const from = currentPos(u.id, old, now);
      let pts = [from];
      const path = old.path || [];
      const idx = path.findIndex((p) => p[0] === u.x && p[1] === u.y);
      if (idx >= 0) {
        for (let i = 0; i <= idx; i++) pts.push([cx(path[i][0], path[i][1]), cy(path[i][0], path[i][1])]);
      } else {
        pts.push([cx(u.x, u.y), cy(u.x, u.y)]);
      }
      const dur = Math.min(2600, 500 + 220 * (pts.length - 1));
      anims[u.id] = { pts, t0: now, dur };
    }
    lastUnits = next;
  }

  function currentPos(id, u, now) {
    const a = anims[id];
    if (!a) return [cx(u.x, u.y), cy(u.x, u.y)];
    let f = Math.min(1, (now - a.t0) / a.dur);
    if (f >= 1) { delete anims[id]; return a.pts[a.pts.length - 1]; }
    f = f < 0.5 ? 2 * f * f : 1 - Math.pow(-2 * f + 2, 2) / 2; // ease in-out
    // Walk the polyline by length.
    let total = 0;
    const seg = [];
    for (let i = 1; i < a.pts.length; i++) {
      const d = Math.hypot(a.pts[i][0] - a.pts[i - 1][0], a.pts[i][1] - a.pts[i - 1][1]);
      seg.push(d); total += d;
    }
    let want = f * total;
    for (let i = 0; i < seg.length; i++) {
      if (want <= seg[i] || i === seg.length - 1) {
        const t = seg[i] ? Math.min(1, want / seg[i]) : 1;
        return [a.pts[i][0] + (a.pts[i + 1][0] - a.pts[i][0]) * t,
                a.pts[i][1] + (a.pts[i + 1][1] - a.pts[i][1]) * t];
      }
      want -= seg[i];
    }
    return a.pts[a.pts.length - 1];
  }

  function routeColor(u) {
    if (u.route != null && state.routes[u.route]) return state.routes[u.route].color;
    return null;
  }

  function arrowHead(ax, ay, bx, by, color) {
    const ang = Math.atan2(by - ay, bx - ax), mx = (ax + bx) / 2, my = (ay + by) / 2;
    const L = 10, sp = 0.5;
    ctx.beginPath();
    ctx.moveTo(mx + Math.cos(ang) * L / 2, my + Math.sin(ang) * L / 2);
    ctx.lineTo(mx - Math.cos(ang - sp) * L / 2, my - Math.sin(ang - sp) * L / 2);
    ctx.lineTo(mx - Math.cos(ang + sp) * L / 2, my - Math.sin(ang + sp) * L / 2);
    ctx.closePath();
    ctx.fillStyle = color;
    ctx.fill();
    ctx.strokeStyle = "#000";
    ctx.lineWidth = 1;
    ctx.stroke();
  }

  function label(text, x, y, color, bold) {
    ctx.font = (bold ? "600 " : "") + "12px Segoe UI, system-ui, sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "top";
    ctx.lineWidth = 3;
    ctx.strokeStyle = "rgba(0,0,0,.85)";
    ctx.strokeText(text, x, y);
    ctx.fillStyle = color || "#fff";
    ctx.fillText(text, x, y);
  }

  function frame(now) {
    const r = canvas.getBoundingClientRect();
    ctx.clearRect(0, 0, r.width, r.height);
    ctx.fillStyle = "#0b0d10";
    ctx.fillRect(0, 0, r.width, r.height);
    hits = [];
    if (terrain && state && state.inGame) {
      const k = view.S / terrainT;
      ctx.imageSmoothingEnabled = view.S < terrainT * 1.5;
      ctx.drawImage(terrain, view.ox, view.oy, terrain.width * k, terrain.height * k);
      drawRoutes(now);
      drawSettlements();
      drawOthers();
      drawUnits(now);
      drawPulses(now);
    }
    requestAnimationFrame(frame);
  }

  function drawRoutes(now) {
    ctx.save();
    ctx.lineWidth = 2.5;
    ctx.setLineDash([9, 7]);
    ctx.lineDashOffset = -((now / 45) % 16);
    for (const route of state.routes) {
      const stops = route.stops.filter((s) => s.x >= 0);
      const n = stops.length;
      if (n < 2) continue;
      for (let i = 0; i < n; i++) {
        const a = stops[i], b = stops[(i + 1) % n];
        if (a.x === b.x && a.y === b.y) continue;
        const ax = sx(a.x, a.y), ay = sy(a.x, a.y), bx = sx(b.x, b.y), by = sy(b.x, b.y);
        ctx.strokeStyle = route.color;
        ctx.beginPath();
        // Bend the way back a little, so both directions show.
        const nx = -(by - ay), ny = bx - ax, len = Math.hypot(nx, ny) || 1;
        const bend = n === 2 ? 10 + 7 * state.routes.indexOf(route) : 7 * state.routes.indexOf(route);
        const mx = (ax + bx) / 2 + nx / len * bend, my = (ay + by) / 2 + ny / len * bend;
        ctx.moveTo(ax, ay);
        ctx.quadraticCurveTo(mx, my, bx, by);
        ctx.stroke();
        ctx.save();
        ctx.setLineDash([]);
        arrowHead(ax + (mx - ax) * 0.9, ay + (my - ay) * 0.9, bx - (bx - mx) * 0.9, by - (by - my) * 0.9, route.color);
        ctx.restore();
      }
    }
    ctx.restore();

    // Paths of units on the move.
    ctx.save();
    for (const u of state.units) {
      if (!u.path || !u.path.length) continue;
      const pos = currentPos(u.id, u, now);
      ctx.strokeStyle = routeColor(u) || (u.kind === "army" ? "rgba(255,120,110,.95)" : "rgba(255,255,255,.85)");
      ctx.lineWidth = u.route != null ? 3 : 2;
      ctx.setLineDash(u.route != null ? [] : [4, 4]);
      ctx.beginPath();
      ctx.moveTo(pos[0] * view.S + view.ox, pos[1] * view.S + view.oy);
      for (const p of u.path) ctx.lineTo(sx(p[0], p[1]), sy(p[0], p[1]));
      ctx.stroke();
      const end = u.path[u.path.length - 1];
      ctx.setLineDash([]);
      ctx.beginPath();
      ctx.arc(sx(end[0], end[1]), sy(end[0], end[1]), 3.5, 0, Math.PI * 2);
      ctx.fillStyle = ctx.strokeStyle;
      ctx.fill();
    }
    ctx.restore();
  }

  function drawSettlements() {
    if (!mapData) return;
    const r = Math.max(4, Math.min(14, view.S * 0.3));
    for (const s of mapData.settlements) {
      const x = sx(s.x, s.y), y = sy(s.x, s.y);
      ctx.fillStyle = s.c;
      ctx.strokeStyle = "#000";
      ctx.lineWidth = 1.5;
      if (s.colony) {
        ctx.fillRect(x - r, y - r, 2 * r, 2 * r);
        ctx.strokeRect(x - r, y - r, 2 * r, 2 * r);
      } else {
        ctx.beginPath();
        ctx.moveTo(x, y - r); ctx.lineTo(x + r, y + r * 0.8); ctx.lineTo(x - r, y + r * 0.8);
        ctx.closePath(); ctx.fill(); ctx.stroke();
      }
      if (s.mine || view.S >= 30) label(s.name, x, y + r + 2, s.mine ? "#fff" : "#cfd5dc", s.mine);
      const col = s.mine && state.colonies.find((c) => c.x === s.x && c.y === s.y);
      hits.push({ x, y, r: r + 4, colony: col || null,
        html: col ? colonyTip(col) : settlementTip(s) });
    }
  }

  function settlementTip(s) {
    let h = "<b>" + (s.capital ? "\u2605 " : "") + esc(s.name) + '</b> <span class="m">' + esc(s.owner || "") + "</span>";
    if (s.colony) return h;
    h += '<div class="row">Teaches: ' + (s.skill ? esc(s.skill) : '<span class="m">unknown, visit to find out</span>') + "</div>";
    h += '<div class="row">Wants: ' + (s.wanted && s.wanted.length
      ? s.wanted.map((g) => gi(g) + " " + esc(goodsName(g))).join(", ")
      : '<span class="m">unknown, visit to find out</span>') + "</div>";
    return h + '<div class="row m">Relations tab (R) for mood and missions</div>';
  }

  function colonyTip(c) {
    return "<b>" + esc(c.name) + '</b> <span class="m">' + c.size + " colonists</span>" +
      '<div class="row">Makes ' + (c.produces.map((p) => gi(p[0]) + signed(p[1])).join(" ") || "-") + "</div>" +
      '<div class="row">Building ' + esc(c.build.name) + (c.build.text ? ", " + esc(c.build.text) : "") + "</div>" +
      '<div class="row m">Click for the colony card</div>';
  }

  function drawOthers() {
    const r = Math.max(3, Math.min(7, view.S * 0.16));
    for (const o of state.others) {
      const x = sx(o.x, o.y), y = sy(o.x, o.y);
      ctx.beginPath();
      ctx.arc(x, y, r, 0, Math.PI * 2);
      ctx.fillStyle = o.c;
      ctx.fill();
      ctx.lineWidth = o.mil ? 2.5 : 1;
      ctx.strokeStyle = o.mil ? "#ff3b3b" : "#000";
      ctx.stroke();
      hits.push({ x, y, r: r + 4, html: "<b>" + esc(o.who) + "</b><div class='row'>" + o.n +
        (o.n === 1 ? " unit" : " units") + (o.mil ? ", armed" : "") + "</div>" });
    }
  }

  function drawUnits(now) {
    // Units standing still on the same tile share one marker.
    const stacks = {};
    const moving = [];
    for (const u of state.units) {
      if (anims[u.id]) { moving.push(u); continue; }
      const k = u.x + "," + u.y;
      (stacks[k] = stacks[k] || []).push(u);
    }
    const R = Math.max(7, Math.min(18, view.S * 0.36));
    for (const k in stacks) {
      const list = stacks[k];
      const u = pickLead(list);
      marker(u, list, [cx(u.x, u.y), cy(u.x, u.y)], R);
    }
    for (const u of moving) marker(u, [u], currentPos(u.id, u, now), R);
  }

  function pickLead(list) {
    const rank = { wagon: 0, ship: 1, army: 2, unit: 3 };
    return list.slice().sort((a, b) => rank[a.kind] - rank[b.kind])[0];
  }

  function marker(u, list, pos, R) {
    const x = pos[0] * view.S + view.ox, y = pos[1] * view.S + view.oy;
    const color = routeColor(u) || (state.color || "#fff");
    const rush = list.some((v) => v.rush);
    ctx.beginPath();
    ctx.arc(x, y, R, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(245,242,235,.95)";
    ctx.fill();
    ctx.lineWidth = 3;
    ctx.strokeStyle = rush ? "#ffb020" : color;
    ctx.stroke();
    const img = image(unitUrl(u.icon));
    if (img && R >= 9) {
      const s = R * 1.7, k = s / Math.max(img.naturalWidth, img.naturalHeight);
      ctx.drawImage(img, x - img.naturalWidth * k / 2, y - img.naturalHeight * k / 2,
                    img.naturalWidth * k, img.naturalHeight * k);
    } else {
      ctx.fillStyle = color;
      ctx.beginPath(); ctx.arc(x, y, R * 0.45, 0, Math.PI * 2); ctx.fill();
    }
    let tx = x + R + 3;
    // Cargo of a carrier.
    if (u.cargo && u.cargo.length && R >= 8) {
      const ci = image(goodsUrl(u.cargo[0][0]));
      if (ci) { ctx.drawImage(ci, tx, y - 9, 18, 18); tx += 19; }
    }
    if (u.turns > 0) {
      ctx.font = "600 12px Segoe UI, system-ui, sans-serif";
      const t = String(u.turns), w = ctx.measureText(t).width + 8;
      ctx.fillStyle = "rgba(255,255,255,.9)";
      ctx.fillRect(tx, y - 8, w, 16);
      ctx.fillStyle = color;
      ctx.textAlign = "left"; ctx.textBaseline = "middle";
      ctx.fillText(t, tx + 4, y + 1);
    }
    if (list.length > 1) {
      ctx.beginPath();
      ctx.arc(x + R * 0.75, y - R * 0.75, 8, 0, Math.PI * 2);
      ctx.fillStyle = "#222"; ctx.fill();
      ctx.font = "700 10px Segoe UI, system-ui, sans-serif";
      ctx.fillStyle = "#fff"; ctx.textAlign = "center"; ctx.textBaseline = "middle";
      ctx.fillText(String(list.length), x + R * 0.75, y - R * 0.75 + 0.5);
    }
    const group = list.find((v) => v.group);
    if (group && view.S >= 14) {
      label((rush ? "» " : "") + group.group, x, y - R - 16, rush ? "#ffb020" : "#ffe7a8", true);
    }
    hits.push({ x, y, r: R + 3, html: list.map(unitTip).join('<hr style="border:0;border-top:1px solid #333;margin:6px 0">') });
  }

  function unitTip(u) {
    let h = "<b>" + esc(u.name) + '</b> <span class="m">' + esc(u.badge) + "</span>";
    if (u.group) h += '<div class="row">Group: ' + esc(u.group) + (u.rush ? " (rushing ahead)" : "") + "</div>";
    if (u.route != null && state.routes[u.route]) h += '<div class="row">Route: ' + esc(state.routes[u.route].name) + "</div>";
    if (u.dest) h += '<div class="row">To ' + esc(u.dest) + (u.turns > 0 ? ", " + turns(u.turns) : "") + "</div>";
    if (u.cargo && u.cargo.length) h += '<div class="row">' + u.cargo.map((g) => gi(g[0]) + g[1]).join(" ") + "</div>";
    if (u.units) h += '<div class="row">' + u.units + " aboard</div>";
    return h;
  }

  function drawPulses(now) {
    pulses = pulses.filter((p) => now - p.t0 < 2600);
    for (const p of pulses) {
      const f = ((now - p.t0) % 1300) / 1300;
      ctx.beginPath();
      ctx.arc(sx(p.x, p.y), sy(p.x, p.y), 8 + f * 26, 0, Math.PI * 2);
      ctx.strokeStyle = "rgba(224,172,72," + (1 - f).toFixed(2) + ")";
      ctx.lineWidth = 3;
      ctx.stroke();
    }
  }


  // ---------------------------------------------------------------- tabs

  // Every section is a widget.  The Overview tab shows the widgets
  // picked by the player (remembered in this browser); the other tabs
  // show a fixed set, each meant to fit one screen.
  const WIDGETS = [
    { w: "map", label: "Map" },
    { w: "alerts", label: "Alerts" },
    { w: "network", label: "Supply network" },
    { w: "colonies", label: "Colony cards" },
    { w: "goods", label: "Empire goods" },
    { w: "market", label: "Europe prices" },
    { w: "routes", label: "Route charts" },
    { w: "nations", label: "Nations" },
    { w: "military", label: "Military" },
    { w: "independence", label: "Independence" },
    { w: "expansion", label: "Expansion replay" },
    { w: "offers", label: "Offers" }
  ];
  const TABS = {
    colonies: ["colonies", "goods"],
    trade: ["map", "offers", "network", "routes", "market"],
    nations: ["nations"],
    military: ["military"],
    independence: ["independence"],
    history: ["expansion"]
  };
  const TAB_KEYS = ["overview", "colonies", "trade", "nations", "military", "independence", "history"];
  let tab = store.get("fc.tab");
  if (!TAB_KEYS.includes(tab)) tab = "overview";
  let picks = null;
  try { picks = JSON.parse(store.get("fc.picks")); } catch (e) { picks = null; }
  if (!Array.isArray(picks)) picks = ["map", "alerts", "network", "military"];

  function shownWidgets() { return tab === "overview" ? picks : TABS[tab]; }

  function applyTab() {
    const on = shownWidgets();
    for (const el of document.querySelectorAll("section[data-w]")) {
      el.classList.toggle("off", !on.includes(el.dataset.w));
    }
    for (const b of document.querySelectorAll("#tabs .tab")) {
      b.classList.toggle("on", b.dataset.tab === tab);
    }
    $("pickToggle").hidden = tab !== "overview";
    if (tab !== "overview") $("picker").hidden = true;
    document.body.classList.toggle("nomap", !on.includes("map"));
    resize();
    if (market) renderMarket();
    if (nations) drawNationChart();
    if (state) renderNetwork(state.colonies);
    if (exp && on.includes("expansion")) renderExpansion();
  }

  function setTab(t) {
    if (!TAB_KEYS.includes(t)) return;
    tab = t;
    if (t === "independence" && state) loadIndependence(state.turn);
    if (t === "history") loadExpansion();
    if (t === "trade") loadOffers(state ? state.turn : null);
    store.set("fc.tab", t);
    applyTab();
    $("main").scrollTop = 0;
  }

  function renderPicker() {
    $("picker").innerHTML = '<span class="lbl">Overview shows</span>' + WIDGETS.map((x) =>
      '<label class="pick' + (picks.includes(x.w) ? " on" : "") + '"><input type="checkbox" data-pick="' + x.w + '"' +
      (picks.includes(x.w) ? " checked" : "") + "> " + esc(x.label) + "</label>").join("");
  }

  $("tabs").addEventListener("click", (e) => {
    const b = e.target.closest(".tab");
    if (b) setTab(b.dataset.tab);
  });
  $("pickToggle").addEventListener("click", () => {
    renderPicker();
    $("picker").hidden = !$("picker").hidden;
  });
  $("picker").addEventListener("change", (e) => {
    const w = e.target.dataset && e.target.dataset.pick;
    if (!w) return;
    // Keep the page order of the widgets.
    const set = new Set(picks);
    if (e.target.checked) set.add(w); else set.delete(w);
    picks = WIDGETS.map((x) => x.w).filter((x) => set.has(x));
    store.set("fc.picks", JSON.stringify(picks));
    renderPicker();
    applyTab();
  });

  // ---------------------------------------------------------------- nation cards

  const KIND_LABEL = {
    soldier: ["soldier", "soldiers"], dragoon: ["dragoon", "dragoons"],
    artillery: ["artillery", "artillery"], warship: ["warship", "warships"],
    merchant: ["merchant ship", "merchant ships"], wagon: ["wagon train", "wagon trains"],
    colonist: ["colonist outside", "colonists outside"], other: ["other", "others"]
  };
  const KIND_ORDER = ["soldier", "dragoon", "artillery", "warship", "merchant", "wagon", "colonist", "other"];

  function kindList(units, only) {
    const parts = [];
    for (const k of KIND_ORDER) {
      if (only && !only.includes(k)) continue;
      const n = units && units[k];
      if (n) parts.push(n + " " + KIND_LABEL[k][n === 1 ? 0 : 1]);
    }
    return parts.join(", ");
  }
  function num(x) { return Math.round(x).toLocaleString(); }
  function tenth(x) { return (Math.round(x) / 10).toLocaleString(undefined, { maximumFractionDigits: 1 }); }

  function bar(v, max, color) {
    const w = max > 0 ? Math.max(v > 0 ? 3 : 0, Math.round(100 * v / max)) : 0;
    return '<span class="nbar"><i style="width:' + w + "%;background:" + esc(color) + '"></i></span>';
  }

  // A small line of one measure over the recorded turns.
  function natSpark(n, measure, color) {
    if (!nations.history) return "";
    const i = natIndex(measure);
    const pts = Object.keys(nations.history).map(Number).sort((a, b) => a - b)
      .map((t) => { const v = nations.history[t][n.id]; return v && v[i] >= 0 ? v[i] : null; })
      .filter((v) => v != null).slice(-20);
    if (pts.length < 2) return "";
    const max = Math.max(...pts), min = Math.min(...pts), W = 64, H = 16;
    const d = pts.map((v, k) => (k ? "L" : "M") + (k * W / (pts.length - 1)).toFixed(1) + "," +
      (H - 1 - (max === min ? H / 2 : (v - min) * (H - 2) / (max - min))).toFixed(1)).join("");
    return '<svg class="nspark" width="' + W + '" height="' + H + '"><path d="' + d + '" stroke="' + esc(color) +
      '" fill="none" stroke-width="1.5"/></svg>';
  }

  // How a nation compares to you, in words, like the moods in Relations.
  const VERDICT_WORDS = {
    military: { farBelow: "far weaker", below: "weaker", even: "about even", above: "stronger", farAbove: "far stronger" },
    naval: { farBelow: "far weaker", below: "weaker", even: "about even", above: "stronger", farAbove: "far stronger" },
    output: { farBelow: "far poorer", below: "poorer", even: "about even", above: "richer", farAbove: "far richer" },
    colonists: { farBelow: "much smaller", below: "smaller", even: "your size", above: "bigger", farAbove: "much bigger" }
  };
  const VERDICT_LABEL = { military: "Army", naval: "Navy", output: "Economy", colonists: "Size" };
  const VERDICT_CLASS = { farBelow: "ok", below: "ok", even: "info", above: "warn", farAbove: "alarm" };
  const STANCE_CLASS = { war: "alarm", peace: "ok", ceaseFire: "warn", alliance: "info", unContacted: "m" };

  function verdictChips(n, me) {
    if (!n.verdict || !me) return "";
    const v = (x, m) => x.v[natIndex(m)];
    return '<div class="nverd">' + ["military", "naval", "output", "colonists"].map((m) => {
      const w = n.verdict[m];
      if (!w) return "";
      const theirs = v(n, m), ours = v(me, m);
      const times = ours > 0 ? (theirs / ours) : null;
      const tip = VERDICT_LABEL[m] + ": " + (times != null ? "about " + (times >= 10 ? Math.round(times) : times.toFixed(1)) + "× yours" : "you have none") +
        " (" + (m === "military" ? "≈ " + tenth(n.soldiers) + " soldiers vs your " + tenth(me.soldiers)
          : m === "naval" ? "≈ " + tenth(n.frigates) + " frigates vs your " + tenth(me.frigates)
          : m === "output" ? "≈ " + num(theirs) + " vs your " + num(ours) + " gold/turn"
          : theirs + " vs your " + ours + " colonists") + ")";
      return '<span class="tag ' + VERDICT_CLASS[w] + '" title="' + esc(tip) + '">' + VERDICT_LABEL[m] + ": " + VERDICT_WORDS[m][w] + "</span>";
    }).join("") + "</div>";
  }

  // How a computer nation feels about you (single player games only):
  // coloured chips with a small meter, no numbers.
  const MOOD_LV = { hostile: [1, "alarm", "Hostile"], wary: [2, "warn", "Wary of you"],
    neutral: [3, "m", "Neutral"], friendly: [4, "ok", "Friendly"] };
  const TRUST_LV = { distrusts: [1, "alarm", "Does not trust you"], careful: [2, "m", "Careful"],
    trusts: [3, "ok", "Trusts you"] };
  function meter(level, of, cls) {
    let h = '<span class="seg ' + cls + '">';
    for (let i = 1; i <= of; i++) h += "<i" + (i <= level ? ' class="on"' : "") + "></i>";
    return h + "</span>";
  }
  function moodChips(n) {
    const m = n.mood;
    if (!m) return "";
    const a = MOOD_LV[m.word] || MOOD_LV.neutral, t = TRUST_LV[m.trust] || TRUST_LV.careful;
    return '<div class="nverd nmood">' +
      '<span class="tag ' + a[1] + '" title="What they think of you, from the game\'s tension and what they remember: hostile, wary, neutral, friendly.">' +
        meter(a[0], 4, a[1]) + " " + a[2] + "</span>" +
      '<span class="tag ' + t[1] + '" title="Kept deals, gifts and trade raise trust; a broken deal cuts it hard.">' +
        meter(t[0], 3, t[1]) + " " + t[2] + "</span>" +
      '<span class="tag info" title="Their long-term ambition, from their nation type.">' + esc(cap(m.ambition)) + "</span></div>";
  }
  function moodRows(n) {
    const m = n.mood, rows = [];
    if (!m) return rows;
    if (m.goals.length) {
      rows.push(["Wants", m.goals.map((g) => '<span class="ncol">' + esc(g) + "</span>").join(""),
        "What they are after right now (their diplomat's short-term goals)."]);
    }
    if (m.remembers.length) {
      const one = (r) => r.text + " (" + r.year + ")";
      rows.push(["Remembers", m.remembers.slice(0, 3).map((r) =>
        '<span class="ncol mem ' + (r.good ? "good" : "bad") + '">' + esc(one(r)) + "</span>").join("") +
        (m.remembers.length > 3 ? '<span class="m"> +' + (m.remembers.length - 3) + "</span>" : ""),
        "What they remember about you, strongest first (green: in your favour, red: against you). All: " +
        m.remembers.map((r) => (r.good ? "+ " : "- ") + one(r)).join("; ") + "."]);
    }
    if (m.feelings.length) {
      rows.push(["Feels", m.feelings.map((f) => '<span class="ncol rel ' + (f.stance === "war" ? "war" : "ally") + '">' +
        '<i class="sw" style="background:' + esc(f.color) + '"></i> ' + esc(f.name) + " " +
        '<b class="' + (f.stance === "war" ? "down" : "info") + '">' + (f.stance === "war" ? "at war" : "allied") + "</b>, " +
        esc(f.word) + (f.why ? ' <span class="m">(' + esc(f.why) + ")</span>" : "") + "</span>").join(""),
        "How they feel about the nations they are at war or allied with (public: everybody knows who fights whom), and why they chose that war or alliance."]);
    }
    return rows;
  }

  // Where a nation lives: an arrow from our biggest colony, firm when
  // we have seen their colonies, faint while it is a guess.
  function whereRow(n) {
    const w = n.where;
    const deg = Math.atan2(w.dy, w.dx) * 180 / Math.PI;
    const sure = w.known || w.conf >= 30;
    const op = w.known ? 1 : Math.max(0.3, Math.min(1, w.conf / 100));
    const arrow = '<svg class="where" width="18" height="18" viewBox="-9 -9 18 18" aria-hidden="true" style="opacity:' + op.toFixed(2) + '">' +
      '<circle r="8" fill="none" stroke="currentColor" stroke-opacity=".25"/>' +
      '<g transform="rotate(' + deg.toFixed(0) + ')"><path d="M-5 0 H5 M1.5 -3.5 L5 0 L1.5 3.5" fill="none" stroke="' + esc(n.color) + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"' +
      (w.known ? "" : ' stroke-dasharray="2.5 1.5"') + "/></g></svg>";
    const text = w.known ? "<b>" + esc(w.dir) + "</b> · ~" + w.dist + " tiles"
      : sure ? "probably <b>" + esc(w.dir) + "</b> · ~" + w.dist + " tiles · " + w.seen + " seen"
      : '<span class="m">no real idea (' + esc(w.dir) + "?)</span>";
    const tip = w.known
      ? "Their " + w.seen + " colon" + (w.seen === 1 ? "y" : "ies") + " you have seen lie " + w.dir + " of your biggest colony, about " + w.dist + " tiles away."
      : "A guess from what you have seen of them in the last 30 turns (" + w.seen + " sightings: colonies, soldiers, ships and which way they were going), " + w.conf + "% sure. The computer players guess the same way.";
    return ["Where", arrow + " " + text, tip];
  }

  function renderNatCards() {
    const N = nations.nations.slice().sort((a, b) => (b.me ? 1 : 0) - (a.me ? 1 : 0));
    const me = N.find((n) => n.me);
    const maxSol = Math.max(1, ...N.map((n) => n.soldiers || 0));
    const maxFri = Math.max(1, ...N.map((n) => n.frigates || 0));
    const maxOut = Math.max(1, ...N.map((n) => n.output || 0));
    const per = nations.perColonist, sp = nations.soldierPower, fp = nations.frigatePower;
    const html = N.map((n) => {
      const v = (m) => n.v[natIndex(m)];
      const est = n.me ? "" : "≈ ";
      const rows = [];
      // Expansion
      rows.push(['Colonies', "<b>" + v("colonies") + "</b> · " + v("colonists") + " colonists " + natSpark(n, "colonists", n.color),
        "Colonies, and colonists working in them as seen on the map."]);
      // Output
      if (n.output != null && n.output >= 0) {
        rows.push(["Output", "<b>" + est + num(n.output) + "</b> gold/turn " + bar(n.output, maxOut, n.color) + natSpark(n, "output", n.color),
          n.me ? "What your colonies make per turn beyond what they use, at your Europe prices (exact)."
               : "Estimate: " + v("colonists") + " colonists × " + per + " gold, what one of your colonists makes per turn at your Europe prices. " +
                 "If they work less well than yours, it is lower."]);
      }
      // Bells
      if (n.bellsPerTurn != null) {
        rows.push(["Bells", "<b>" + est + num(n.bellsPerTurn) + "</b>/turn · rebels " + v("sol") + "% " + natSpark(n, "sol", n.color),
          n.me ? "Liberty bells your colonies make per turn (exact)."
               : "Estimate from how fast their rebels grow: every new rebel takes 200 bells (rebel share × colonists, over the last turns)."]);
      } else if (v("sol") >= 0) {
        rows.push(["Rebels", v("sol") + "% " + natSpark(n, "sol", n.color), "Share of colonists that supports independence."]);
      }
      // Treasury
      if (v("gold") >= 0) {
        const g = n.goldPerTurn;
        rows.push(["Treasury", "<b>" + num(v("gold")) + "</b> gold" + (g != null ? ' <span class="' + (g >= 0 ? "up" : "down") + '">' + (g >= 0 ? "+" : "") + num(g) + "/turn</span>" : "") +
          " · tax " + v("tax") + "% " + natSpark(n, "gold", n.color),
          "Gold in the treasury (Foreign Affairs report); the change per turn is the average over the last turns: sales and taxes minus purchases."]);
      }
      // Army and navy
      if (n.soldiers != null && n.soldiers >= 0) {
        let tip = "Attack strength " + v("military") + ", as many soldiers as that (a soldier attacks with " + sp + ").";
        if (n.me) {
          tip += " Yours: " + (kindList(n.units, ["soldier", "dragoon", "artillery"]) || "none") + ".";
          if (n.muskets) tip += " In store: " + n.muskets + " muskets (" + Math.floor(n.muskets / 50) + " more soldiers) and " + n.horses + " horses.";
        } else if (kindList(n.units, ["soldier", "dragoon", "artillery"])) {
          tip += " Seen now: " + kindList(n.units, ["soldier", "dragoon", "artillery"]) + ".";
        }
        rows.push(["Army", "<b>" + est + tenth(n.soldiers) + "</b> soldiers " + bar(n.soldiers, maxSol, n.color) + natSpark(n, "military", n.color), tip]);
      }
      if (n.frigates != null && n.frigates >= 0) {
        let line = "<b>" + est + tenth(n.frigates) + "</b> frigates " + bar(n.frigates, maxFri, n.color) + natSpark(n, "naval", n.color);
        let tip = "Sea attack strength " + v("naval") + ", as many frigates as that (a frigate attacks with " + fp + "). Merchant ships do not count.";
        if (n.raids) {
          const r = n.raids, parts = [];
          if (r[0] || r[1]) parts.push("you hit " + (r[0] + r[1]) + (r[1] ? " (" + r[1] + " sunk)" : ""));
          if (r[2] || r[3]) parts.push("they hit " + (r[2] + r[3]) + " of yours" + (r[3] ? " (" + r[3] + " sunk)" : ""));
          if (parts.length) {
            line += '<div class="raid">Sea fights: ' + parts.join(", ") + " · 10 turns</div>";
            tip += " Sea fights with them in the last 10 turns: you damaged " + r[0] + " and sank " + r[1] +
              " of their ships; they damaged " + r[2] + " and sank " + r[3] + " of yours. Damaged ships sail home for repairs and lose their cargo: that is the blockade.";
          }
        }
        rows.push(["Navy", line, tip]);
      }
      rows.push(...moodRows(n));
      if (n.where) rows.push(whereRow(n));
      const seen = n.me ? "" : kindList(n.units);
      if (seen) rows.push(["Seen now", esc(seen), "Their units on the map that you can see right now."]);
      // Colonies, estimated per turn
      const cl = (n.colonyList || []).slice().sort((a, b) => b.value - a.value);
      const cols = cl.slice(0, 6).map((c) => '<span class="ncol" title="' + esc(c.name + ": " + c.size + " colonists, " +
        (n.me ? "" : "about ") + c.value + " gold/turn") + '">' + esc(c.name) + " <b>" + c.size + "</b></span>").join("") +
        (cl.length > 6 ? '<span class="m"> +' + (cl.length - 6) + "</span>" : "");
      return '<div class="ncard' + (n.me ? " me" : "") + '"><div class="nhead"><i class="sw" style="background:' + esc(n.color) + '"></i>' +
        "<b>" + esc(n.name) + "</b>" + (n.me ? ' <span class="tag m">you</span>' : n.met ? "" : ' <span class="tag m">not met</span>') +
        (n.stance && n.stance !== "unContacted" ? ' <span class="tag ' + (STANCE_CLASS[n.stance] || "m") + '">' + esc(cap(n.stance.replace("ceaseFire", "cease fire"))) + "</span>" : "") + "</div>" +
        moodChips(n) + verdictChips(n, me) +
        '<dl class="nrows">' + rows.map((r) => '<dt title="' + esc(r[2]) + '">' + r[0] + '</dt><dd title="' + esc(r[2]) + '">' + r[1] + "</dd>").join("") + "</dl>" +
        (cols ? '<div class="ncols">' + cols + "</div>" : "") + "</div>";
    }).join("");
    if ($("natCards").innerHTML !== html) $("natCards").innerHTML = html;
  }

  // ---------------------------------------------------------------- offers

  let offTurn = null, offAt = 0;
  async function loadOffers(turn) {
    offAt = Date.now();
    try {
      const r = await fetch("/api/offers", { cache: "no-store" });
      const d = await r.json();
      if (d.offers) { offTurn = turn; renderOffers(d.offers); }
    } catch (e) { /* Try again next poll */ }
  }

  const OFFER_STATE = {
    open: ["info", "open"], taken: ["warn", "promised"], done: ["ok", "done"], late: ["warn", "done late"],
    failed: ["alarm", "failed"], declined: ["m", "declined"], expired: ["m", "expired"], cancelled: ["m", "called off"]
  };
  function renderOffers(list) {
    if (!list.length) {
      $("offers").innerHTML = '<p class="m">No offers yet. Computer nations post them on the notice board (first to the nation they like most) and send peace terms when they want out of a war with you.</p>';
      return;
    }
    $("offers").innerHTML = list.map((o) => {
      const st = OFFER_STATE[o.state] || ["m", o.state];
      let what;
      if (o.kind === "peace") what = "Peace" + (o.gold ? " and <b>" + num(o.gold) + "</b> gold for you" : "");
      else what = (o.kind === "want" ? "Wants " : "Sells ") + (o.goods ? gi(o.goods, o.goodsName) : "") + " <b>" + o.amount + "</b> " + esc(o.goodsName) +
        " for <b>" + num(o.gold) + "</b> gold · " + (o.kind === "want" ? "deliver to " : "fetch at ") + esc(o.place);
      let clock = "";
      if (o.left != null) {
        const used = Math.max(0, Math.min(1, (o.deadline - o.left) / o.deadline));
        const cls = o.left > 3 ? "ok" : o.left >= 0 ? "warn" : "alarm";
        clock = '<div class="oclock" title="Turns left to complete the deal; late pays less, very late ends it">' +
          '<span class="nbar"><i class="' + cls + '" style="width:' + Math.round(100 * used) + '%"></i></span> ' +
          (o.left >= 0 ? o.left + " turns left" : (-o.left) + " turns late, pays " + num(o.priceNow)) + "</div>";
      } else if (o.kind !== "peace" && o.state === "open") {
        clock = '<div class="m">' + o.deadline + " turns to deliver once accepted</div>";
      }
      return '<div class="ocard"><div class="ohead"><i class="sw" style="background:' + esc(o.color) + '"></i><b>' + esc(o.from) + "</b>" +
        '<span class="tag ' + st[0] + '">' + st[1] + '</span><span class="m">' + esc(o.posted) + "</span></div>" +
        '<div class="owhat">' + what + (o.paid ? ' <span class="m">· paid ' + num(o.paid) + "</span>" : "") + "</div>" + clock + "</div>";
    }).join("");
  }

  // ---------------------------------------------------------------- expansion replay

  let exp = null, expIdx = 0, expTimer = null, expTurn = null, expBusy = false, expAt = 0;

  async function loadExpansion(turn) {
    if (expBusy || (exp && exp.reading && Date.now() - expAt < 5000)) return;
    expBusy = true;
    expAt = Date.now();
    try {
      const r = await fetch("/api/expansion", { cache: "no-store" });
      const d = await r.json();
      if (d.frames) {
        const atEnd = !exp || expIdx >= exp.frames.length - 1;
        const keep = exp && exp.frames[expIdx] ? exp.frames[expIdx].turn : null;
        exp = d;
        if (turn != null) expTurn = turn;
        expPrep();
        if (atEnd || keep == null) expIdx = exp.frames.length - 1;
        else expIdx = Math.max(0, exp.frames.findIndex((f) => f.turn >= keep));
        renderExpansion();
      }
    } catch (e) { /* Try again next poll */ }
    expBusy = false;
  }

  // Owners of every tile at every frame, from the changes.
  function expPrep() {
    const n = exp.w * exp.h;
    let cur = new Int16Array(n).fill(-1);
    exp.own = exp.frames.map((f) => {
      cur = cur.slice();
      for (let i = 0; i + 1 < f.d.length; i += 2) cur[f.d[i]] = f.d[i + 1];
      return cur;
    });
    exp.land = exp.own.map((o) => {
      const c = new Array(exp.nations.length).fill(0);
      for (let i = 0; i < o.length; i++) if (o[i] >= 0) c[o[i]]++;
      return c;
    });
  }

  function expColor(k) { return exp.nations[k] ? exp.nations[k].color : "#777"; }
  function rgba(hex, a) {
    const p = [1, 3, 5].map((i) => parseInt(hex.substr(i, 2), 16));
    return "rgba(" + p.join(",") + "," + a + ")";
  }

  function renderExpansion() {
    if (!exp || !exp.frames.length) {
      $("expNote").textContent = exp && exp.reading ? "Reading the game's old autosaves..." : "Nothing recorded yet: the replay starts with the next turn.";
      return;
    }
    const slider = $("expTurn");
    slider.max = exp.frames.length - 1;
    slider.value = expIdx;
    const f = exp.frames[expIdx];
    $("expLabel").textContent = f.label + "  (turn " + f.turn + ")";
    drawExpMap();
    drawExpLegend();
    drawExpChart();
    $("expNote").innerHTML = "Only what you knew at the time: land and colonies you had seen, the Foreign Affairs report. " +
      exp.frames.length + " turns recorded, from " + esc(exp.frames[0].label) + "." +
      (exp.reading ? " <b>Reading old autosaves: " + exp.done + " of " + exp.todo + "</b>." : "");
  }

  function drawExpMap() {
    const cv = $("expCanvas");
    const W = cv.parentElement.clientWidth || 800;
    const T = Math.max(3, Math.min(24, W / (exp.w + 0.5)));
    const H = Math.ceil((exp.h + 1) * T / 4);
    const dpr = window.devicePixelRatio || 1;
    cv.width = Math.round(W * dpr);
    cv.height = Math.round(H * dpr);
    cv.style.height = H + "px";
    const g = cv.getContext("2d");
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.fillStyle = "#0b0d10";
    g.fillRect(0, 0, W, H);
    const diamond = (x, y) => {
      const px = (x + (y & 1) * 0.5) * T, py = y * T / 4;
      g.beginPath();
      g.moveTo(px + T / 2, py);
      g.lineTo(px + T, py + T / 4);
      g.lineTo(px + T / 2, py + T / 2);
      g.lineTo(px, py + T / 4);
      g.closePath();
    };
    // Backdrop: land and sea as known now, quiet colours.
    if (mapData && mapData.w === exp.w) {
      for (let y = 0; y < exp.h; y++) {
        for (let x = 0; x < exp.w; x++) {
          const v = mapData.tiles[y * exp.w + x];
          if (!v) continue;
          const p = mapData.palette[(v & 255) - 1];
          diamond(x, y);
          g.fillStyle = p && p.land ? "#2a2d27" : "#121a22";
          g.fill();
        }
      }
    }
    const own = exp.own[expIdx];
    for (let y = 0; y < exp.h; y++) {
      for (let x = 0; x < exp.w; x++) {
        const o = own[y * exp.w + x];
        if (o < 0) continue;
        diamond(x, y);
        g.fillStyle = rgba(expColor(o), exp.nations[o].european ? 0.6 : 0.25);
        g.fill();
      }
    }
    for (const s of exp.frames[expIdx].s) {
      const [x, y, o, size, colony] = s;
      const px = (x + (y & 1) * 0.5 + 0.5) * T, py = (y / 4 + 0.125) * T;
      g.fillStyle = expColor(o);
      g.strokeStyle = "#0b0d10";
      g.lineWidth = 1;
      if (colony) {
        const r = Math.max(2, Math.min(T * 0.6, 1.5 + Math.sqrt(size) * T / 6));
        g.beginPath();
        g.arc(px, py, r, 0, 2 * Math.PI);
        g.fill();
        g.stroke();
      } else {
        const r = Math.max(1.5, T / 5);
        g.fillRect(px - r, py - r, 2 * r, 2 * r);
      }
    }
  }

  function drawExpLegend() {
    const f = exp.frames[expIdx], land = exp.land[expIdx];
    const eu = exp.nations.map((n, k) => k).filter((k) => exp.nations[k].european);
    const maxLand = Math.max(1, ...eu.map((k) => land[k]));
    const colonies = (k) => f.s.filter((s) => s[2] === k && s[4]).length;
    eu.sort((a, b) => land[b] - land[a]);
    $("expLegend").innerHTML = eu.map((k) => {
      const n = exp.nations[k], seen = colonies(k), all = f.c[n.id];
      return '<div class="explrow"><span class="mname"><i class="sw" style="background:' + esc(n.color) + '"></i>' + esc(n.name) + "</span>" +
        bar(land[k], maxLand, n.color) + '<span class="mval">' + land[k] + " tiles</span>" +
        '<span class="mval" title="Colonies seen on the map, and in the Foreign Affairs report">' + seen +
        (all != null && all !== seen ? " / " + all : "") + " colonies</span></div>";
    }).join("") + '<div class="explrow m"><span class="mname"><i class="sw" style="background:#777;opacity:.5"></i>Native land</span>' +
      "<span>shown faint; small squares are their settlements</span></div>";
  }

  function drawExpChart() {
    const svg = $("expChart");
    const W = svg.clientWidth || 600, H = 160, L = 34, B = 18;
    const n = exp.frames.length;
    const eu = exp.nations.map((x, k) => k).filter((k) => exp.nations[k].european);
    const max = Math.max(1, ...exp.land.map((c) => Math.max(...eu.map((k) => c[k]))));
    const X = (i) => L + (n === 1 ? 0 : i * (W - L - 6) / (n - 1));
    const Y = (v) => 6 + (H - B - 6) * (1 - v / max);
    let h = "";
    for (const v of [0, Math.round(max / 2), max]) {
      h += '<line x1="' + L + '" x2="' + W + '" y1="' + Y(v) + '" y2="' + Y(v) + '" stroke="#2a313b"/>' +
        '<text x="' + (L - 4) + '" y="' + (Y(v) + 4) + '" text-anchor="end" fill="#98a2ad" font-size="10">' + v + "</text>";
    }
    for (const k of eu) {
      const d = exp.land.map((c, i) => (i ? "L" : "M") + X(i).toFixed(1) + "," + Y(c[k]).toFixed(1)).join("");
      h += '<path d="' + d + '" stroke="' + esc(exp.nations[k].color) + '" fill="none" stroke-width="1.8"/>';
    }
    h += '<line x1="' + X(expIdx) + '" x2="' + X(expIdx) + '" y1="4" y2="' + (H - B) + '" stroke="#e8c27a" stroke-width="1.5"/>';
    h += '<text x="' + L + '" y="' + (H - 4) + '" fill="#98a2ad" font-size="10">' + esc(exp.frames[0].label) + "</text>" +
      '<text x="' + W + '" y="' + (H - 4) + '" text-anchor="end" fill="#98a2ad" font-size="10">' + esc(exp.frames[n - 1].label) + "</text>";
    svg.setAttribute("viewBox", "0 0 " + W + " " + H);
    svg.style.height = H + "px";
    svg.innerHTML = h;
    svg.onclick = (e) => {
      const r = svg.getBoundingClientRect();
      const x = (e.clientX - r.left) * W / r.width;
      expIdx = Math.max(0, Math.min(n - 1, Math.round((x - L) * (n - 1) / Math.max(1, W - L - 6))));
      renderExpansion();
    };
  }

  function expStop() {
    if (expTimer) clearInterval(expTimer);
    expTimer = null;
    $("expPlay").textContent = "Play";
  }
  $("expPlay").addEventListener("click", () => {
    if (!exp || !exp.frames.length) return;
    if (expTimer) { expStop(); return; }
    if (expIdx >= exp.frames.length - 1) expIdx = 0;
    $("expPlay").textContent = "Pause";
    expTimer = setInterval(() => {
      if (expIdx >= exp.frames.length - 1) { expStop(); return; }
      expIdx++;
      renderExpansion();
    }, +$("expSpeed").value);
  });
  $("expSpeed").addEventListener("change", () => {
    if (expTimer) { expStop(); $("expPlay").click(); }
  });
  $("expTurn").addEventListener("input", (e) => {
    expIdx = +e.target.value;
    renderExpansion();
  });

  // ---------------------------------------------------------------- military

  function renderMilitary() {
    const N = nations.nations;
    const me = N.find((n) => n.me);
    if (!me) return;
    const sp = nations.soldierPower;
    const maxSol = Math.max(1, ...N.map((n) => n.soldiers || 0));
    const maxFri = Math.max(1, ...N.map((n) => n.frigates || 0));
    const sorted = (key) => N.slice().filter((n) => n[key] != null && n[key] >= 0).sort((a, b) => b[key] - a[key]);
    const rows = (key, unit) => sorted(key).map((n) =>
      '<div class="mrow' + (n.me ? " me" : "") + '"><span class="mname"><i class="sw" style="background:' + esc(n.color) + '"></i>' + esc(n.name) + "</span>" +
      bar(n[key], key === "soldiers" ? maxSol : maxFri, n.color) + '<span class="mval">' + (n.me ? "" : "≈ ") + tenth(n[key]) + " " + unit + "</span></div>").join("");
    const fights = N.filter((n) => n.raids).map((n) => {
      const r = n.raids;
      return '<div class="mrow"><span class="mname"><i class="sw" style="background:' + esc(n.color) + '"></i>' + esc(n.name) + "</span>" +
        "<span>you damaged <b>" + r[0] + "</b>, sank <b>" + r[1] + "</b> · they damaged <b>" + r[2] + "</b>, sank <b>" + r[3] + "</b></span></div>";
    }).join("");
    const extraSoldiers = Math.floor((me.muskets || 0) / 50);
    const html =
      '<div class="mgrid">' +
      '<div class="mbox"><h3>Your forces</h3>' +
        '<div class="mline"><b>' + tenth(me.soldiers) + "</b> soldiers' worth on land: " + (kindList(me.units, ["soldier", "dragoon", "artillery"]) || "none") + "</div>" +
        '<div class="mline"><b>' + tenth(me.frigates) + "</b> frigates' worth at sea: " + (kindList(me.units, ["warship"]) || "no warships") +
          (me.units && me.units.merchant ? " (and " + kindList(me.units, ["merchant"]) + ")" : "") + "</div>" +
        '<div class="mline m" title="50 muskets arm one soldier; 50 horses more make a dragoon">In store: ' + gi("model.goods.muskets") + " " + num(me.muskets || 0) +
          " muskets = " + extraSoldiers + " more soldiers · " + gi("model.goods.horses") + " " + num(me.horses || 0) + " horses</div>" +
        '<div class="mline m">Strength counts only armed units: a soldier attacks with ' + sp + ", a dragoon with 4.5, artillery with 10.5, a frigate with " + nations.frigatePower + ".</div>" +
      "</div>" +
      '<div class="mbox"><h3>Army <span class="sub">in soldiers</span></h3>' + rows("soldiers", "") + "</div>" +
      '<div class="mbox"><h3>Navy <span class="sub">in frigates</span></h3>' + rows("frigates", "") + "</div>" +
      '<div class="mbox"><h3>Sea fights <span class="sub">last 10 turns</span></h3>' + (fights || '<div class="m">None recorded yet (counted from now on).</div>') + "</div>" +
      "</div>";
    if ($("military").innerHTML !== html) $("military").innerHTML = html;
  }


  // ---------------------------------------------------------------- independence

  let indep = null, indepTurn = null, indepAt = 0, indepBusy = false;

  async function loadIndependence(turn) {
    if (indepBusy) return;
    indepBusy = true;
    try {
      const r = await fetch("/api/independence", { cache: "no-store" });
      const d = await r.json();
      if (d.colonies) { indep = d; indepTurn = turn; indepAt = Date.now(); renderIndependence(); }
    } catch (e) { /* try again later */ }
    indepBusy = false;
  }

  function pctClass(p) { return p >= 60 ? "ok" : p >= 40 ? "warn" : "alarm"; }
  function pct(p) { return '<b class="' + pctClass(p) + '">' + p + "%</b>"; }

  function renderIndependence() {
    const d = indep, sp = d.soldierPower;
    const declared = d.playerType !== "colonial";
    const mine = d.mine || [];
    const myLand = mine.filter((u) => !u.naval), myShips = mine.filter((u) => u.naval);
    const count = (list, k) => list.filter((u) => u.kind === k).length;
    const refN = (k) => d.ref.filter((r) => r.kind === k).reduce((a, r) => a + r.number, 0);
    const refAtt = (k) => { const r = d.ref.find((x) => x.kind === k); return r ? r.attack10 / 10 : 0; };
    const mySol = d.myLandAttack / sp, refSol = d.refLandAttack / sp;
    const times = mySol > 0 ? refSol / mySol : null;
    const extra = Math.floor(d.muskets / 50);

    // Declaring
    const can = d.sol >= d.solNeeded && d.coastal && d.year <= d.lastYear;
    let h = '<div class="ibanner ' + (declared ? "war" : can ? "ready" : "") + '">' +
      (declared ? "<b>War of Independence.</b> Hold your colonies; the King gives up when he is worn down (see below)."
        : can ? "<b>You can declare independence now</b> (Game menu). Read the checklist first."
          : "<b>Not ready to declare:</b> rebels across all colonies " + d.sol + "% of the " + d.solNeeded + "% needed" +
            (d.coastal ? "" : ", and you need a colony with a port") + ". Deadline: " + d.lastYear + ".") + "</div>";

    // Forces side by side
    const row = (label, you, king, tip) => '<tr title="' + esc(tip || "") + '"><td>' + label + '</td><td class="n">' + you + '</td><td class="n">' + king + "</td></tr>";
    h += '<div class="igrid"><div class="mbox"><h3>Forces <span class="sub">you · the King</span></h3><table class="itable">' +
      row("Soldiers", count(myLand, "soldier"), refN("soldier"), "King's infantry are regulars: each attacks a colony with " + refAtt("soldier") + "; your veteran soldier attacks with 4.5, a colonist soldier with 3.") +
      row("Dragoons", count(myLand, "dragoon"), refN("dragoon"), "King's cavalry attacks a colony with " + refAtt("dragoon") + ".") +
      row("Artillery", count(myLand, "artillery"), refN("artillery"), "Artillery attacks with 10.5 (King's: " + refAtt("artillery") + " against a colony) and defends well behind walls, badly in the open.") +
      row("Warships", count(myShips, "warship"), refN("warship"), "A man-of-war attacks with " + refAtt("warship") + ", your frigate with 24.") +
      '<tr class="tot"><td>Strength</td><td class="n">' + tenth(mySol * 10) + '</td><td class="n">≈ ' + tenth(refSol * 10) + "</td></tr>" +
      "</table>" +
      '<div class="mline">' + (times ? "The King's army is <b class=\"alarm\">" + (times >= 10 ? Math.round(times) : times.toFixed(1)) + "×</b> yours" : "You have no army yet") +
      " in soldiers' worth (game's own measure: you have " + d.ratioLand + "% of the land strength, " + d.ratioNaval + "% at sea).</div>" +
      '<div class="mline m">In store: ' + gi("model.goods.muskets") + " " + d.muskets + " muskets = " + extra + " more soldiers, " + gi("model.goods.horses") + " " + d.horses + " horses.</div>" +
      "</div>";

    // Your units
    h += '<div class="mbox"><h3>Your armed units</h3>' + (myLand.length + myShips.length ? '<table class="itable">' +
      mine.map((u) => '<tr' + (u.seized ? ' class="bad"' : "") + '><td>' + esc(u.name) + (u.veteran ? ' <span class="tag ok">veteran</span>' : "") + "</td><td>" + esc(u.where) +
        (u.seized ? ' <span class="tag alarm" title="Units in Europe or sailing there are seized when you declare">seized if you declare</span>' : "") +
        '</td><td class="n" title="attack / defence where it stands">' + tenth(u.attack10) + " / " + tenth(u.defence10) + "</td></tr>").join("") + "</table>" : '<div class="m">None.</div>') + "</div></div>";

    // Colonies
    h += '<div class="mbox wide"><h3>If the King attacks <span class="sub">chance your best defender holds one attack, now and with what is in the colony</span></h3>' +
      '<table class="itable cols"><tr><th>Colony</th><th class="n" title="Rebels: the King gets this much less popular support">Rebels</th><th>Walls</th><th>Defender now</th>' +
      '<th class="n" title="Against one attack by King\'s infantry / cavalry">Holds now</th><th>Best you can do</th><th class="n">Holds then</th><th class="n" title="Veteran soldiers that become Continental regulars when you declare (rebels above 50%)">Muster</th></tr>' +
      d.colonies.map((c) => '<tr><td><b>' + esc(c.name) + '</b></td><td class="n">' + c.sol + "%</td><td>" + esc(c.walls || "none") + "</td>" +
        "<td>" + (c.defender ? esc(c.defender) + (c.defenderArmed ? "" : ' <span class="tag warn">unarmed</span>') : '<span class="alarm">nobody</span>') +
          ' <span class="m">(' + tenth(c.defence10) + ")</span></td>" +
        '<td class="n">' + pct(c.holdInf) + ' <span class="m">/ ' + c.holdCav + "%</span></td>" +
        "<td>" + (c.bestText ? esc(c.bestText) + ' <span class="m">(' + tenth(c.bestDefence10) + ")</span>" : '<span class="m">' + (c.defenderArmed ? "already the best here" : "no muskets in store") + "</span>") + "</td>" +
        '<td class="n">' + (c.bestText ? pct(c.bestHoldInf) : "") + "</td>" +
        '<td class="n" title="Up to ' + c.musterLimit + " can be mustered here; veteran soldiers here now: " + c.veterans + '">' + (c.musterLimit ? c.muster + " of " + c.musterLimit : '<span class="m">-</span>') + "</td></tr>").join("") +
      "</table>" +
      '<div class="mline m">The King\'s regulars attack a colony with ' + refAtt("soldier") + " plus the colony's tories as a bonus (100% − rebels): a colony with few rebels makes him stronger. Each attack he loses costs him a unit (cavalry first loses its horses).</div></div>";

    // Checklist
    const todo = [];
    const seized = mine.filter((u) => u.seized);
    if (seized.length) todo.push(["alarm", seized.length + " unit" + (seized.length > 1 ? "s are" : " is") + " in Europe (" + esc(seized.map((u) => u.name).join(", ")) + "): bring them home before declaring, or they are lost."]);
    for (const c of d.colonies) {
      if (!c.armed) todo.push(["warn", esc(c.name) + " has no armed defender" + (c.bestText ? ": arm the " + esc(c.bestText.split(" as ")[0]) + " there (holds " + c.bestHoldInf + "% instead of " + c.holdInf + "%)." : ".")]);
    }
    const musterRoom = d.colonies.filter((c) => c.musterLimit > c.muster);
    if (musterRoom.length) todo.push(["info", "Put veteran soldiers in " + musterRoom.map((c) => esc(c.name) + " (room for " + (c.musterLimit - c.muster) + ")").join(", ") + " before declaring: they become Continental regulars (+3 attack and defence)."]);
    const tory = d.colonies.filter((c) => c.sol < 50);
    if (tory.length) todo.push(["warn", "Few rebels in " + tory.map((c) => esc(c.name) + " (" + c.sol + "%)").join(", ") + ": the King attacks there with up to +" + (100 - Math.min(...tory.map((c) => c.sol))) + "%. More bells there, or expect to lose them."]);
    if (extra > 0) todo.push(["info", d.muskets + " muskets in store arm " + extra + " more soldiers; horses make them dragoons."]);
    h += '<div class="mbox wide"><h3>Before you declare</h3>' + (todo.length ? '<ul class="ilist">' + todo.map((t) => '<li class="' + t[0] + '">' + t[1] + "</li>").join("") + "</ul>" : '<div class="m">Nothing to add.</div>') + "</div>";

    // Winning and help
    h += '<div class="igrid"><div class="mbox"><h3>How you win</h3>' +
      '<div class="mline">The King gives up when <b>all</b> of these hold:</div><ul class="ilist plain">' +
      "<li>he holds none of your colonies;</li>" +
      "<li>your army is at least 1.5× what he has left on land;</li>" +
      "<li>he is down to fewer than " + d.landRequired + " land units (now " + d.refLand + ") <i>or</i> fewer than " + d.navalRequired + " ships (now " + d.refNaval + ").</li></ul>" +
      '<div class="mline m">Then you win; you may play on after the victory screen. Europe stays closed; a custom house keeps selling for you.</div></div>' +
      '<div class="mbox"><h3>Help</h3><div class="mline">After declaring, once you make <b>' + num(d.interventionBells) + "</b> more bells, a foreign power sends: " +
      esc(d.intervention.map((x) => x.name).join(", ")) + ".</div>" +
      '<div class="mline m">On declaring, the native tribe that likes you least declares war on you; the one that likes you most makes peace.</div></div></div>';

    if ($("indep").innerHTML !== h) $("indep").innerHTML = h;
  }

  // ---------------------------------------------------------------- routes

  let lastRoutesJson = "";
  function renderRoutes(routes) {
    const j = JSON.stringify(routes.map((r) => [r.name, r.moved, r.movedGoods, r.carriers]));
    if (j === lastRoutesJson) return;
    lastRoutesJson = j;
    $("routes").innerHTML = routes.length ? routes.map((r) => {
      const moved = r.moved || [];
      const max = Math.max(1, ...moved);
      const total = moved.reduce((a, b) => a + b, 0);
      const bars = moved.map((a, i) => {
        const h = a ? Math.max(2, Math.round(40 * a / max)) : 1;
        return '<rect x="' + i * 14 + '" y="' + (42 - h) + '" width="11" height="' + h + '" rx="1" fill="' + (a ? r.color : "#3a414b") + '"><title>' +
          (moved.length - 1 - i === 0 ? "This turn" : (moved.length - 1 - i) + " turns ago") + ": " + a + "</title></rect>";
      }).join("");
      const goods = Object.entries(r.movedGoods || {}).slice(0, 5).map(([g, a]) => gi(g, goodsName(g)) + " " + a).join(" ");
      const stops = r.stops.map((st) => esc(st.name)).join(" → ");
      return '<div class="rcard"><div class="rhead"><i class="sw" style="background:' + esc(r.color) + '"></i><b>' + esc(r.name) + "</b>" +
        ' <span class="m">' + r.carriers + (r.carriers === 1 ? " carrier" : " carriers") + "</span></div>" +
        '<div class="m rstops">' + stops + "</div>" +
        '<div class="rbody"><svg width="' + moved.length * 14 + '" height="44">' + bars + "</svg>" +
        '<div class="rgoods">' + (total ? "<b>" + total + "</b> unloaded<br>" + goods : '<span class="m">nothing unloaded yet</span>') + "</div></div></div>";
    }).join("") : '<div class="m">No trade routes yet.</div>';
  }

  // Tiny bar chart of what a route unloaded in each of the last turns.
  function movedSpark(r) {
    if (!r.moved) return "";
    const max = Math.max(1, ...r.moved);
    const total = r.moved.reduce((a, b) => a + b, 0);
    const goods = Object.entries(r.movedGoods || {}).map(([g, a]) => a + " " + goodsName(g)).join(", ");
    const tip = total ? "Unloaded, last " + r.moved.length + " turns: " + goods : "Nothing unloaded yet";
    const bars = r.moved.map((a, i) => {
      const h = a ? Math.max(2, Math.round(12 * a / max)) : 1;
      return '<rect x="' + i * 4 + '" y="' + (13 - h) + '" width="3" height="' + h + '" fill="' + (a ? r.color : "#555") + '"/>';
    }).join("");
    return ' <svg class="spark" width="' + r.moved.length * 4 + '" height="14"><title>' + esc(tip) + "</title>" + bars + "</svg>";
  }

  function renderLegend(routes) {
    const html = routes.map((r) => '<span><i style="background:' + r.color + '"></i>' +
      esc(r.name) + ' <span class="m">' + r.carriers + (r.carriers === 1 ? " carrier" : " carriers") + "</span>" +
      movedSpark(r) + "</span>").join("") +
      '<span><i style="background:#fff;height:2px"></i>going to</span>' +
      '<span><i style="background:#ffb020"></i>rushing ahead</span>' +
      '<span><i style="background:#ff3b3b;width:8px;height:8px;border-radius:50%"></i>armed foreigners</span>';
    if ($("legend").innerHTML !== html) $("legend").innerHTML = html;
  }

  // ---------------------------------------------------------------- map input

  let drag = null;
  canvas.addEventListener("pointerdown", (e) => {
    drag = { x: e.clientX, y: e.clientY, ox: view.ox, oy: view.oy, moved: false };
    canvas.setPointerCapture(e.pointerId);
  });
  canvas.addEventListener("pointermove", (e) => {
    if (drag) {
      const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
      if (Math.abs(dx) + Math.abs(dy) > 3) {
        drag.moved = true;
        view.auto = false;
        canvas.classList.add("dragging");
        view.ox = drag.ox + dx;
        view.oy = drag.oy + dy;
        $("tip").hidden = true;
      }
      return;
    }
    hover(e);
  });
  canvas.addEventListener("pointerup", (e) => {
    canvas.classList.remove("dragging");
    if (drag && !drag.moved) click(e);
    drag = null;
  });
  canvas.addEventListener("pointerleave", () => { $("tip").hidden = true; });
  canvas.addEventListener("wheel", (e) => {
    e.preventDefault();
    const r = canvas.getBoundingClientRect();
    const mx = e.clientX - r.left, my = e.clientY - r.top;
    const f = Math.exp(-e.deltaY * 0.0015);
    const S = Math.max(4, Math.min(120, view.S * f));
    const k = S / view.S;
    view.ox = mx - (mx - view.ox) * k;
    view.oy = my - (my - view.oy) * k;
    view.S = S;
    view.auto = false;
  }, { passive: false });
  canvas.addEventListener("dblclick", () => { view.auto = true; fitView(); });

  function hitAt(e) {
    const r = canvas.getBoundingClientRect();
    const mx = e.clientX - r.left, my = e.clientY - r.top;
    let best = null, bd = Infinity;
    for (const h of hits) {
      const d = Math.hypot(h.x - mx, h.y - my);
      if (d <= h.r && d < bd) { best = h; bd = d; }
    }
    return { best, mx, my };
  }
  function hover(e) {
    const { best, mx, my } = hitAt(e);
    const tip = $("tip");
    if (!best) { tip.hidden = true; return; }
    tip.innerHTML = best.html;
    tip.hidden = false;
    const r = canvas.getBoundingClientRect();
    const w = tip.offsetWidth, h = tip.offsetHeight;
    tip.style.left = Math.min(r.width - w - 6, mx + 14) + "px";
    tip.style.top = (my + 14 + h > r.height ? Math.max(4, my - h - 10) : my + 14) + "px";
  }
  function click(e) {
    const { best } = hitAt(e);
    if (best && best.colony) flashColonyAt(best.colony.x, best.colony.y);
  }

  // ---------------------------------------------------------------- start

  $("logToggle").addEventListener("click", () => toggleDrawer("log"));
  $("pediaToggle").addEventListener("click", () => toggleDrawer("pedia"));
  $("relToggle").addEventListener("click", () => toggleDrawer("rel"));
  $("logClose").addEventListener("click", () => setDrawer(false));
  document.querySelectorAll(".dtab").forEach((b) =>
    b.addEventListener("click", () => setDrawer(true, b.dataset.tab)));
  document.querySelectorAll(".ptab").forEach((b) =>
    b.addEventListener("click", () => {
      pediaSection = b.dataset.sec;
      $("pediaSearch").value = "";
      $("pediaBody").scrollTop = 0;
      renderPedia();
    }));
  $("pediaSearch").addEventListener("input", () => { $("pediaBody").scrollTop = 0; renderPedia(); });
  // Any goods icon or goods link opens its Colopedia entry.
  document.addEventListener("click", (e) => {
    const t = e.target.closest && e.target.closest("[data-g]");
    if (!t) return;
    e.preventDefault();
    e.stopPropagation();
    openPedia("goods", t.dataset.g);
  }, true);
  $("chipAlerts").addEventListener("click", () => {
    if (!shownWidgets().includes("alerts")) {
      if (!picks.includes("alerts")) {
        picks = WIDGETS.map((x) => x.w).filter((x) => x === "alerts" || picks.includes(x));
        store.set("fc.picks", JSON.stringify(picks));
      }
      setTab("overview");
    }
    $("alertsSec").scrollIntoView({ behavior: "smooth" });
  });
  document.addEventListener("keydown", (e) => {
    if (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA")) return;
    if (e.key === "l" || e.key === "L") toggleDrawer("log");
    if (e.key >= "1" && e.key <= "7" && !e.ctrlKey && !e.altKey) setTab(TAB_KEYS[+e.key - 1]);
    if (e.key === "c" || e.key === "C") toggleDrawer("pedia");
    if (e.key === "r" || e.key === "R") toggleDrawer("rel");
  });
  const pushQuery = window.matchMedia("(min-width: 900px)");
  const setPush = () => document.body.classList.toggle("push", pushQuery.matches);
  setPush();
  if (pushQuery.addEventListener) pushQuery.addEventListener("change", setPush);
  window.addEventListener("resize", () => { resize(); if (market) renderMarket(); if (nations) drawNationChart(); if (state) renderNetwork(state.colonies); });

  applyTab();
  setDrawer(store.get("fc.logOpen") === "1");
  requestAnimationFrame(frame);
  poll();
})();
