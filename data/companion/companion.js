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
    // New turn: prices and Congress change, refresh an open Colopedia.
    if (pedia && pediaTurn !== s.turn && !$("pedia").hidden && !$("log").hidden) renderPedia();

    const cj = JSON.stringify(s.colonies);
    if (cj !== lastColoniesJson) { lastColoniesJson = cj; renderColonies(s.colonies); }
    const gj = JSON.stringify(s.goods);
    if (gj !== lastGoodsJson) { lastGoodsJson = gj; renderGoods(s.goods); }
    const aj = JSON.stringify(s.alerts);
    if (aj !== lastAlertsJson) { lastAlertsJson = aj; renderAlerts(s.alerts); }
    renderLegend(s.routes);
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
    return '<div class="card" data-id="' + esc(c.id) + '">' +
      '<h3>' + esc(c.name) + ' <span class="m">' + c.size + ' colonists · store ' + c.cap +
      '</span><span class="sol" title="Sons of Liberty">SoL ' + c.sol + '%</span></h3>' +
      '<div class="grid">' +
      '<span class="k">Makes</span><div class="items">' + produces + "</div>" +
      '<span class="k">Needs</span><div class="items">' + needs + "</div>" +
      '<span class="k">Stock</span>' + stock +
      '<span class="k">Building</span>' + build +
      (incoming ? '<span class="k">Arriving</span><div class="items">' + incoming + "</div>" : "") +
      "</div></div>";
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

  // ---------------------------------------------------------------- log

  function logClass(e) {
    if (e.kind === "turn") return "turn";
    if (e.kind === "load" || e.kind === "unload" || e.kind === "arrive") return e.kind;
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
          (g[1] < 0 ? "−" + -g[1] : "+" + g[1]) + "</span>").join("") + "</span>";
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
  let drawerTab = store.get("fc.tab") === "pedia" ? "pedia" : "log";

  function setDrawer(open, tab) {
    if (tab) drawerTab = tab;
    $("log").hidden = !open;
    document.body.classList.toggle("logOpen", open);
    $("logList").hidden = drawerTab !== "log";
    $("pedia").hidden = drawerTab !== "pedia";
    document.querySelectorAll(".dtab").forEach((b) =>
      b.classList.toggle("on", b.dataset.tab === drawerTab));
    $("logToggle").classList.toggle("on", open && drawerTab === "log");
    $("pediaToggle").classList.toggle("on", open && drawerTab === "pedia");
    if (open && drawerTab === "log") { unread = 0; showUnread(); }
    if (open && drawerTab === "pedia") renderPedia();
    store.set("fc.logOpen", open ? "1" : "0");
    store.set("fc.tab", drawerTab);
    setTimeout(resize, 220);
  }
  function toggleDrawer(tab) {
    const showing = !$("log").hidden && drawerTab === tab;
    setDrawer(!showing, tab);
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
        html: col ? colonyTip(col) : "<b>" + esc(s.name) + "</b>" });
    }
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

  function renderLegend(routes) {
    const html = routes.map((r) => '<span><i style="background:' + r.color + '"></i>' +
      esc(r.name) + ' <span class="m">' + r.carriers + (r.carriers === 1 ? " carrier" : " carriers") + "</span></span>").join("") +
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
  $("chipAlerts").addEventListener("click", () =>
    $("alertsSec").scrollIntoView({ behavior: "smooth" }));
  document.addEventListener("keydown", (e) => {
    if (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA")) return;
    if (e.key === "l" || e.key === "L") toggleDrawer("log");
    if (e.key === "c" || e.key === "C") toggleDrawer("pedia");
  });
  const pushQuery = window.matchMedia("(min-width: 900px)");
  const setPush = () => document.body.classList.toggle("push", pushQuery.matches);
  setPush();
  if (pushQuery.addEventListener) pushQuery.addEventListener("change", setPush);
  window.addEventListener("resize", resize);

  resize();
  setDrawer(store.get("fc.logOpen") === "1");
  requestAnimationFrame(frame);
  poll();
})();
