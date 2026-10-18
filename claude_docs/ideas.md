# FreeCol UI mod: ideas and status

Goal: make FreeCol's UI workable, give proper overviews, a companion
page for a second screen, and computer nations and tribes that act with
reasons. Fork `mischadeq/freecol`, branch `colony-overview` (cloud
work arrives on `claude/...` branches, Mischa merges).

Last updated: 2026-10-17 (749 unit tests, all green).

How to read this file:
1. **Where things stand**: every feature in one line, with its status.
2. **To check in the game**: what Mischa has not seen working yet.
3. **Known problems and loose ends**, **Decided**, **Ideas, not
   started**.
4. **Build log**: what was built when and why, oldest first, with the
   tests. Later entries can change earlier ones; the summary at the top
   is the current state.

Status words: **seen OK** = Mischa saw it working. **in use** = part of
Mischa's game every turn, no complaint left open (not formally ticked).
**to check** = built and unit-tested, not yet seen in a game.
"Auto-checked" / "sandbox-checked" in the log means Claude ran the real
game in an automated session: a smoke test, not a play test.

Aim: release the mod once Mischa is content with it.

## Where things stand

### Game screens and orders

| Feature | Where | Status |
| --- | --- | --- |
| Colony Overview: main products, needs, stocks, build, sortable, supply forecast in Needs | Report, Shift+F6 | seen OK (layout); forecast colours to check |
| Supply warnings at the start of the turn (no carrier, nothing coming, too late, held up and now late) | turn report | to check (never seen on screen) |
| Logistics report with route map and "moved, last 10 turns" bars | Report, Shift+F7 | to check |
| Goods Overview (goods × colonies) | Report, Shift+F8 | to check |
| Unit Overview with filters | Report, Shift+F10 | to check |
| Unit groups: Form / Leave / Disband Group, march together, Rush (Shift+R) | Orders | to check |
| Execute-orders button with count, hover list, end-turn warning; routes and go-to wait for the player | bottom bar | in use (count and bar fixes to re-check) |
| Unit badges as symbols, hover explanations, View > Unit Badges | map | to check |
| New trade route editor (map, stops table, warnings); routes do not haul to a full colony | Trade Routes | to check |
| Warehouse dialog explains Low / High / Keep | colony | to check |
| Borderless full screen on the monitor the game is on | Preferences > Display | in use |
| Undo: Restart This Turn / Go Back One Turn | Game menu | to check |
| Prices in the Europe amount dialog, treasury line on the Europe screen | Europe | to check |
| Join colony and learn (soldier at the palisade to a teacher) | colony | to check |
| Unexplored map as an old chart | Preferences > Display | in use (freeze fixed) |
| Natives: gift part of a load; haggling when buying fixed | native trade | to check |
| Trade with another nation's colony: what they sell in tens, price, haggling; high/low balling hint | trade screen | tried once (Add fixed), to check again |
| Send an Envoy: peace, alliance, join my war, leave the coalition, gift; tribes too; trade (price agreement, notice board) | Report > Send an Envoy | to check |
| Post a Trade Offer (notice board, price agreement) | Report | to check |
| Offer dialogs (peace terms, calls to arms, notices, agreements, ultimatums, buy-outs, tribe offers, envoy terms) | start of turn | in use |
| War of Spanish Succession as a war, then a partial cession; game option | game options | to check (new game) |
| A new game starts with the game and map options chosen last time | new game screen | to check |
| Notice-board deals: move a ship or wagon train into their colony to deliver or fetch at once | map | to check |

### Companion page (http://localhost:8765, View > Open Companion Page)

| Feature | Status |
| --- | --- |
| Tabs (Overview with widgets, Colonies, Trade, Nations, Diplomacy, Military, Independence, History), keys 1-8 | in use |
| Live map, alerts, colony cards (Liberty gauge, advice chips), empire goods | in use |
| Supply network graph (directions kept, networks side by side) | in use; far colonies to check |
| Europe market prices and chart; route charts | in use |
| Log drawer with routine colony messages, important words highlighted | in use |
| Relations drawer (homefront, nations, tribes per settlement); Colopedia drawer | in use |
| Nations cards: figures, verdict chips, moods, memories, where they live; ranking table and chart | in use |
| Tribe cards on the Nations tab and tribes in the Military army bars | to check |
| Diplomacy tab: table, wars and alliances between others, coalitions, relations graph, offers, tribe cards | in use (seen 2026-10-17) |
| Independence tab | to check |
| History tab (expansion replay from old autosaves) | to check |

### Computer nations and tribes (running in Mischa's game)

| Step | What | Status |
| --- | --- | --- |
| 0 | Replay of autosaves to tune the diplomat (`ant replay`) | done |
| 1 | Wars can be fought: landings across the sea, targeting fix | in use |
| - | Where they live: sightings, guesses, scouting ships | in use |
| - | The computer arms colonists, buys a ship for an invasion, keeps a treasury | in use |
| 2 | Moods and memories, trust | in use |
| 3 | War and peace between computer nations by the diplomat; offers, notice board, trade ships | in use |
| 4 | Price agreements by ship | in use |
| 5 | Alliances as pacts, joint wars, coalitions (buy-outs, joint strikes) | in use |
| 6 | Grievances and ultimatums before a war | in use |
| 7 | Tribes: know each other, rivals, calls for help, refugees, allied settlements, tribes as allies, unrest and uprisings, Pontiac | in use |
| 8 | The player's envoys (nations and tribes), encroaching means taking land | to check |
| - | The King's wars no longer poison relations; tension fades in peace | in use |

## To check in the game

Short list of what has not been seen working yet, newest first.

- **Envoys** (Report > Send an Envoy): a gift, a peace envoy with gold,
  terms in the popup, "join my war" with a nation and with a tribe
  (price per brave, alliance), leaving a coalition, the trade lines.
- **War of Spanish Succession** (new game): the war message, the treaty
  6 turns later, which colonies go, the abandoned ones, the rest of the
  loser still alive.
- **Tribe cards** on the Nations tab, tribes in the Military army bars.
- **Tribes and land:** a colony on a tribe's border does not anger it;
  one on land taken without paying does.
- **Trade screen** at another nation's colony: Add, the amount in tens,
  haggling, a deal at their price.
- **Supply network** with a far-away colony.
- **Execute orders:** the hover list matches the units on the map; the
  count drops after pressing; the bar stays when no unit is active.
- **Older, never seen on screen:** supply warnings, Logistics / Goods /
  Unit Overview reports, unit groups and Rush, unit badges, the trade
  route editor, the warehouse dialog, Undo, Europe price dialog and
  treasury line, join colony and learn, natives' gift part and haggle,
  Independence and History tabs.

## Known problems and loose ends

- **Error when moving onto a French tile while at war** (FreeCol.log,
  2026-10-16). Mischa: leave it for now. Not looked at.
- **Envoy menu item is hard to find** (in the long Report menu).
  Offered: a keyboard shortcut, or a button by each nation in Foreign
  Affairs. Open.
- **A European nation's price to join your war** (500-3500, doubled
  when not allies, up to 10,000) may be too steep next to the tribes'
  per-brave price. Asked Mischa whether to price it by their soldiers.
  Open.
- Left over from the steps (small, none urgent):
  - envoy deals in gold only (no goods);
  - the computer does not sell to you under a price agreement (only
    buys), and agreements do not make war less likely;
  - computer nations do not take each other's notices; no cancelling a
    promise; no counter-offers in the offer dialog;
  - the coalition founder cannot be bought out;
  - terms other than gold in an ultimatum ("withdraw your soldiers");
  - the King still declares wars at random (idea: pay to stay out);
  - tribes: no dedicated war-party mission; no help between tribes in
    wars between tribes;
  - history charts for the tribes on the page;
  - old chart map: guessed strokes beyond the edge, the minimap.

## Decided

- **Needs column stays icons only.** There are few enough goods to
  remember, so no names there.
- **Food stays in "Mainly produces" for now.** To reconsider later:
  it shows up as a main product for many colonies (Food +3, +4), which
  is accurate but says little about what a colony is specialised in.
  One option is to hide it unless it is a real surplus, say +8 or more.
- **The companion page is read-only:** the player acts in the game.
- **Honest view:** nations, tribes and the page use only what they
  could know (see `plans/companion-ui.md`).
- **A war out of pure hatred stays in;** local allies against the
  King's global wars; soldiers never stand in an ally's colony.
- **Envoys:** one per nation every 5 turns, free, the answer next turn;
  a price only when trusted; tribes put survival first; an abandoned
  colony is disbanded; a shared border is not encroaching.
- **Spanish Succession:** partial cession, small colonies abandoned,
  each colonist of the bigger ones leaves with a 1 in 10 chance; two
  computer nations picked at random, the stronger more likely wins.

## Ideas, not started

### Nations' economies (Mischa, 2026-10-17, for later)
- Nations specialise (one makes coats, another sugar), and the
  computer players build fewer, bigger colonies that produce more.
- Europe's prices react to every nation's sales: if Spain and France
  sell tons of coats, the coat price drops for everyone.
- First: Mischa starts a new game to see how the AI builds now.

### Unit groups, next steps
- Choose which units on a tile join a group, instead of all of them.
- Show the group on the map (a banner or number on the unit).
- "Attack with the group" as an explicit order.
- A list of groups with a button to jump to each.

### Companion page, next steps
- Filter the map to one trade route or one group (left out for now).
- Arrivals of a group are logged once when the members arrive together;
  members arriving in different moments still get a line each.
- Later, maybe: let the page give simple orders. Kept read-only for now
  (decided with Mischa).

### Logistics, next steps
- Suggested routes: link a colony that needs a good to one with a
  surplus, straight from the overview.
- Open the trade route editor from the Logistics report.
- Check the wagon limit in the base rules. The `shippableWagons` mod's
  copy of the wagon definition limits wagon trains to fewer than the
  number of colonies. If the base rules have it too, a small companion
  rules mod could lift it.

### Colony overview
- A totals row for the whole empire.
- Option to show gross production instead of net.
- Show hammers / construction capacity as a product.

### Other overviews
- Turn messages: group and filter end-of-turn reports, click to go to
  the colony concerned.
- Goods Overview: freeze the first columns when scrolling sideways.

### Colony screen and map
- Colony screen: show what a tile or building would yield before
  committing a worker to it.
- Map overlays: tile ownership, potential production, colonies with
  problems.

### Wars and the computer players
- Checked in the source on 2026-10-13: the King declares war and peace
  on your behalf at random; computer soldiers only sought targets within
  8 moves; a bug dropped enemy units in the open. The bug and the
  landings are done (step 1); still open: an option so the King's wars
  need real tension, and a nation paying to stay out of the King's war.

### Housekeeping
- Decide later whether any of this is worth offering upstream. If so,
  `claude_docs` should stay out of that pull request.
- Release: Mischa wants to release the mod once he is content with it.
  Before that: a list of what changed for players, and the options to
  turn features on or off.

## Build log

Oldest first. Each entry: what was built, why, what was tested, what
was left out. Checkboxes from the old "to be tested" lists are gone:
the status lives in the tables above.

### 2026-10-11: setup, the first Colony Overview

- **Setup.** JDK 25 and Ant 1.10.18 installed, game builds with `ant`
  and runs from our own source. Fork created, `upstream` points at
  FreeCol/freecol, `origin` at the fork.
- **Mods survey.** Read all existing mods (1 in freecol-mods, 5 linked,
  20 shipped in `data/mods`). None touch reports or overviews, and the
  mod system cannot load Java code, so UI work has to go in the source.
- **Colony Overview report, first version** (Report menu, Shift+F6).
  One row per colony: size, top 3 net production, needs, largest
  stocks, warehouse capacity, current build. Tooltips explain every
  number. Click a colony name to open it.
- **Debug listing of all nations' colonies.** With `--debug-run N` (or
  any debug mode with menus) in single player, the report also lists
  the AI nations' colonies in gray, so there is real data to test with.

- **Housekeeping: empty wrapper repos removed** (2026-10-13). The
  folders `Freecol\freecol` and `Freecol\mods` were git repos with no
  commits, no remote and no stash around the real clones; their `.git`
  folders are gone. The real clones (`freecol\freecol`,
  `mods\freecol-mods`) are untouched.

### 2026-10-11: Colony Overview checks, supply forecast and warnings

Quick checks (2 minutes, `java -jar FreeCol.jar --debug-run 60`, click
Start Game, then Report > Colony Overview or Shift+F6):

- **Smaller building icons.** Tested by Mischa 2026-10-12: OK. Auto-checked: rows are even.
- **Building column colours.** Tested by Mischa 2026-10-12: OK. Red only for "Nothing"; magenta for
      "needs N more ..."; dark gray when it is simply counting turns.
      Auto-checked for "Nothing" only.
- **Sortable columns.** Tested by Mischa 2026-10-12: OK. Click each of the seven headers: the rows
      reorder, an arrow appears on the sorted header, a second click
      reverses. Close and reopen the report: the sort is remembered.
      Auto-checked for "Mainly produces" and "Needs".

Longer check (about 15 minutes, needs your own colonies and a trade
route; start with `java -jar FreeCol.jar --debug` and use Debug > Add
Gold to buy a wagon train):

- **Supply forecast in the overview.** Colony B burns lumber
      (colonist in the carpenter's house), a wagon on a trade route
      loads lumber at A and unloads at B. In the overview, lumber under
      Needs for B is green and the tooltip names the wagon, the route
      and the arrival time.
- **Broken supply line in the overview.** Take the wagon off the
      route: lumber for B turns red, the tooltip says no wagon train
      or ship is assigned.
- **Start-of-turn messages appear in the real game.** Auto-checked
      on 2026-10-12: after ending a turn the game produced "St. John's
      relies on trade route Test run for Lumber, but none of its
      carriers will bring any ..." and "Charlesbourg Royal relies on
      trade route Back haul for Lumber, but no wagon train or ship is
      assigned to that route any more ...". Still worth seeing on
      screen yourself.
- **Start-of-turn message: no carrier.** End the turn after taking
      the wagon off. A message says B relies on the route but nothing
      is assigned to it. It does not repeat on the following turns.
- **Start-of-turn message: nothing coming.** Wagon assigned, but A
      has no lumber to spare.
- **Start-of-turn message: too late.** B runs out before the wagon
      can arrive.
- **Start-of-turn message: delayed.** Block or hold the wagon for
      a turn while it is on its way, with so little in store that the
      hold-up makes it late (since 2026-10-13 a hold-up that still
      arrives in time gives no message).
- **Building column, goods on their way.** A colony whose build
      waits for tools, with a carrier bringing enough: the cell reads
      "Tools arriving in N turns".

The supply warnings pass their unit tests (all four message kinds),
and the start-of-turn check ran in the automated game without errors,
but no supply message has been seen on screen yet.

Known limits of the supply warnings (by design, not bugs):

- The forecast assumes carriers visit every stop in order. The game
  lets them skip stops with no work, so times are estimates.
- "Delayed" compares with last turn's expectation, which is kept in
  memory only. After loading a save the first turn has nothing to
  compare with.
- A carrier that spends a turn loading counts as one turn delayed. If
  that is too noisy, add a threshold.
- A delay on the very last leg into the colony is not detected.
- A broken supply line is noticed at the start of the next turn, not
  at the moment the wagon is reassigned. The overview shows it
  immediately.

### 2026-10-12, round 1: unit groups and the first reports (commits 0893f2ac5 / 860678de9)

- **Unit groups: armies, wagon trains, fleets.**
      - Orders menu: Form Group (Shift+G), Leave Group, Disband Group.
      - Form Group takes all your units on the active unit's tile that
        travel the same way (land or sea) and asks for a name. If the
        active unit is already in a group, the others join it.
      - One order for the whole group: go to a tile or colony, assign
        a trade route (carriers only), fortify, sentry. A step with
        the arrow keys also moves the members standing with the unit,
        but only for a plain move; attacks, boarding and trading stay
        with the unit you ordered.
      - The info panel (bottom right) shows "Army 1 (3 units)".
      - Groups are kept per game in your FreeCol user data folder
        (`unitgroups/<game id>.properties`), so they survive save and
        load. They exist on your machine only; the game rules, the AI
        and multiplayer are untouched.
      - Auto-checked: group shown in the info panel; a goto order
        reached all three members; assigning one wagon to a route
        assigned all three. Unit test for the group registry passes.
      - Not checked at all: the Form/Leave/Disband menu items and
        their dialogs, arrow-key moves, fortify/sentry for a group.
      - Known limits: members move one after the other, each finding
        its own path, so they can arrive on different turns. There is
        no way yet to pick only some units on a tile.
- **Logistics report** (Report menu, Shift+F7). Every trade route
      with its stops and what each stop loads, what it delivers to
      which colony and when (green in time, red too late or nothing
      coming), its carriers with group, position, cargo and next
      deliveries, and the wagon trains and ships without a route.
      Auto-checked: renders correctly, including a route with no
      carriers. Not seen yet: a carrier actually on its way with a
      delivery time.
- **Goods Overview report** (Report menu, Shift+F8). One row per
      goods type, one column per colony, totals in front. Each cell
      shows stock and change per turn; red for overflow or nearly
      gone, magenta for draining or nearly full. Auto-checked: renders
      correctly with 4 colonies. Not seen yet: many colonies (the
      table scrolls sideways).
- **Unit Overview report** (Report menu, Shift+F10). Every unit
      not at work inside a colony: group, where, what it is doing,
      moves left. Filters: All, Idle, Soldiers, Wagon trains, Ships,
      In a group. Click a unit to select it on the map. Auto-checked:
      renders, filters work, destinations show turns to go. Not
      checked: clicking a unit.
- **Colony Overview made narrower.** Tested by Mischa 2026-10-12: OK. Only the main product is
      named, the other two show icon and amount; three stocks instead
      of four; tighter spacing. Auto-checked: the horizontal scrollbar
      is gone at a 1500 pixel wide window.

### 2026-10-12, round 2: groups march together, Logistics map

- **Groups march together; Rush to Destination.** A normal go-to
      order for a group now keeps it together: the slowest member moves
      first each turn and the faster ones go no further than its tile.
      Orders > Rush to Destination (Shift+R) lets every member go at
      full speed, so the fastest arrive first; the info panel then
      shows "(rushing)". The next normal go-to order makes the group
      march together again. Auto-checked: after the first go-to order
      all four members of a test army ended the turn on the same tile;
      the Rush menu item is present. Not checked: several turns of
      marching, and the rush itself moving units ahead.
- **Map on the Logistics page.** A map strip at the top, zoomed to
      the area the trade routes cover. Each route in its own colour as
      a dashed line through its stops with an arrow for the direction;
      the route names below get the same colour. Wagon trains and
      ships as circles at their real position, with the path they will
      take to their next stop, the goods they carry and the turns to
      get there. Hover for details; click to close the report and look
      at that spot on the main map. Auto-checked: renders and fills the
      strip. Not checked: a carrier on its way (path and turns), hover,
      click.

### 2026-10-12, round 3: execute orders, unit badges

- **Execute Orders button.** New round button at the right end of
      the bottom bar (fast-forward sign). A red number shows how many
      units still have go-to orders or trade routes to carry out this
      turn; greyed out when there are none. Same as Orders > Execute Go
      To Orders (O). Sandbox-checked: count 4 at turn start, button
      moved the army, count back to 0.
- **End-turn warning.** Ending the turn while units still have
      orders asks "End turn anyway / Not yet". Option: Preferences >
      Other > "Warn about orders not carried out". Sandbox-checked:
      "Not yet" keeps the turn and the orders.
- **Unit badges as symbols.** ! waiting for orders, zz no moves
      left, arrow on its way, circular arrow trade route, eye sentry,
      shield outline fortifying, filled shield fortified, double
      chevron skipped, hammer pioneer at work, plus under repair,
      house in a colony. Only your own units; other nations keep the
      letters. Option: Preferences > Interface > Map View > "Unit
      badges as symbols" (off = classic letters).
- **Badge explanations on hover.** Rest the mouse on your units on
      the map: a tooltip explains each unit's badge in words. Option:
      "Explain unit badges on hover".
- **View > Unit Badges.** A legend of all badges with the classic
      letter next to each.
- Sandbox results for Round 3 (2026-10-12): an arrow-key step moved
  all four army members (step 13 OK); marching together kept the
  scout with the soldiers; Rush sent the scout ahead (3 tiles further).

### 2026-10-12, round 4: the companion page and many small fixes

- **Companion page for a second monitor.** FreeCol now serves a
      live, read-only page at http://localhost:8765 (this computer only,
      no Docker needed). View > Open Companion Page opens it in the
      browser. Portrait layout, top to bottom: header (year, turn, gold,
      tax, alerts / orders / idle counters), live map (routes as moving
      dashed lines, wagons and armies sliding along their paths, cargo
      and turns next to carriers, group labels, rushing in amber, armed
      foreigners ringed red; scroll to zoom, drag, double-click to fit),
      alerts (supply-aware, click to show on the map), colony cards
      (makes, needs, stock bars, building with progress, deliveries
      arriving), empire goods with a trend line. A hideable log sidebar
      (button or L) lists per turn what the carriers loaded and unloaded,
      arrivals, new or lost colonies and the game's messages, repeats
      counted. Options: Preferences > Other > "Companion page for a
      second monitor" (on) and "Companion page port" (8765); restart
      after changing. The page files are in data/companion/ and can be
      edited without rebuilding. Sandbox-checked with Mischa's own save
      (Autosave 1497): route set up, 3 turns played, page followed along,
      wagons loaded at Alpha, Army 2 arrived at Bravo.
- **Colopedia on the companion page** (Mischa: "connect the wiki").
      Colopedia button in the page header (or C) opens a second tab in
      the sidebar, next to Log. Start: what to do right now in your game
      (colonies building nothing, pending orders, idle units), Congress
      status, and a 10-step beginner guide (food, money, hammers, tools
      and pioneers, bells and SoL, crosses, schools, wagons, defence, the
      King). Goods: description, Europe prices, made from / made into,
      made in which building, which expert, used in, needed for.
      Buildings, Units, Founding Fathers (in Congress / being elected).
      Search box across everything. Clicking any goods icon anywhere on
      the page opens its entry. Built from the rules of the game in play,
      so it matches mods. Link to the online manual
      (https://freecol.org/docs/FreeCol.html). Sandbox-checked.
- **Borderless full screen.** Exclusive full screen made the game
      flicker black whenever the other monitor got the focus (Windows
      minimises it, FreeCol restores it). New option Preferences >
      Display > "Borderless full screen (for two monitors)", on by
      default: a window without borders over the whole screen instead.
      Restart after changing. Not testable in the sandbox (no full
      screen there): Mischa to check.
- **Undo: Game > Restart This Turn / Go Back One Turn.** FreeCol
      has no undo (the server decides everything), but it already saves
      at the start of every turn (Autosave-<game>_<nation>_<year>.fsg).
      These two menu items reload that save after a confirmation, for
      mistakes like disbanding the wrong unit. Sandbox-checked: Restart
      This Turn reloads the game at the start of the turn; with no save
      for a turn it says so.
- **Borderless full screen leaves the taskbar free.** Windows drew
      its taskbar over the bottom of the borderless game (hiding the
      order buttons) when the other monitor had the focus. The game now
      fills the screen above the taskbar. Mischa to check.
- **Prices in the amount dialog (Europe).** When buying from or
      selling to the market in Europe (Shift+drag), the dialog shows the
      price as you type: "Cost: 20 × 3 = 60 gold, gold afterwards ..."
      (no tax on purchases) or "Sale: ... tax 10% = ..., you receive ...
      gold afterwards ...", and warns about boycotts. Same formulas as
      the server (ServerPlayer.buyInEurope / sellInEurope).
      Sandbox-checked (screenshots of both).
- **Treasury line on the Europe screen**, next to the buttons: gold,
      tax, taxes paid so far this game, trade balance (sales after tax
      minus purchases), from the market's own records. Updates every
      second. Sandbox-checked.
- **Europe market section on the companion page**: tax, taxes paid
      and trade balance; a price chart per goods (you sell for / you
      buy for, step lines, hover for the turn, colours validated for the
      dark background, buy line dashed); a table of all goods with sell
      and buy price, change over 10 turns, how much you sold, tax paid.
      Click a row to chart it. The game does not keep old prices, so
      they are recorded each turn to companion/prices-<game>.csv in the
      FreeCol user folder: the history starts with this version and
      grows as you play. Sandbox-checked with seeded history.
- **Relations tab on the companion page** (R, or the Relations
      button). Homefront: tax, rebels %, colonies, Congress (next father
      and bells to go), immigration (crosses), Europe (docks, ships,
      recruit price), boycotts with the cost to lift, the King's army.
      European nations: met or not, stance, known colonies. Native
      tribes you have met: stance, worst mood, tribe profile from the
      rules (settlements, aggression, usual skills), and per known
      settlement: capital, mood, skill taught, the three wanted goods
      (+50/+25/+10%), missions, most hated nation. Click a settlement to
      show it on the map. Only what the game lets you know.
      Sandbox-checked with Mischa's save (Tupi, English, French).
      Wanted goods change over time, so each settlement shows "as of
      turn N" (when this session last saw them change), the log gets a
      line "<settlement> now wants ..." when they change, and hovering a
      native settlement on the map shows its skill and wanted goods.
      Not yet seen live: a change being logged (needs a visit).
- **claude_docs/tribes.md**: the eight tribes, from the rules and
      the trade code: settlements, temper, region, richness, skills with
      odds, what they sell, how prices work, mood, haggling odds,
      teaching rules (capitals teach repeatedly), missionary vs scout vs
      soldier.
- **claude_docs/freecol-advisor-prompt.md**: a prompt to paste into
      Claude Haiku 5.5 (or a Project) to ask FreeCol questions. Contains
      the verified core rules, the tribe guide, our extra tools, and
      tables of buildings, units and founding fathers generated from the
      rule files.
- **Fix: asking natives for a lower price when buying failed.**
      Reported by Mischa (90 furs at an Iroquois village). FreeCol bug,
      not ours: the haggle offer is sent with the price unset, it
      overwrites the server's copy of the trade, and the server then
      found nothing buyable ("Can not buy"). Selling already skipped
      that check while haggling; buying now does the same
      (server/control/InGameController.nativeTrade). Unit test
      NativeTradeHaggleTest fails without the fix with the exact error
      from Mischa's log, passes with it. Natives may still refuse to
      haggle further, that is the normal rule.
- **Fix: wagons on an edited route counted as "no carrier".**
      FreeCol's own TradeRoute.getAssignedUnits() compares route objects
      by identity, and after a route is edited the client keeps two
      copies. The Colony Overview, Logistics report, supply warnings and
      companion page now match carriers by route id instead. Found in the
      sandbox: the game warned "no wagon train is assigned" for a route
      with two wagons. Unit test added.
- **Companion page: nations ranking.** A section under Colonies
      that puts the European nations side by side: expansion (colonies,
      colonists, colonists per colony, a bar per colony by size, units),
      economy (gold, tax), military (army and navy strength) and politics
      (rebels %, fathers). Each cell has its rank and a bar; the chips on
      top give your rank per group, and a line says where you are ahead
      and behind. Click a column to sort by it and see its trend over the
      turns (you in gold, the others grey). Figures come from the same
      server query as the Foreign Affairs report (rebels, fathers and tax
      of others need Jan de Witt), colony sizes from the map. Recorded
      each turn to companion/nations-<game>-<player>.csv. Files:
      CompanionNations.java (new), CompanionServer (/api/nations),
      client InGameController (record at turn start), page files.
- **Gift part of a load to the natives.** Choosing a gift now asks
      how much to give (all of it by default) instead of always handing
      over the whole hold. The server values the part itself, from its
      own copy of the trade, so a client can not inflate the value, and
      the rest stays on offer. Files: NativeTradeItem (partMatcher,
      withAmount), NativeTrade (removeGiftFromUnit), server
      InGameController and NativeAIPlayer (match a part), client
      InGameController (amount dialog). Unit test NativeTradeGiftTest.
- **Colony advice that keeps the supply network intact** (Mischa:
      warn when a colony is suboptimal, but Arnhem must keep feeding
      Nijmegen). Each colony card on the companion page gets a Network
      row (what it sends where along which route, and how much the
      receiver is short per turn) and an Advice list: a supplier that
      cannot cover the shortfall it is meant to cover, the tory penalty
      or being at its edge, nobody in the town hall (who could move
      there, or that nobody can be spared), bells made versus bells the
      colonists use (each colonist past the second uses one a turn, so
      one colonist in the town hall often wins no rebels at all), swaps
      of two colonists that only gain, experts with a free place in their
      own trade, goods a route brings that go no further (Nijmegen's
      coats), goods nothing uses, muskets and horses with no soldier.
      Every move or swap is checked first: it may not bring anything
      below what the colony's routes must deliver, and may not turn a
      surplus into a shortage. Grain counts as food. Only advice; nothing
      is moved. Bells no longer show as "runs out in 0 turns". Files:
      client/control/ColonyAdvisor.java (new), CompanionState, page
      files, messages. Unit test ColonyAdvisorTest (duty from a route,
      the food supply can not be cut, the farmer is never sent to the
      town hall, a swap that only gains is found).
- **Companion page says more with less** (Mischa: "a load of
      text"). A Supply network graph above the colonies: colonies placed
      as on the map, an arrow per route connection with the goods on it
      and how much the receiver lacks per turn; grey keeps up, red the
      supplier makes too little, dashed amber nothing to send; a gold
      arc for rebels, an amber or red ring at or past the tory limit, a
      goods box beside a colony where deliveries pile up. Click a colony
      to find it on the map and its card. On the cards the Network row
      and the advice sentences became a Liberty gauge (a pip per
      colonist: rebels gold, tories grey, past the limit red, a dashed
      red pip when the next tory brings the penalty; bells made/used;
      bells to 50%) and short chips with the full advice on hover.
- **High balling or low balling in the trade dialog.** When trading
      with a computer nation, the middle of the Trade Negotiation dialog
      says what the deal is worth to them and whether the gold asked is
      High balling (with the counter offer they will make, or that they
      will refuse because they do not have it), Fair, or Low balling
      (with what they would pay). It follows the computer's own rules
      (EuropeanAIPlayer.acceptDiplomaticTrade), with our prices standing
      in for theirs. Unit test TradeAppraisalTest checks it against the
      real computer player. Found on the way: the computer counts goods
      it gives you as a gain, and its counter offer is the gold asked
      minus half the deal's value, often more than the deal is worth;
      both left as they are, the estimate follows them.
- **Fix: the Trade Negotiation dialog was cut off on the right**
      (the right column and the Send button). The window was sized
      before the wrapped texts in the middle were laid out; it is now
      sized again once they are. The texts in the middle wrap narrower.
- **Routine colony messages go to the companion page** (Mischa: too
      many warnings in the turn report, move them to the page, do not
      crowd both). While the page is open in a browser (it asked for the
      state in the last 30 seconds), messages about missing or
      overflowing goods, rebels and production, market prices, trade
      route goods and arrivals stay out of the turn report and appear in
      the page's log; the colony cards already show their cause. When
      the report opens anyway, one line says how many went to the page.
      Combat, demands, losses, buildings finished and other news stay in
      the game. Option: Routine colony messages on the companion page
      (on by default). Tested on Mischa's save: the report went from four
      lines to the emigrant plus "3 routine colony messages are in the
      companion page's log". Unit test RoutineMessagesTest.
- **Warehouse dialog explains its numbers** (Mischa: "I just see 10
      90 50"). Each box now labels them Low %, High % and Keep (and
      Import with enhanced trade routes), with the amount in store under
      the icon instead of beside the numbers; one line under the title
      says what they mean: Low and High are warnings as a share of the
      warehouse, Keep is an amount that stays, trade routes and the
      custom house take only what is above it. Tool tips say the same.
- **Trade routes do not haul goods to a full colony** (Mischa: a
      warning every time a settlement is full; note it is full and do not
      take those goods at the stop before). Better than a flag noted on
      the last visit: before loading, the carrier looks at the colony
      where it will unload the goods (the next stop that does not list
      them) and loads only what fits there on arrival: the warehouse
      less what is in store and what the colony itself makes on the way.
      Nothing fits: the goods stay where they are ("Nijmegen has no
      room"). Only our own colonies, only goods with a storage limit
      (not food), only without the enhanced trade routes game option,
      which already does this. The warning came from goods above the
      warehouse capacity being thrown away at the end of each turn.
      Unit test TradeRouteRoomTest.

### 2026-10-13: screens, routes, companion tabs, Independence, old chart

- **Full screen on the right monitor** (Mischa: View > Full screen
      jumped back to the vertical monitor; moved over with Win+Shift+
      arrow it kept the vertical monitor's width, so either the order
      buttons were under the taskbar or the game did not use the full
      width). Cause: FreeCol picked a monitor once at start-up and
      always went full screen there. Now full screen uses the monitor
      the window is on; a borderless game moved to another monitor
      (Win+Shift+arrow) resizes to that monitor (minus the taskbar); and
      the monitor is remembered, so the next start opens there. New
      class ScreenChooser (client/gui), changes in FreeColFrame, Canvas
      (toggleFrame) and SwingGUI (start-up monitor). Note: the automatic
      scale factor follows the start-up monitor's height, so starting on
      the wide monitor can change the size of text and icons; Preferences
      > Display scaling sets it by hand. Unit test ScreenChooserTest
      (vertical 1080x1920 next to a wide screen). Could only be partly
      tried in the sandbox (its virtual monitors overlap): going full
      screen from the wide monitor did size the game to that monitor.

- **Fewer supply warnings: only delays that make a delivery late.**
      A carrier that is held up but still arrives before the colony runs
      out no longer gives a message. When the hold-up does make it late,
      one message says so: "Wagon train on trade route X is held up by
      1 turn and will now be too late: Wijchen runs out of tools in 1
      turn (8 left), the delivery needs 2 turns." Late from the start
      (not a hold-up) keeps the old "too late" message; neither repeats
      while the line stays late. SupplyWatch.check, string
      supply.delayed. Unit tests in SupplyForecastTest: testWatch (a
      harmless hold-up is quiet), testDelayOnlyWarnsWhenLate,
      testLateWithoutDelay.

- **What each route moved, as a small graph.** Every unload by a
      carrier on a trade route is noted per route and turn (last 20
      turns, kept in `companion/routes-<game>-<player>.csv` in the user
      data folder, so it survives quitting; loading an older save or
      Restart This Turn forgets the turns that will be played again).
      Logistics report: under each route a row "Moved, last 10 turns:"
      with a bar per turn (hover a bar: what was unloaded that turn) and
      the main goods with totals. Companion page: a tiny bar chart after
      each route in the map legend, hover for the goods. Counting starts
      when the new version runs; older turns show empty. New classes
      RouteHistory (client/control) and MovedChart (client/gui/panel);
      hook in InGameController.unloadUnitAtStop; CompanionState routes
      JSON gets "moved" and "movedGoods". Unit test RouteHistoryTest.
      Tried on Mischa's save: two turns ended, the report showed bars
      for all six routes (e.g. Ore to Wijchen: 48 tools, 8 ore, 2 food).
      Also: client/control AllTests now lists all the mod's tests.

- **New trade route editor** (Mischa: the old dialog is clunky;
      wanted a map in it; carriers stay assigned from the unit or group).
      One screen: name, Silence messages, how many carriers. A small map
      with the route and all colonies; click a colony to add it as a
      stop. "Add stop:" buttons for every colony and Europe (adds after
      the selected stop). The stops as a table: # | Stop | ▲ Loads (a
      chip per goods, "×2" for two holds; click a chip to take a hold
      off) | ▼ Unloads (worked out from the stop before, which the game
      never showed) | In store (amount and per turn for the goods
      involved; red when a loading stop has nothing above Keep, or an
      unloading stop is full; hover for Keep and warehouse size) | ↑ ↓ ✕.
      Delete key removes the selected stop. Goods row: click (or drag
      onto a stop) to load one more hold at the selected stop. Warnings
      at the bottom: fewer than two stops, goods loaded at every stop
      (never unloaded), the same place twice in a row, a stop that loads
      and unloads nothing, more holds than the smallest carrier has, a
      colony asked to load goods it neither has nor makes. A refused OK
      no longer changes the route (the old dialog had already changed
      it). The Trade Routes list now shows per route: name, the stops
      (Arnhem → Nijmegen → …), carriers, and the moved chart.
      Rewritten TradeRouteInputPanel; new RoutePlan (client/control);
      TradeRoutePanel row renderer; LogisticsMap.includeAllColonies.
      Unit test RoutePlanTest. Tried on Mischa's save: opened Food Coat
      to Nijmegen and GUNS (the editor flags StinkyTown and
      Chattahoochee on GUNS as loading and unloading nothing), and on
      Ore to Wijchen added muskets at Nijmegen by clicking: the route
      was saved with ore + muskets and Wijchen showed muskets under
      Unloads.

- **Fix: a soldier at the palisade could not be made a student**
      (Mischa: picking "Assign to teacher" on the soldier next to the
      dragoon in Nijmegen sometimes did nothing, and the teacher said it
      needs a student). Trace on his save: the soldier stands on the
      colony tile, outside the colony; only colonists working inside
      can be taught, so the game refused without a word, while the menu
      still offered it. Now the menu says "Join colony and learn" for
      such a unit (tool tip: it puts down its arms, which go to the
      warehouse, and takes a job), and choosing it joins the colony
      first, then assigns the teacher. When the teacher already has a
      student, the item says so: "· replaces Free Colonist (2/4)".
      Units aboard a wagon or on another tile are not offered it.
      InGameController.assignTeacher and canJoinToLearn,
      QuickActionMenu.addEducationItems. Unit test JoinToLearnTest.
      Tried on Mischa's save: the soldier (unit:6673) joined Nijmegen,
      muskets 114 → 164, and became the veteran's student.

- **Companion page in tabs, with plain-language nations and a
      Military tab** (Mischa: a lot of scrolling; a 1–4 rank says
      nothing; economic power was shallow; sinking their ships should
      show). Chose: tabs plus an Overview with picked widgets (C), all
      widgets available, honest figures only (what you could know).
      - Tabs: Overview · Colonies · Trade · Nations · Military (keys
        1–5). Overview shows the widgets ticked under ⚙ Widgets (map,
        alerts, supply network, colony cards, empire goods, Europe
        prices, route charts, nations, military), remembered in the
        browser. The alerts chip switches to the alerts.
      - Nations: a card per nation. Output in gold per turn (ours exact:
        colony surpluses at our Europe prices; theirs ≈ their colonists
        × what one of ours makes); bells per turn (theirs ≈ from how fast
        their rebels grow, 200 bells a rebel); treasury and its change
        per turn; army in soldiers (a soldier attacks with 3) and navy
        in frigates (24), with a bar against the others; units seen now;
        sea fights; their colonies with sizes. Hover for how each is
        worked out. Ranking table and chart stay below.
      - Military: your forces (units, muskets and horses in store and
        how many more soldiers that arms), army and navy bars for all
        nations, sea fights per nation over the last 10 turns.
      - Sea fights: counted from the game's combat messages (theirs
        damaged / sunk by you, ours by them), after each attack and at
        the start of each turn; kept in companion/raids-<game>.csv.
        Counting starts now; earlier fights are not known.
      - Trade routes widget: a card per route with stops, carriers, a
        bar per turn of what it unloaded and the main goods.
      New: NationEstimate, CompanionRaids; CompanionNations adds the
      figures; index.html, companion.js, companion.css. Unit test
      NationEstimateTest. Tried on Mischa's save (screenshots of all
      tabs): Dutch army 4 soldiers' worth (1 soldier, 2 dragoons; 214
      muskets in store = 4 more), French ≈ 15, English ≈ 14, Spanish ≈
      29; navy Dutch 1.8 frigates, others 0; output Dutch 406 gold/turn,
      French ≈ 302, English ≈ 282, Spanish ≈ 283.

- **Nations in words, like the moods in Relations** (Mischa: "content"
      says more than 1/100; a 1–4 rank says nothing). Each nation card
      now has tags comparing it with you: Army / Navy far weaker, weaker,
      about even, stronger, far stronger; Economy far poorer … far
      richer; Size much smaller … much bigger (red when they are ahead,
      green when you are; hover: "about 3.8× yours (≈ 15.3 soldiers vs
      your 4)"), plus their War / Peace stance. Rule: under half, under
      80%, within a quarter, up to double, more than double
      (NationEstimate.verdict).
      **A graph per nation** (Mischa: only one overall graph): army,
      navy, output and treasury get a small line on every card, next to
      colonists and rebels. Output is now recorded per turn (new measure
      in companion/nations-<game>.csv; older files still load) and is a
      column in the ranking table and chart, and the Economy group ranks
      on output, not only gold and tax. Unit test
      NationEstimateTest.testVerdictInWords. Tried on Mischa's save after
      two turns: Spanish "Army: far stronger, Navy: far weaker, Economy:
      poorer, Size: smaller".

- **Independence tab on the companion page** (Mischa: worried about
      the King's force; wants his units against theirs, quality and the
      expected result). Tab 6 (also a widget): whether you can declare
      (rebels across all colonies vs 50%, port, deadline year); forces
      side by side (soldiers, dragoons, artillery, warships) and
      strength in soldiers' worth (on his save: 12.7 against ≈ 290, 23×,
      the game's own measure gives him 9% of land strength); his armed
      units with attack/defence and where they are, flagged "seized if
      you declare" when in Europe (his 5 veterans were); per colony:
      rebels, walls, the defender now and its chance to hold one attack
      by King's infantry / cavalry, the best defender possible with what
      is in the colony (armed, fortified) and its chance, and how many
      veterans would muster into Continental regulars; a "before you
      declare" list; how the King gives up (holds no colony, your army
      1.5× his remaining land force, his land units under 7 or ships
      under 2); and the foreign help after 3,500 bells. Attack and
      defence are worked out with the same modifiers as the game's
      combat model (attack bonus, the REF's bombard bonus, popular
      support from tories, walls, fortify); unit tests check both
      against the combat model for real units (5 attack cases, 18
      defence cases). New: CompanionIndependence, /api/independence.
      Unit test IndependenceTest.

- **Test version: the unexplored map as an old chart** (Mischa: the
      flat blue is bland; old maps fade out, "a mountain range fades out
      but we know it continues"; no spoilers). Unexplored tiles are aged,
      muted paper (one seamless texture anchored to the map, so no tile
      pattern). The last three rows of the explored map fade: colour
      turns into a sepia ink sketch (dark parts become brown strokes,
      light parts the paper), then into the paper; the sea under it
      dissolves into the paper too. The amount is blended over each
      tile from its corners, so there are no steps. Shows nothing that
      has not been seen. Option: Preferences > Display > "Unexplored map
      as an old chart" (on). First try (bright parchment, real coastline
      sketched ahead) was dropped: too bright, and a spoiler.
      Not yet done: faint guessed strokes beyond the edge that continue
      what is known (step 3), the minimap. Performance to watch on a
      big screen (pixel pass over the frontier tiles on each redraw).
      ParchmentPainter (client/gui/mapviewer), hooks in MapViewer and
      TileViewer, client option unexploredParchment. Unit test
      ParchmentPainterTest (seamless, anchored, muted).

### 2026-10-13: the living diplomacy plan

Full plan: `claude_docs/plans/ai-diplomacy-and-natives.md` (snapshot of
the Claude Doc "FreeCol: living diplomacy design").
- **What:** every computer nation gets a "diplomat" with opinion,
  memories, trust and fear of each other nation, a long-term ambition,
  short-term goals and a score function; it makes offers (goods,
  purchases, trade agreements, pacts, joint wars, coalitions against
  the strongest), complains before going to war, and plans landings.
  Native tribes get the same: opinions of each other, calling for help,
  refugees, settlements or whole tribes joining a European nation, joint
  uprisings.
- **Why:** wars start and stop at random, nobody fights for land or
  makes an offer, and the tribes are isolated and hardly matter.
- **Decided:** at most one offer per nation per 5 turns; nations stay
  committed to their goals; deals between computer nations too; no
  off switch; a native settlement that joins you becomes an allied
  native settlement (stays native, cheap trade with each other).
- **Open:** what Hard should mean for the diplomat; whether wealth
  should count more than 40% of threat.
- **Step 0, next:** replay Mischa's consecutive autosaves (copies only,
  never written to), find what he and each AI did per turn, and compare
  with what the diplomat would have done, to tune the weights.
- **Step 0 build plan** (2026-10-13): `claude_docs/plans/step0-replay.md`.
  New pure package `common/diplomacy` (world view, relations, threat,
  goals, effects, risk, scorer, diplomat) and a replay tool in
  `tools/replay` (save series, turn diff, observed moves, hindsight,
  report). The score function is written out per move, with its
  assumptions (A1–A14). No game behaviour changes in step 0.

### Step 0 built: the replay (2026-10-14)
- **What:** `ant replay -Dsaves=<autosave folder> -Dprefix=Autosave-6c7a41f5_ -Dout=replay -Ddeclared=iroquois`
  reads every autosave of one game (read-only), lets each European
  nation's diplomat score its moves from that nation's honest view,
  compares with what the next save shows it did, and judges the 10
  turns after. Writes `replay.csv`, `replay.json` and `replay.html`
  (nations, a turn strip, wars, the ledger). 243 saves take 15 s.
- **Diplomat** (`common/diplomacy`, nothing in the game uses it yet):
  `WorldViewBuilder` (honest: Foreign Affairs report via
  `NationSummary`, colonies on explored tiles, public wars; hindsight:
  everything), `NationCharacter` (weights from the nation type),
  `Memory`/`Relations` (opinion from tension and fading memories),
  `Threat`, `Goal`, `CaptureSim` (fights played out 400 times with the
  game's attack/(attack+defence)), `Situation`, `Effects` (each move's
  gold, trade, land, security, relations and risk), `Diplomat` (rank
  and choose), `ScoredAction` (keeps every part of the score).
  `Estimates` and `CombatMath` moved here from the companion
  (`NationEstimate`, `CompanionIndependence` now call them).
- **Replay** (`tools/replay`): `SaveSeries`, `Snapshot` (the facts of
  one save, plain data), `TurnDiff` (wars and who started them, peace,
  captures, destroyed settlements, units lost, ships damaged, arming,
  missions, Europe sales), `Observed`, `Hindsight`, `Replay`,
  `ReplayReport`, `ReplayMain`; page template `data/replay/report.html`.
- **Changes after the first run:** goals only amplify a move (bonus
  grows with what the move brings by itself); opinion counts only for
  war, peace, alliances and attacks (for trade it only changes the
  chance they accept); trade volume counts at a quarter of the gold;
  plunder added (100 gold per native, a colony's share of its owner's
  gold); tribes raid instead of taking colonies (a quarter of the
  colony's value as risk); Mischa's own word decides who started his
  wars (tension rises after an attack, so it can not tell).
- **Tested:** `DiplomacyTest` (16: threat worked example, opinion and
  fading, capture odds match the game rule, characters, parts add up,
  peace with a strong near enemy, no war on a strong neighbour, attack
  on a weak colony, ships needed across the sea, boycotted surplus,
  goals only amplify), `ReplayTest` (7: file names to turns, who
  declared, the King's wars, Mischa's word, peace, captures and
  losses and arming, missions); companion tests unchanged and green.
- **First findings (Mischa's game, 1493 to spring 1669):** all four of
  the King's wars on the Dutch score −96 to −121 for the Dutch diplomat
  (none would have been chosen); the French war on the Dutch in 1658
  came from tension alone with no Dutch colony in their reach (−24);
  Spain's war on the Apache in 1664 is the only war with a reason
  (+23). No European colony changed hands in 244 turns. Mischa's own
  war on the Iroquois scored −78 (the Iroquois army next door), yet
  the next 10 turns went better than usual for him (treasure); his
  arming matched the diplomat 7 times out of 17 moves.

### Step 0, tuning round 1 (2026-10-14)
- **Decided with Mischa:** count the land a place frees, the trade lost
  with a tribe, a new move "arm a tribe against a rival", the King's
  army as a reason to arm, a lower bar for small moves; keep opinion at
  full weight (past deals and alliances should hold a nation back).
  His idea: let a nation stay out of a war the King declares, for gold
  or a boycott.
- **Changes:** `WorldViewBuilder.freed` (tiles a place owns within two
  of my colonies, best production at my prices, double for goods my
  colonies are short of, half worked, 30 turns; land weight of a
  trading empire 0.3 → 0.6); `NationView.tradeWithMe` from my own
  `LastSale`s with the tribe (last 20 turns); `Effects.threatOf`
  (tribes count a quarter: they raid); `Effects.kingThreat` (0 below
  20% rebels, full at 50%, only while the King's army is bigger);
  `Action.Kind.ARM_TRIBE` (muskets or horses to a tribe next to the
  rival we fear most, never an ally; seen in saves through the arms
  `LastSale`s); `Diplomat.SMALL_THRESHOLD` 5 for trade and arming
  (war, peace, alliances keep 20); `Effects.kingsWarPrice` (100 gold
  per enemy soldier, at least 500, or a 20-turn boycott of the best
  seller, whichever is cheaper) shown as "Stay out?" in the wars table;
  a move now agrees if it is in the top 3 or scores its bar.
- **Result:** the diplomat agrees with 15 of Mischa's 21 moves (was 7
  of 17); the Iroquois war went from −78 to −33 (still a no: he was
  friends with them and the war cost him trade); the King's four wars
  stay at −96 to −121, and the diplomat would have paid to stay out of
  all three of his (1400 to 2033 gold, or a coats boycott).
- **Tested:** `DiplomacyTest` +6 (freed land and lost trade, tribes
  raid, the King's army, small-move bar, arming a tribe, staying out of
  a King's war), `ReplayTest` +1 (arms sold to a tribe).

### Step 1 built: wars can be fought (2026-10-14)
- **Targeting bug fixed:** `UnitSeekAndDestroyMission.scorePath` dropped
  the score of enemy units in the open (since upstream commit
  91a611c74), so computer soldiers only ever went for settlements, and
  never took a target of opportunity in the open.
- **Attacks across the sea:** new `server/ai/military/InvasionPlanner`,
  called from `MilitaryCoordinator` every turn. At war, it asks the
  nation's diplomat (honest view) which attack is worth it (score 20 or
  more), picks the smallest force of spare units that takes the place
  65% of the time (at most 8), gives them a seek-and-destroy mission on
  it, raises their transport priority to 200 and books them on the
  ships at once; units already on their way are left alone and booked
  again each turn while they wait. Spare: waiting in Europe, or not the
  last armed unit of its colony, and never from a colony under attack.
  One attack at a time; by sea only with free room on a ship.
- **Found while testing:** the client crashed at end of turn in
  `followTradeRoute` when a trade route's working stop list ran empty
  (an IndexOutOfBounds in your 1669 save); guarded (separate commit).
- **Tested:** `UnitSeekAndDestroyMissionTest.testFindsEnemyUnitsInTheOpen`
  (fails without the fix); `InvasionPlannerTest` (4: smallest winning
  force, ships an army to another island with a transport destination,
  a whole AI turn puts the army on the move, colonies under attack keep
  their units); server AI tests (43) green. In the sandbox with the
  1669 save and the English put at war with the Dutch: the English
  diplomat chose Valkenburg, sent an artillery from Nonsuch and one from
  Europe, and with a galleon to carry them the Europe one sailed.
- **What it shows about the game:** the computer nations rarely can
  attack anyone: they have explored almost nothing (Spain at war with
  the Iroquois and Apache knows none of their villages), have fewer
  armed units than colonies, and one or two small ships. Step 3 (the
  diplomat acting: arming, buying ships, scouting) is what makes wars
  common.

### Where are they: guesses and scouting (2026-10-14)
Mischa's idea: nobody gets the map for free, but a nation can work out
roughly where the others live from what it sees of them.
- **Guesses:** `common/diplomacy/Bearings` and `Sightings`. Once a turn
  each nation notes, on the tiles it can see, the colonies, soldiers and
  ships of the others (at most 4 of each kind per nation per turn). Each
  one points from its biggest colony towards where that nation lives;
  colonies count 3, soldiers 2, ships 1, a course 1.5; old ones fade over
  30 turns. A unit seen twice gives a course: on land, or a ship along
  the coast, coming nearer means it came from home; a ship sailing east
  or west towards the open sea came from home, one sailing in goes home.
  Visitors say little: sightings near our colony count less (squared, up
  to 6 tiles) and a ship heading straight at us, away from us, or at our
  door gives no course. The guess is the weighted average direction;
  confidence is how much they agree times how many there are.
- **Checked on your 243 saves (replay, new report section):** before
  they ever found your colonies, the French guessed right (within 45
  degrees) 21 times out of 21 usable guesses, the Spanish 19 of 25. The
  English only ever saw your traders at their door and now say "no real
  idea" instead of pointing the wrong way (the first version did, which
  is how the visitor rules came about). No computer nation found a Dutch
  colony in 244 turns.
- **Scouting:** the diplomat's SCOUT move (at war, or the leader, with
  no colony of theirs known and a guess). New `ScoutPlanner` (after the
  military coordinator) sends one ship at a time: a warship, or an
  empty trading ship that is not the only ship. New
  `ScoutBearingMission` sails to open water at the guess, then zigzags
  on (35 degrees left, then right, 8 tiles a leg); done when one of
  their colonies is in sight, given up after 12 turns. The invasion
  planner now uses the guesses too.
- **Saved:** the computer players' sightings go into the save (the
  "sightings" attribute of each AI player), and so does the mission.
- **Companion page:** a "Where" row on each nation card: an arrow from
  your biggest colony, solid to their colonies you have seen, dashed
  and faint while it is a guess ("probably north-east, ~25 tiles, 4
  seen"). Your own sightings are kept in
  `companion/bearings-<game>-<player>.txt`.
- **Tested:** `BearingsTest` (7), `SightingsTest` (2), a SCOUT test in
  `DiplomacyTest`, `ScoutPlannerTest` (2: sends the frigate west, it
  sails, survives save and load, stops when the colony is seen; open
  water near a point), a replay summary test; all 530 tests green. In
  the sandbox with the 1669 save, Spain at war with you and its last 30
  turns of sightings: it chose to look north-west and sent the spare
  galleon (26,26 to 16,17 in a turn). It made peace with you two turns
  later anyway (its top move all along).

### The computer arms its colonists and buys ships (2026-10-14)
Next of the small moves: the computer now does what its diplomat says.
- **Arming:** new `server/ai/military/WarPreparations`, run each turn
  before the military coordinator. When the diplomat's ARM move scores
  5 or more (in danger, or the King's army), up to 4 colonists a turn
  take the muskets in store (a dragoon when there are horses too),
  least skilled first, never leaving a colony under 2 workers. They get
  a defend mission, so the coordinator can also send them to attack.
- **Found while doing it:** the colony planner counted every defender
  as a free worker ("FIXME: drop this when the AI stops building
  excessive armies") and took the muskets back each turn. That is why
  computer nations had fewer soldiers than colonies. Colonists the
  diplomat armed now stay armed while the nation is in danger and for
  20 turns after (kept in the save, "keptArmed").
- **Buying a ship:** new move BUY_SHIP. When an enemy colony across the
  sea would be worth taking but the ships have no room for the army,
  the diplomat weighs buying the cheapest ship that carries 4: worth
  80% of the attack it makes possible, minus 40% of its price (the ship
  stays ours). In a test, a 6-colonist colony scores 12.9 for a 2000
  gold merchantman; a 2-colonist colony is not worth one.
- **Scouting fix:** scouting needs a guess at least 30% sure. Before,
  the English "looked south-west, ~3 tiles" for 20 turns on a guess
  that was really "no idea".
- **In the sandbox** (1669 save, 3 turns): the French, at war with the
  Arawak and "in danger", armed 6 colonists over two turns (5 to 11
  soldiers) and sent three artillery at Xaymaca. Nobody bought a ship:
  no computer nation in that game is at war with someone across the
  sea that it knows.
- **Tested:** `WarPreparationsTest` (arms in danger, the dragoon, kept
  armed and released after 20 turns; buys a ship only with gold),
  `DiplomacyTest` (buy a ship, arming worth more in danger, no scout on
  an unsure guess); all 534 tests green.
- **Not yet:** looking around with no guess at all (the English case);
  buying soldiers on the diplomat's word (the old Europe buying rules
  still run as before).

### Step 2 built: moods and memories, live (2026-10-14)
Computer nations now remember what others did to them during a real
game, and the player can read their mood on the companion cards.
- **Live memories (2a):** new `common/diplomacy/Memories`. Each turn
  the computer player compares the game with last turn
  (`Snapshot`/`TurnDiff`/`Observed`, moved from `tools/replay` to
  `common/diplomacy`) and the same code turns that into memories for
  the replay and the live game (`Memories.remember`). Only what the
  nation itself saw: its own units lost and ships damaged, wars on it,
  colonies changing hands. Wars, peace and conquered colonies still
  come from the game's own history (no double count any more: the
  replay counted a captured colony twice).
- **New memory kinds:** `DEAL_KEPT` (a peace that held 20 turns),
  `DEAL_BROKEN` (they broke a fresh peace or attacked us while allied),
  `GIFT` and `TRADE` (accepted diplomatic trades), `HELPED_IN_WAR`
  (they took a colony of our enemy), `REFUSED_OFFER` (they turned our
  offer down). A peace starts as a `TREATY` on probation (no feeling).
  Each kind has a weight, a fade time and a trust change; trust is
  remembered twice as long as the feeling.
- **Trust:** `Relations.trust` = 50 plus kept deals, gifts, trade and
  help, minus broken deals (−40 each, hard). Fills `NationView.trust`,
  which the diplomat already used for alliance and trade risks.
- **Saved** with the game ("memories" attribute on the AI player).
  Last turn's snapshot is not saved: the first turn after a load
  misses its changes.
- **Once per turn:** new `Outlook` (world view + situation + ranking).
  `EuropeanAIPlayer` builds it once at the start of its turn, with its
  memories; `WarPreparations`, `InvasionPlanner` and `ScoutPlanner`
  share it instead of each building their own (and none passes `null`
  memories any more).
- **Companion cards (2b):** in a single player game, each computer
  nation's card on the Nations tab shows chips: mood (🙂 friendly,
  😐 neutral, 🤨 wary, 😠 hostile), trust (🤝 trusts you, ⚖️ careful,
  ✋ does not trust you) and ambition (🎯 trading empire, conqueror,
  ...). Rows: "Wants" (its goals in words: "sell furs", "make peace
  with you"), "Remembers" (the 3 strongest memories about you with the
  year, 👍/👎; all on hover), "Feels" (its mood towards nations it is
  at war or allied with). Goals about other nations show only for
  their public wars. Multiplayer: nothing shown.
- **Tested:** `MemoriesTest` (fading, treaty to kept deal,
  save/parse, trust up and down, losses/broken peace/King's war/help
  from two snapshots, strongest first, mood words), `AIMemoriesTest`
  (a soldier lost in a live game becomes a memory and lowers the
  opinion, trust in the outlook, save and load keeps memories, trades
  become gifts/trades/refused offers), `MoodsTest` (the nations JSON
  has mood, trust, remembers; a secret grudge against a nation at
  peace does not show). The companion tests now run with the rest:
  all 559 tests green.
- **Try in the game:** sink or damage a French ship while at war: next
  turn the French card should turn wary or hostile and remember it.
- **Not yet:** a war the human declares counts as the King's (a save
  does not say who declared), so breaking a peace with a computer
  nation does not yet give `DEAL_BROKEN`; trades between two computer
  nations leave no memory (they do not trade with each other yet);
  units lost by a nation at war with two or more others are not
  blamed on anyone (the save does not say who killed them).

### Step 3a built: computer nations choose war and peace (2026-10-14)
Between two computer nations, war, peace and alliances now come from
the diplomat instead of the tension rule.
- **How:** `EuropeanAIPlayer.determineStances` asks the diplomat (the
  turn's `Outlook`) for DECLARE_WAR / MAKE_PEACE / ALLIANCE towards
  each other computer nation; it acts at a score of 20 or more.
- **Both sides:** a war is one side's choice; peace needs the other's
  diplomat not to be against it (score 0 or more), an alliance needs it
  to want one too (5 or more).
- **Commitment** (new `common/diplomacy/Commitments`): a choice holds
  at least 10 turns; after that a different choice must score 30% more
  than the old one did. A shock lifts both: war declared on us (not by
  the King), a colony of ours taken or destroyed. Choices and the last
  shock are saved with the game ("stanceChoices").
- **Why, in words:** each choice keeps its main reason ("revenge",
  "wants their colonies", "war is too dangerous", "hates them", ...).
  The companion card shows it in the "Feels" row:
  "Spain: ⚔ hostile · revenge".
- **Unchanged:** wars with the human player (old tension rule, war on
  you should come with a grievance first: step 6), with the tribes, the
  King's wars, the REF.
- **Who declared:** a save does not say; the replay guesses from
  tension. In a live game the computer diplomats now tell the snapshot
  which wars they chose, so a broken peace gives `DEAL_BROKEN` and a
  shock even when the declarer was not hateful.
- **Tested:** `CommitmentsTest` (only strong war/peace/alliance moves,
  the 10-turn hold, 30% better, a shock lifts it, what is a shock,
  reasons in words, save/parse), `AIStancesTest` (the diplomat decides
  only between computer nations; angry is not enough for war; old rule
  towards the human; choices saved and loaded), `MemoriesTest` (a
  diplomat's war breaks a peace). All 570 tests green.
- **To look at:** full hatred (tension 1100) with nothing else to gain
  scores exactly 20, just enough for a war "because it hates them".
  That matches the old rule (war at 1010); should a war need a real
  gain too?
- **Not yet:** the replay check on Mischa's saves ("own wars with a
  reason"); the replay still guesses declarers from tension. Offers
  (3b) not started.
- **Decided with Mischa:** a war out of pure hatred stays in ("a nation
  I have vexed since the start should hate me and try to get rid of
  me").

### Companion: no emojis, charts back to the start, History tab (2026-10-14)
- **No emojis** (Mischa: colours, charts and meters instead). Mood and
  trust are coloured chips with a small segment meter (hostile 1 of 4
  .. friendly 4 of 4; distrusts 1 of 3 .. trusts 3 of 3); memories
  and wars/alliances get a green/red/blue edge; ambition is a plain
  chip; the sea-fight line says "Sea fights:". The gear on "Widgets"
  and the ▶▶ on "orders" (both can show as colour emoji) are gone. In
  the game: the trade route editor's ✓, ⚠ and ✕ are gone (the lines
  are already coloured; the remove button is a plain X). Plain text
  symbols stay (★ capital, ✝ mission, the original ✧ in the menu bar).
- **Archive** (new `client/companion/CompanionArchive`): per turn the
  empire figures, the settlements (place, owner, size) and who owned
  each tile, as the player saw it. Recorded every turn while the page
  runs; once per game, in a background thread, it reads the game's old
  autosaves (only this game's, only missing turns) to fill the turns
  before the page ran. Kept in `companion/expansion-<game>-<player>.txt`
  in the user data folder (runs of owned tiles, small).
  Honest: from a save only the player's view of each tile (new
  `Tile.getPlayerView`) and the Foreign Affairs report.
- **Empire chart goes back to the start:** the turns before the page
  ran come from the archive. The note under the chart says while old
  saves are being read ("Reading old autosaves: 40 of 230").
- **History tab (key 7): expansion replay.** A big map: every
  nation's land in its colour (tribes faint), colonies as dots growing
  with their size, native settlements as small squares. Play/pause, a
  turn slider, slow/normal/fast. Legend: land tiles (bar) and colonies
  per nation at that turn ("6 / 7 colonies": seen on the map / in the
  Foreign Affairs report). Below, a "Land owned" line chart per nation
  with a cursor; click it to jump to a turn. Also an Overview widget.
- **Tested:** `ArchiveTest` (a frame from a save shows only what the
  player had seen; written and read back the same; reads a real save
  from a folder, skips another game's, reads each turn once), the page
  rendered in headless Chromium with made-up data (no script errors,
  play and slider work). All tests green.
- **Not yet:** the land backdrop is the map as known now, not as known
  then; autosaves the game already deleted can not be read (FreeCol
  keeps one per turn unless "delete autosaves" is on).

### Step 3b built: offers, the notice board, trade ships (2026-10-14)
Computer nations now start deals with the human player, and the player
can post his own. Decided with Mischa: peace and gold by messenger,
goods by ship; a public notice board; the player answers in the game,
the companion page only shows.
- **How the player answers:** a dialog at the start of his turn
  (Accept / Decline), plus a line in the turn report. The game's trade
  screen could not be used for messenger offers: it needs a unit next
  to the colony, both ways (`DiplomacyMessage` checks it). New
  `OfferMessage` (offer to the player, his answer, his own posts).
  No counter-offers in this dialog.
- **Peace terms by messenger:** a computer nation at war with the
  player whose diplomat scores peace at 5 or more offers it, with gold
  (a fifth of its treasury, at most 1000) when it is losing. Accept:
  peace and the gold at once. Decline: "turned down their offer" is
  remembered. The terms stay 5 turns.
- **Notice board:** the diplomat's best trade move becomes a public
  notice, "wanted: 100 muskets, 600 gold, deliver to Havana" or "for
  sale: 200 furs, fetch at Quebec". Seen first by the nation it likes
  most, after 3 turns by every nation it feels at least neutral about,
  never by an enemy; up 10 turns. Accepting is a promise: a ship of the
  player next to the colony at the start of the computer's turn, with
  the goods (or the room and the gold), completes it. Deadline: twice
  the sailing time plus 4 (6..15 turns). On time: full price,
  remembered as a kept deal and a trade. A little late (up to half the
  deadline over): 25% less, "delivered late" (small trust loss). Very
  late: no deal, "broke your word" (trust cut hard). Reminders at 3 and
  1 turns left. No way to cancel yet.
- **Trade ships:** when the diplomat's best trade is with the player
  and a ship is free (empty, nothing to do but hauling), the computer
  loads the goods (or takes the gold) and sails to the player's nearest
  coastal colony (`TradeVoyageMission`); next to it, the game's trade
  screen opens with the deal. The turn report says when it sets out.
  Computer ships on such a voyage may trade with foreign colonies
  without Jan de Witt (the player's own ships still need him).
- **The player posts offers:** Reports > Post a Trade Offer (want or
  sell, goods, amount, gold, coastal colony). Every computer nation
  weighs it on its turn; one that does not dislike the player and
  gains 10% on it (at its diplomat's values) sends a ship.
- **The player's offers judged by the diplomat** (`DealValue`): goods
  at their Europe price (1.5x when short, nothing for boycotted goods
  given away), peace and alliances at the diplomat's score (a point is
  100 gold); no alliance with a nation it does not like. The old rule
  counted goods the computer gives away as a gain.
- At most one offer of its own per nation per 5 turns. All offers are
  saved with the game.
- **Companion:** Trade tab, "Offers": every offer the player was shown,
  took or posted, with state chips and a countdown bar (green, orange,
  red when late).
- **Tested:** `OfferTest` (who sees a notice when, deadlines, late and
  too late prices, stale, save/parse), `DealValueTest`, `AIOffersTest`
  (peace accepted moves stance and gold, declined is remembered,
  delivered on time / late / too late, goods fetched, offers saved and
  told once, the trade ship's berth, deal and saved mission, the ship
  opening the trade through the server, the player's post taken by the
  Dutch who send a ship, one offer per 5 turns, an alliance from a
  hated nation refused). All tests green.
- **Not yet (or to try in a game):** no counter-offers in the offer
  dialog; no cancelling a promise; computer nations do not take each
  other's notices (only the player can); peace terms are never asked
  of the player by the computer for his wars (only offered); the
  diplomat's sale prices are rough (80% of Europe's for sales, 120%
  for purchases).

### Step 5a built: alliances are defensive pacts (2026-10-14)
In the base game an alliance meant almost nothing. Now:
- **Who declared a war is known.** The server notes every declaration
  and whether the King ordered it (new `WarDeclarations`; the King's
  order runs through `WarDeclarations.asKing`). The turn diff uses it,
  so the player's own declarations are no longer taken for the King's
  (the step 2 limitation is gone). Not saved: after a load the last
  round's wars are guessed from tension again.
- **Calls to arms.** When a nation declares war on our ally (not the
  King), we are called. A computer ally scores `JOIN_WAR` (the war's
  own worth, half its risk because it does not fight alone, plus
  keeping its word: 10 at normal trust, more when it likes the ally)
  and joins at 0 or more. The player gets a dialog. Joining: the ally
  remembers "you fought their enemy". Refusing: the alliance ends and
  the ally remembers "you broke your word". The turn report tells the
  player when his computer ally joins or refuses.
- **Betrayal is public.** Every nation that sees a nation declare war
  on its own ally remembers it as a betrayer (`BETRAYER`: opinion -10,
  trust -15). An ally that makes a separate peace with the common
  enemy broke its word.
- **The King's wars do not call allies** (Mischa: local allies against
  global enemies). When a King's war sets two New World allies
  against each other, it is not betrayal. A loyal ally (trust at least
  50 and not disliking the other, measured before the King's order)
  keeps a **local truce**: its soldiers do not attack the partner (new
  check in `Mission.invalidAttackReason`, and `InvasionPlanner` skips
  its colonies), it offers peace (to the player: peace terms; two
  loyal computer allies make peace on the second one's turn). A
  disloyal one fights. A partner that attacks during the truce ends it
  and is remembered as having broken its word. Truces are saved with
  the choices (`Commitments`).
- **Allies share what they see:** an ally's sightings feed our guesses
  of where the others live.
- **Invitations:** a computer nation whose diplomat scores an alliance
  with the player at 20 or more invites him (dialog).
- **Tested:** `AIAlliancesTest` (the King's war is noted as his; a
  computer ally joins or leaves; the player is called, accepting means
  war, refusing ends the alliance and is remembered; a loyal ally keeps
  a truce, offers peace and its soldiers leave the partner alone; a
  disloyal one fights; two loyal computers make peace), `MemoriesTest`
  (the server says who declared; betrayal seen by all, not for the
  King's war; separate peace), `CommitmentsTest` (truces saved). All
  599 tests green.
- **Not yet:** a truce broken by the partner is only noticed through
  losses and captured colonies. Soldiers standing in an ally's colony:
  never (Mischa).

### Step 5b built: joint wars (2026-10-14)
- A computer nation at war whose diplomat scores paying another nation
  to join (its `INCITE` move) at 20 or more asks it: "join our war
  against Spain, 500 gold now; the colonies you take are yours". When
  it has less gold than the price, it promises the colony it plans to
  take instead ("and Havana if we take it"), as Mischa decided: only
  conquered colonies, a division of the spoils.
- A computer partner weighs it with its own diplomat (the war itself
  plus the gold, plus 15 points for a promised colony) and joins at 20
  or more; refusing is remembered as a refused offer. The player gets a
  dialog. Accepting declares the war at once and the gold moves.
- The joint war is kept as the offer (state "taken"). When the inviter
  takes the promised colony, it hands it over (remembered as a kept
  deal). A partner that makes peace alone while the inviter still
  fights broke its word; the inviter itself makes no separate peace
  while its partner fights. At most one joint-war offer per 5 turns.
- **Tested:** `AIAlliancesTest` (the player joins for gold, the
  promised colony is handed over, his separate peace is remembered and
  the inviter is no longer bound; a computer partner goes to war and is
  paid). All tests green.
- **Not yet:** a partner's promised colony of its own conquest is not
  asked for; the target cannot buy a partner out yet.

### Step 5c built: coalitions against a runaway leader (2026-10-14)
- **The line by difficulty** (Mischa): a nation whose threat (60% army
  share, 40% output share) reaches 0.40 on very easy, 0.38, 0.35 on
  medium, 0.32, 0.30 on very hard is a runaway leader (new
  `Coalition.line`, in every world view).
- **Forming.** A balance keeper (the French in the classic rules)
  starts a coalition at the line, any other nation when the leader is
  0.05 over it. Computer nations that see the same leader and like the
  founder join at once; the player is invited (dialog). Members are
  allied with each other (5a pacts). One coalition per target; a
  founder whose invitations all fail waits 10 turns before trying
  again. A human target is told in the turn report: "A coalition has
  formed against you: France, Holland".
- **Acting.** Attacked: the pacts call the members. Attacking: when the
  members' armies together are 1.2 times the target's and the
  founder's war score plus 10 reaches 20, the founder declares war and
  calls every member: computer members join (`JOIN_WAR` plus 10) or
  leave the coalition and the alliance (remembered as a broken word);
  the player gets a call to arms.
- **Ending.** When the target's threat stays under 0.25 for 10 turns,
  or fewer than two members are left (a lone founder waits 5 turns for
  answers). Members who go to war with the founder or ally with the
  target drop out. Saved with the founder ("coalitions").
- **Companion:** cards show "Coalition against Spain" (red when it is
  against you) and "Local truce" in a King's war; the Offers list
  shows calls to arms, alliance and coalition invitations and joint
  wars.
- **Tested:** `CoalitionTest` (line by difficulty, the line decides the
  leader, ends when calm, waits for answers, save/parse),
  `AIAlliancesTest` (a coalition forms against a runaway Spain, the
  Dutch join, the player is invited and becomes allied with every
  member, only one coalition per target; at war, members are called
  and fight or leave; coalitions are saved). All 608 tests green.
- **Next (Mischa):** the relations graph on the companion page (5d),
  after step 7; a coalition planning its attacks together. Open: the
  target buying a member out (Yamasee War).

### Companion page: lighter on the game and the computer (2026-10-14)
Mischa felt the page could be optimised. What was found and done:
- **The map drew 60 times a second, always**, even on tabs where it is
  hidden and when nothing moved. Now it draws only when it is on
  screen and something changed (the view, the game state, the mouse),
  at full speed while a unit moves or a colony pulses, and 20 times a
  second for the marching route lines. Measured in a browser: 3 draws
  in 3 idle seconds instead of about 180; none on other tabs.
- **Polling.** The page asked for the whole state every second, and
  the game builds it on its own screen thread. Now: every second while
  the page is on screen, every 5 seconds in a background tab (and at
  once when it comes back), and never faster than three times what the
  game took to answer, so a busy game is not slowed by the page.
- **History tab.** It downloaded the whole archive again every turn
  (it grows all game); now only the new turns (`/api/expansion?since=`),
  with a full reload while old saves are still being read in. The land
  backdrop is drawn once and reused, the canvas is no longer resized on
  every frame, and the land chart only moves its cursor while playing.
  Checked in a browser: the turns loaded one by one give the same
  picture as a full load.
- Sliders and checkboxes in the page's gold.
- **Tested:** `ArchiveTest` (only the turns after one are sent, with
  the count); the page in headless Chromium with made-up data (no
  script errors). All tests green.
- **Not looked at:** the game-side build of the state itself (it
  already caches paths and route turns per turn).

### Companion: honesty check, Diplomacy tab, page guide (2026-10-14)
Mischa asked for a look at what each page shows, what it means, that
nothing peeks at hidden data, and room for the coming steps.
- **Page guide:** `claude_docs/plans/companion-ui.md`: every tab, what
  it is for, where each figure comes from, whether it is honest, and
  what is coming.
- **Honesty fixes:** a nation's units "seen now" were counted from the
  units the game last heard of; now only the tiles in sight (same for
  the coloured dots on the map). A computer nation's war plans ("take
  a colony from you", "revenge on you") were shown as its goals; now
  only what it makes known: goods it wants to trade and that it wants
  peace with you. A developer file name was removed from the Relations
  drawer. Everything else checked and fine (list in the guide).
- **New Diplomacy tab (key 5;** Military, Independence and History
  move to 6, 7, 8): a table of you and each nation (stance, mood and
  trust meters, local truce, coalitions, their strongest memory of
  you), the wars and alliances between the others with their reasons,
  the coalitions and their members, the offers, and a Tribes summary
  (stance, mood, settlements known and visited, your missions).
- **Not yet implemented, shown greyed with their step:** relations
  graph (5d), joint attack plans, grievances and ultimatums (6), trade
  agreements (4); for the tribes: friends and rivals, unrest meter,
  calls for help and refugees, allied settlements (7). Also an
  Overview widget for Diplomacy and Tribes; an Alerts subtitle.
- **Tested:** `MoodsTest` (only public goals; a far-away unit out of
  sight is not counted, the one next door is); the tab in headless
  Chromium with made-up data. All 609 tests green.

### Coalition buy-outs, priced by synergy (2026-10-15)
The nation a coalition stands against can pay a member to leave it.
- **Synergy:** how tightly a member is bound to its coalition, in
  points: a base of 5, its opinion of the other members (-10..+10),
  its trust in them (-5..+5), its fear of the target (threat x 20),
  how long the coalition has lasted (up to +5 after 20 turns), and +3
  for each other war it fights side by side with a member. The asking
  price is 150 gold a point (at least 300), so a fresh, lukewarm
  member asks about 2,000 gold, an old comrade in arms much more.
- **The computer as target:** every 5 turns it offers the cheapest
  member (never the founder) its price, if that is at most half its
  gold. A computer member takes it when the gold, as its character
  values gold, outweighs its synergy: traders and settlers take the
  asking price, balance keepers and conquerors hold out (the target
  remembers the refusal).
- **You as a member:** a dialog "Spain offers you 2,200 gold to leave
  the coalition". Accepting: the gold, your alliances in the coalition
  end, your war on the target too, and the members remember you broke
  your word.
- **You as the target:** the Diplomacy tab shows each member's asking
  price ("leaves for 2,200 gold"; a member lets its price be known to
  the target, nobody else sees it). Give at least that much gold in the
  game's diplomacy screen and it leaves. Less is kept as a plain gift.
- **Turn report:** "France took gold from Spain and left your
  coalition" (to the members), "France took your gold and left the
  coalition against you" (to the target).
- **Tested:** `CoalitionTest.testSynergyPrice`; in `AIAlliancesTest` a
  trader bought out (gold, peace, memories), too poor to try, the price
  rising with friendship, a balance keeper refusing, the player
  accepting an offer, the player buying a member out with a gift (and
  the founder not for sale); `MoodsTest` (the price only on coalitions
  against you).
- **Not done:** buying out the founder (the coalition would need a new
  keeper); counter-offers by the other members.

### Step 7a built: tribes know each other, and remember (2026-10-15)
- **They know each other from the start:** every pair of tribes is at
  peace from turn one, also in an older save (set on the first native
  turn). No first contact between tribes.
- **Characters:** warlike Apache, Sioux, Aztec and Inca; peaceful
  Arawak; trading Tupi; Cherokee and Iroquois in the middle. Peaceful
  tribes bring gifts half again as often, warlike ones less; warlike
  tribes go to war sooner.
- **Old rival:** each tribe picks the tribe whose settlements come
  nearest, once (saved). Settlements of two tribes within 4 tiles share
  a border.
- **Opinions between tribes:** start at 0; tension makes them worse,
  the old rival is -30, each border -3 (up to 5), each European both
  hate or fight +15, and memories count.
- **Neighbours rub:** each turn the tension towards the old rival grows
  by the borders (more for warlike tribes), so an old grudge builds up
  over a long game.
- **Wars between tribes stay rare:** only on the old rival, only with a
  grudge (high tension or a bad memory), only when the opinion is down
  to the character's line (warlike -39, middling -55, traders -69,
  peaceful -92). The braves are made angry enough to really attack.
  Peace after 15 turns or so (longer for warlike tribes), unless the
  hatred is complete.
- **Memories for tribes** (saved): a settlement destroyed, land taken
  (a colony within 2 tiles of a settlement), a mission set up, arms
  sold to them (trade) or to their rival, plus the usual (units lost,
  peace, help). What happened is worked out once a turn for all tribes.
- **Tested:** `TribeRelationsTest` (characters, opinions, rare wars,
  memories); `AITribesTest` (everyone knows everyone, nearest rival,
  shared enemy, war only on the rival with a grudge and peace later,
  rubbing, land taken once, the turn record, save and load).
  All 627 tests green.
- **Not done yet:** the tribes do not use the full European diplomat
  (they do not need its moves yet); Europeans paying a tribe to fight
  another (`INCITE` against a tribe) comes with the grievances.

### Steps 7b and 7c built: calls for help, refugees (2026-10-15)
- **Calls for help:** a tribe calls when a European destroyed one of
  its settlements since last turn, or when enemy soldiers stand next to
  its capital. Every other tribe weighs it on its own turn: kinship
  (+30) plus its opinion of the caller, times its character (warlike
  help most), minus a third of how much it likes the attacker, minus
  fear (the attacker's soldiers near its own lands), minus distance
  beyond 5 tiles. At 10 or more it declares war on the attacker and
  sends up to 2 braves (not the last defenders) to seek and destroy the
  attacker's nearest colony or soldier. The caller remembers who came
  ("came to their aid", big plus and trust) and which friends stayed
  home. The same call is heard once per 10 turns.
- **King Philip's War:** an old rival that loathes the caller (-50 or
  worse) and likes the attacker sides with the attacker: war on the
  caller.
- **Turn report:** "The Sioux come to the aid of the Tupi, and are at
  war with you" / "The Arawak, old rivals of the Tupi, side with you".
- **Refugees:** when a settlement is destroyed, half its people (at
  least one) flee to the nearest settlement of their own tribe; the
  last settlement's people go to the tribe they get on with best (not
  at war with them) and become its people. Turn report: "Survivors of
  X flee to Y".
- **News for the companion page** (`TribeNews`, last 30 turns, not
  saved): help, stayed home, sided with, refugees.
- **Tested** (`AITribesTest`): the help score, a tribe coming to help
  (war, war party, memory, news), the rival not helping, a tribe that
  likes the attacker staying home, a rival siding with the attacker,
  refugees to their own tribe and to the best friend, nobody taking
  them in at war. All 633 tests green.
- **Not done:** a dedicated war-party mission (the braves use the
  existing seek-and-destroy mission); help by tribes for each other in
  wars between tribes.

### Steps 7d and 7e built: allied settlements, tribes as allies (2026-10-15)
- **A settlement breaks off and joins you** when your mission has been
  there 20 turns or more, the settlement is happy with you (alarm at
  the lowest level), and its tribe is weak or angry with you (at war
  with you, lost a settlement to you, lost its capital, or down to two
  settlements). It asks in a dialog; a computer nation always accepts.
- **An allied settlement** stays native, flagged as yours (saved,
  public):
  - you use its land for free;
  - it trades with you without anger on its prices;
  - its braves never attack you, even when their tribe is at war with
    you or angry;
  - its braves attack your enemies within 4 tiles of it (they never
    stand in your colonies);
  - its tribe resents you (tension up at once, and a little each turn),
    and so do tribes next to it.
- **A whole tribe joins you** when two thirds of its settlements have
  your happy mission and it likes you (or, as the Tlaxcala, when you
  are both at war with its old rival). Its chiefs offer an alliance (a
  dialog). Then:
  - your wars are its wars (it declares war on your enemies);
  - when it calls for help you get a call to arms; refusing ends the
    alliance and is remembered. A computer ally decides with its
    diplomat.
  - its braves never attack you.
- **Offers from tribes:** the tribes keep their own offer book (saved);
  the answer goes through the same dialog as the European offers.
- **Tested** (`AITribesTest`): a young mission does not break off, an
  old happy one asks and becomes an ally (still native, free land for
  you only, braves will not attack you, the tribe resents you); a whole
  tribe joins and takes up your war; an ally who refuses a call loses
  the alliance; Tlaxcala; all saved. All 638 tests green.
- **Not done:** the tribe raiding its breakaway settlement to take it
  back; the companion Offers list does not show tribe offers yet (they
  come as dialogs and in the turn report).

### Steps 7f and 7g built: uprisings, tribe cards (2026-10-15)
- **Unrest, never out of nowhere:** a tribe hates a European when its
  opinion is -40 or lower and it has a real grievance (a settlement
  destroyed, land taken, or high tension). When it and at least one
  other tribe (not its old rival) hate the same European, unrest
  begins: "The tribes speak of war against you" in the turn report
  (once a turn). It calms when the hatred goes (gifts, giving back
  land).
- **Uprising:** after 5 turns of unrest (7 very easy, 6 easy, 4 hard, 3
  very hard) the tribe declares war and sends 3 braves at the nearest
  colony: "The Sioux rise against you!". A tribe whose old rival rose
  against the same European stays out.
- **Pontiac's rule:** when a tribe's European friend (allied, or liked)
  loses a colony within 6 tiles of the tribe to another European, the
  tribe turns on the winner.
- **Tribe cards** on the Diplomacy tab (replacing the greyed boxes):
  character, mood and trust meters, an unrest meter with the turns
  left, what they remember of you, old rival and friends (only with
  your mission; the rival also when they are at war), allied
  settlements, the latest news. Honesty notes in
  `claude_docs/plans/companion-ui.md`.
- **Tested:** `AITribesTest` (unrest turns by difficulty, unrest before
  the uprising, one tribe alone or with only its rival does not rise,
  unrest calms, a tribe stays out when its rival rose, Pontiac, unrest
  saved); `MoodsTest.testTribeCardIsHonest`; the cards in headless
  Chromium with made-up data. All 646 tests green.
- **Not done:** tribes in the relations graph (5d, next); a tribe
  raiding its breakaway settlement; the companion Offers list for tribe
  offers.

### Step 5d built: the relations graph (2026-10-15)
- **On the Diplomacy tab:** every nation and tribe you met as a dot in
  its colour (Europeans on the upper arc with you in the middle, tribes
  on the lower arc with a dashed edge). Lines: alliance blue, war red,
  old rivals dashed orange, settlements allied to a European dotted
  green with their number. Each coalition puts a dashed ring of its
  colour around its members; the legend says against whom. Hover a
  line or dot for words.
- **Honest:** wars and alliances between the others are public (single
  player: from the server; multiplayer: only yours); old rivals only
  as the tribe cards show them (your mission, or at war).
- **Tested:** `GraphTest` (unmet nations left out, public war shown,
  rival hidden until at war, multiplayer only your own stances);
  the drawing in headless Chromium with made-up data.

### Coalition joint attacks (2026-10-15)
- **One target, one landing:** once a coalition is at war with its
  target and at least two members fight, the founder picks one colony
  (the one its diplomat would attack, else the target's colony nearest
  to it) and a strike turn: when the slowest member can be there (2 to
  10 turns; by land a tile a turn, by sea four tiles a turn plus two to
  load and land). The plan holds until 5 turns after the strike turn or
  until the colony falls, then a new one is made. Saved with the
  coalition.
- **Members keep to it:** a computer member attacks the target only at
  the planned colony, and sets off just in time to land on the strike
  turn (the log says "waits for the joint strike"); the plan adds the
  coalition bonus to the attack's score. Other enemies are attacked as
  before.
- **You as a member:** the turn report says "all members strike
  Havana of Spain together, landing in 6 turns", and the Coalitions
  box on the Diplomacy tab shows "Joint strike: Havana, in 6 turns".
  The target never sees it.
- **Tested:** `CoalitionTest.testJointPlan` (strike turn, launch turn,
  how long a plan holds, saving, older saves);
  `AIAlliancesTest.testCoalitionPlansAJointStrike`;
  `InvasionPlannerTest.testJointStrikeIsTimed` (waits when too early,
  sails to the planned colony when it is time); `MoodsTest` (members
  only). All 650 tests green.
- **Not done:** members bringing their armies to one muster point
  first; the player's own armies are of course up to the player.

### Step 6 built: grievances and ultimatums (2026-10-15)
Plan: `claude_docs/plans/step6-grievances.md`.
- **No war out of nowhere:** when a computer nation wants a war of its
  own choice (against you by the old anger rule, against a computer
  nation by its diplomat), it first **complains** (turn report:
  "France complains: your soldiers stand near our colonies"), after 3
  turns sets **terms** (a dialog: "pay 450 gold, or war"), and declares
  **war** only when you refuse or let 3 more turns pass. Paying settles
  it: no new grievance against you for 15 turns, and they remember you
  kept your word. An open grievance is forgotten after 12 turns if the
  reason for war goes away.
- **The cause, from what they see:** your soldiers within 2 tiles of
  their colonies; harm they remember; your colonies within 4 tiles of
  theirs; else "you stand in the way of our plans".
- **The demand:** a quarter of your gold (public in the Foreign
  Affairs report), between 200 and 2,000.
- **Between computer nations:** the other side pays when it can and
  is not stronger, else refuses and the war comes.
- **Not through grievances:** calls to arms, joint wars, coalition wars
  (they have a public reason), the King's wars, the tribes.
- **Paying a tribe to fight a tribe:** your missionary's "incite" now
  also lists the other tribes you met; cheaper against the tribe's old
  rival. A computer nation at war with a tribe pays the cheapest other
  tribe (up to a third of its gold, every 10 turns). A tribe whose
  hatred of another tribe is at the top goes to war with it.
- **Companion page:** "Complaint: your soldiers stand near our
  colonies (terms in 2 turns)" or "Ultimatum: 450 gold or war, 2 turns
  left" on the nation card and in the Diplomacy table. The Offers list
  shows ultimatums, buy-outs and the tribes' offers (step 7 leftover).
- **Tested:** `GrievanceTest`; `AIGrievancesTest` (complaint, terms,
  war when ignored; paying settles it; refusing means war; the cause;
  a computer nation pays or fights; saved); `AITribesTest` (a European
  pays a tribe, the rival is cheaper, the paid tribe goes to war);
  older stance tests now expect the complaint first. All 660 tests
  green.
- **Not done:** the King's random wars needing real tension (an option
  in the plan; you liked the King's wars as they are, so left alone);
  terms other than gold ("withdraw your soldiers").

### Step 7 leftovers: taking back a settlement, tribe offers listed (2026-10-15)
- **A tribe takes back its breakaway settlement:** when its anger with
  you (the ally) reaches 800 tension (it grows each turn the
  settlement serves you), it marches on the settlement. You get a
  dialog: defend it (war with the tribe; it stays yours) or let it go
  back to its tribe. Ignoring it for 5 turns lets it go back too. A
  computer ally defends it when its army is not weaker than the
  tribe's. Turn report: "Ypiranga is under the Tupi again".
- **The tribes' offers in the companion Offers list** (settlement
  joins, alliance, call for help, take-back), with ultimatums and
  buy-outs. The "turns to deliver" line now shows only on goods deals.
- **Tested:** `AITribesTest` (not before the anger; let go, defended
  with war, ignored until it runs out). All 662 tests green.

### Step 4 built: price agreements by ship (2026-10-15)
Plan: `claude_docs/plans/step4-price-agreements.md` (Mischa's redesign:
not per-turn deliveries, but a price for a period).
- **What:** a computer nation buys a good from you at a fixed price per
  unit for 5 to 30 turns, as many as you bring by ship to any of its
  coastal colonies, as often as you like. Handy when your goods are
  boycotted: they sell them in Europe themselves.
- **The dialog:** on their turn, a ship of yours next to their colony
  with the good gets "they take up to 100 coats at 9 each: how many do
  you sell?". Each colony takes what its warehouse has room for (at
  most 200 a visit) and what they can pay; full says so. Every sale is
  a trade memory: trust grows.
- **The price:** they agree up to 85% of their own Europe price after
  tax; asked more, they answer with 75% (accept or not); they refuse
  when boycotted themselves, when the good sells for nothing, when they
  do not like you or are at war with you, or when an agreement for that
  good is already running.
- **Who proposes:** you, from Reports, "Post an offer", "A price
  agreement with one nation" (nation, goods with your own Europe price
  or "boycott", price per unit, turns); the computer, at most every 20
  turns, for a good it keeps needing, at 85% of its Europe cost.
- **Ends** when the turns run out, or with a war.
- **Fixed amounts in parts:** a notice board "wanted" deal can now be
  delivered in parts (40 now, 60 later), paid pro rata.
- **Companion page:** the Offers list shows running agreements
  (goods, price, delivered and gold earned, turns left bar, "running").
  The per-visit questions stay dialogs only.
- **Tested:** `PriceAgreementTest` (prices, counter, room, turns,
  saving); `AIAgreementsTest` (accept, counter, one per good,
  boycotted, not at war, a ship sells what you say and trust grows, not
  asked twice at once, full warehouse, ends with time and with war,
  delivery in parts, saved). All 672 tests green.
- **Not done:** the computer selling to you under an agreement (only
  buying for now); an agreement making war less likely.

### The computer keeps a treasury (2026-10-15)
Mischa noticed the computer nations never keep spare gold: every turn
they bought units in Europe until the next one cost more than they had
(`buyUnitsInEurope`). So they could not pay for deals: price agreements
took 0 goods, no notices, no buy-outs, no paid tribes, ultimatums
refused.
- **A reserve shopping cannot touch:** 300 gold plus 50 a colony (at
  most 2,000), times the character (traders 1.5, independence 1.2,
  conquerors 0.7), plus what it has promised in open deals: its
  notices, peace and joint-war gold, unpaid "wanted" deals, and two
  loads (2 x 100 goods) for each price agreement.
- **Shopping in Europe** (colonists, ships, artillery, dragoons, the
  diplomat's war ship) only spends gold above the reserve. The cheats
  top up without eating it.
- **Deals spend it:** price agreements, notices, peace, buy-outs,
  paying tribes, paying ultimatums.
- **Losing a war** (a colony taken, or 3 units lost in the last 10
  turns): only the promises are kept; the rest buys soldiers.
- **Not on the companion page:** the reserve is the nation's own
  business; their gold is in the Foreign Affairs report.
- **Tested:** `TreasuryTest`; `AIAgreementsTest.testTreasury` (reserve,
  promises, shopping leaves it, losing a war). All 674 tests green.
- **To tune in step 8:** the sizes (a slower military build-up is the
  price).

### Fix: frozen game at the start of a turn with offers (2026-10-15)
Mischa's game froze after one turn with "France offers peace" and
"England proposes a price agreement" in the turn report. A thread dump
showed the screen thread inside an offer dialog: the dialogs were
opened at the start of the turn and the turn report was then drawn on
top, so the (modal) dialog was hidden but blocked every click.
- **Fix:** offer dialogs now wait until no panel (the turn report, a
  colony, Europe...) and no other dialog is showing, then come one at
  a time.
- **Tested:** all 674 unit tests green (no unit test for Swing timing;
  Mischa to check in the game).

### Fixes from Mischa's first game with steps 4-7 (2026-10-15)
- **Trade routes go at the player's word:** units on trade routes no
  longer move by themselves whenever no unit needs attention (after the
  turn report, after closing a colony). They move when the player
  presses the execute-orders button (the double triangle), or at the
  end of the turn if they have not moved yet. The same for units with
  a go-to order (they set off when Mischa gave another ship a new
  route); a unit given an order right now still moves at once.
- **Ships sailing to or from Europe no longer count as pending
  orders** (the end-turn warning and the count on the execute-orders
  button): they have their order but nothing to do until they arrive.
- **Trade routes wait for the turn report:** units with orders (trade
  routes, goto) used to start moving while the turn report was still
  open. They now wait until it is closed (pressing End Turn with the
  report open does nothing until it is closed).
- **Offer answers show at once:** the news of an answer ("agreement
  accepted", "they bought 80 coats") appeared only after the next unit
  move; the client now waits for the server's reply and shows it.
- **Parchment map while scrolling:** scrolling with the mouse at the
  screen edge draws the map on a separate painter thread into its own
  image, which never got the mist fade (so it vanished while moving);
  and both painters shared one "fade into this image" field, so a fade
  could land in the wrong image (patches and odd rounded shapes left
  in place). The image is now passed along each drawing, the scrolling
  painter gets the fade too.
- **Parchment, the real cause of the rounded corners:** the paper laid
  over the sea near the unknown did not blend: wherever it was thicker
  than the fog of war it replaced the pixel, which cut the fog (the
  game's dark rounded diamonds on tiles out of sight) off along a hard
  contour. It now blends over whatever is there, so sea and fog fade
  smoothly into the paper. While edge-scrolling the sea had turned
  brown (that painter draws the sea into the same image and the fade
  treated it like land); it now gets the same paper over it. (A try
  with a flat "chart blue" sea in between was dropped: it made lighter
  diamonds with hard edges around the visible area.)
- **The button bar no longer disappears:** the row of unit buttons
  (with the execute-orders button) vanished in two ways. (1) The game
  hides it while no unit is active, and since trade routes and go-to
  orders wait for the player, that is now common: the turn starts with
  only "End turn" in the corner and no execute-orders button. The bar
  now stays during a game; buttons the unit would use are greyed out.
  (2) A window resize (full screen, scaling) throws the buttons away
  and makes them again only if a unit is active; with none active,
  the new buttons were never put on screen until the next resize. They
  are now put on screen whenever they are made. Also: when no orders
  were carried out, a unit that becomes free later (out of a colony,
  off a ship) is picked as the next active unit again. To be tested:
  start a turn where every unit has orders; toggle full screen with
  no unit selected, then select one.
- **Execute-orders count no longer stuck at 1:** a unit that tried
  its order and could get no further (waiting at Nijmegen for a ship,
  a blocked path) kept counting as waiting all turn, and kept the
  end-turn warning up. Units that tried this turn no longer count;
  the button stays usable to try them again (a ship came in). Land
  units in Europe waiting for a ship do not count either.
- **Hover the execute-orders button** for the list: "Still to move"
  (unit, where it is, where it goes or its route) and "Tried this
  turn, can get no further now". To be tested: the list matches the
  units on the map; the count drops after pressing the button.
- **Freeze with the old-chart map (fixed):** the parchment fade read
  pixels outside the map image for a tile half off the screen edge
  (after the colony and build-queue panels were open), which failed on
  every repaint and froze the game. The fade now stays inside the
  image, and if it ever fails the tile is drawn without it instead of
  stopping the map. New ParchmentFadeTest; the old ParchmentPainterTest
  was not in the test suite and now is.
- **Tested:** all 681 unit tests green (new: PendingOrdersTest,
  ParchmentFadeTest); the drawing and the turn order need Mischa's eye
  in the game.

### Trade with other nations, supply network (2026-10-16)
- **Europe price says it is gold:** the trade screen showed
  "100 Muskets (300 in Europe)", which read like a number of goods.
  Now "(worth 300 gold in Europe)"; the same when posting an offer.
- **Supply network keeps its shape with far colonies:** every colony was
  placed by its map position and stretched to fill the box, so one far
  colony (Cuper's Cove) squeezed the rest into a strip at the top and
  the labels were cut off. Now colonies keep their direction from each
  other (west-east, north-south order) but are spaced evenly; route
  networks that do not touch get their own part of the box, side by
  side, with a faint line between; a colony without routes stays in its
  direction, with the network nearest to it. Nodes shrink when they
  stand close; the box grows only within the room the page has; labels
  stay inside the box. Checked with a drawing of Mischa's colonies plus
  a far network. To be tested on the page.
- **Buying from another nation's colony works, on their terms:** asking
  a colony for goods was always refused: the old trade screen offered
  placeholder goods ("100 Muskets") that sat in no warehouse, so the
  computer marked them invalid whatever gold was offered, and the
  server would not have handed them over anyway. Now the colony says
  what it sells (`SaleTerms`, worked out by the nation itself in
  `EuropeanAIPlayer.saleTerms`, sent with `SaleTermsMessage`):
  - **How much:** what the colony can spare after its own needs (5
    turns of what it uses up; 50 muskets and horses kept, 100 at war),
    times a share by character (traders 0.9, conquerors 0.5, others
    0.65; weapons less, except for traders) and by how they feel about
    you (half at neutral, all for a friend, nothing below opinion -40).
    No weapons or horses to a nation they dislike or fear. Rounded
    down to tens: you see what they sell, not what they hold.
  - **Price:** Europe price after tax, less the trip to Europe and back
    (2.5% a turn, at most 25%), less up to 15% when their colonists are
    rebels (they do not like arming the Crown), plus their margin
    (traders 10%, conquerors 20%, others 15%), up to 10% off for a
    friend, up to 30% on for a nation they hate. Boycotted goods a
    quarter less (Europe will not take them).
  - **The screen:** "They sell" box: pick goods (icon, "up to 40",
    greyed "out of stock" / "not for sale"), the amount in tens (no
    more than they sell or the ship can carry), the price shows, Add
    puts the goods and their price in gold on the table. Removing the
    goods takes their price off again. A deal on the terms is
    accepted; less gold or more goods is not.
  - **Tested:** `SaleTermsTest` (amounts in tens, out of stock, own
    needs kept, traders sell more, opinion, weapons, trip and rebels
    lower the price, friend/foe/trader prices, deal checks, sending);
    `AISaleTermsTest` (every goods listed, deal on terms taken, too
    little gold or too much refused, disliked buyer gets nothing).
    All 695 tests green. **Not checked:** the screen itself in the
    game (layout, a full deal at a Spanish colony). No haggling yet.
- **Trade screen fixes and haggling (Mischa's first try):** Add did
  nothing: the game refuses to make goods "at" a colony whose store
  the player cannot see; the goods are now made without a place and
  then put in their colony. The total follows the amount as you type
  (not only after Enter). The box now reads: goods, "They ask 4 gold
  per 10", "Amount [10] = 4 gold", Add.
  - **Haggling:** pick 5, 10, 15 or 20% off and Ask. They agree with a
    chance that falls as the discount eats into their margin (room:
    their margin plus any extra asked of a disliked buyer; a friend
    keeps at least half the margin to bargain with), higher for
    traders (0.75 at the start) than settlers (0.55) and conquerors
    (0.4), plus a quarter of the opinion. Past the room: always no.
    Agreed discounts add up; a refusal holds for that goods for the
    rest of the turn. Goods already on the table get the new price.
    Worked out on the server with the nation's own dice; not saved.
  - **Tested:** `SaleTermsTest` (room, chances fall with the discount,
    traders bargain gladly, a friend still has room, a haggled line);
    `AISaleTermsTest` (goods made as the screen makes them are valid,
    a haggle lowers the price and a deal at it is taken, a refusal
    holds, a new turn forgets it, the server moves the cloth to the
    ship and the gold to the seller). All 699 tests green. **Not
    checked:** the screen in the game.
- **Log: the important words stand out:** on the companion page log,
  who and where are bold (the nations, tribes, colonies, places and
  goods the game filled into the message), and so is what went wrong
  (exceeded its capacity, wasted, shortage, missing, stopped
  production, concerned, encroachments, our lands, starving...); good
  news is green (peace, alliance, joined, completed, arrived), bad news
  red (war, attacked, destroyed, captured, burned, lost...). Numbers
  stay plain; "war" in "warehouse" is not war. The game works out the
  runs (`CompanionHighlight`), the page draws them. Tested with the
  lines of Mischa's turn 272 (`HighlightTest`) and a drawing of them.
  All 704 tests green.
- **The King's wars no longer poison relations:** in Mischa's game the
  French King kept declaring war on the Dutch (at the turn change),
  then France offered peace and paid, yet France grew angrier each
  time. Causes: the old rule adds 1000 tension for any war and takes
  only 500 off for peace, and tension between Europeans never fades,
  so each forced war plus peace left 500 more for good; and our memory
  turned every war in the history into "they declared war on us" for
  both sides. Now: a war the King orders adds no tension (the fighting
  still does); a war in the history is a grudge only for the side that
  was attacked by the other's own choice (wars are tagged with the
  declarer and the King's order; older saves count as before);
  tension towards other European nations at peace fades by 10 a turn
  (`ServerPlayer.TENSION_FADE`), so full hatred is gone after about
  100 peaceful turns. Tested: `KingsWarTest` (King's war: no tension,
  no grudge, peace leaves none; own war: tension, only the victim holds
  a grudge; fading in peace, not at war, never below 0). All 707 tests
  green. **Not done:** the King still declares wars at random (the
  idea of a nation paying to stay out of the King's war stays open).

### Step 8.1 built: envoys to nations (2026-10-17)
- **Report > Send an Envoy:** pick a nation (each shows its stance
  and "envoy ready" or "next envoy in 3 turns"), what to propose
  (peace when at war, an alliance when at peace, a gift any time) and
  the gold that goes with it. The envoy leaves now; the gold travels
  with him (back if they say no).
- **Answered on their turn**, so it reaches you at the start of your
  next turn (`EnvoyDesk`, run from `OfferPlanner.plan`): the same
  diplomat that makes their own offers scores the move
  (`DealValue.stance`), your gold is added. Worth it: yes (peace or
  alliance made, they keep the gold). A war they chose themselves less
  than 10 turns ago: "not yet". Too little trust (or, for an alliance,
  too little liking): no, with the reason. Otherwise they name their
  price (rounded up to 50, at most 5000) as terms in the usual offer
  popup; accept and pay, and it is done. A gift is always taken: a
  memory of the gift and tension down by a tenth of the gold.
- **One envoy per nation every 5 turns**; at the start of the turn the
  wait is over: "An envoy can go to France again." Envoys and terms are
  in the companion page's offer list.
- **Rules:** `EnvoyRules` (pure). Peace looks only at trust, not at
  liking: a fresh war always leaves the opinion of the enemy low, and
  the diplomat's peace score weighs it already.
- **Tested:** `EnvoyRulesTest` (waiting, yes, the price, distrust, too
  dear, not yet, alliance never, gifts, rounding), `AIEnvoyTest` (gift
  round trip with the gold held, the wait and the status, wrong
  proposals refused at once with no gold lost, a peace envoy answered
  with gold back on a no, terms accepted and paid, terms that no longer
  fit). All 721 tests green. **Not checked:** the dialogs in the game.

### Step 8.3a built: encroaching means taking their land (2026-10-17)
- **Discussed with Mischa:** sharing a border is not encroaching (Cuper's
  Cove next to the Arawak). The game's old rule angered a village at
  every colony, used tile and armed unit within its land plus 2 tiles,
  paid for or not; our "land taken" memory counted any colony within
  2 tiles of a village.
- **Now:** land got from a tribe is recorded (`LandClaims`, saved with
  the tribe): bought (or given) or taken without paying. Each turn a
  village is angered only by colonies and used tiles on land taken from
  it, and by armed units inside the tribe's borders or right next to
  the village. The tribe's "land taken" memory comes from taken land
  too. Land got before the record counts as taken when it lies inside
  a village's own land (its radius).
- **Tested:** `LandClaimsTest` (recorded, saved and read back,
  forgotten; old land inside a village counts, outside not; a border
  colony angers nobody; a colony on taken land angers; claims made by
  the game are recorded, stolen and free). All 726 tests green.

### Step 8.3b built: envoys to tribes (2026-10-17)
- **Tribes are in the envoy window** (Report > Send an Envoy): peace
  when at war, a gift any time; one envoy per tribe every 5 turns,
  answered on the tribe's turn (`TribeEnvoyDesk`, from
  `TribePlanner.plan`).
- **Survival first** (`TribeEnvoyRules`): the tribe weighs its braves
  against your soldiers within 4 tiles of its villages, and a village
  lost to you in the last 20 turns. Losing (2 braves per 3 soldiers or
  fewer, or a village lost): peace at once, humbly. Even: peace for
  gold, half the anger above 300 (900 anger: 300 gold). Strong (1.5
  braves per soldier or more): twice the gold, and if it dislikes you,
  conditions: give back the land you took (the tiles become theirs
  again, workers on them step out), abandon a colony standing on taken
  land (disbanded, its colonists leave as free units, the land is
  theirs), pull your soldiers out of their land (peace begins the turn
  none is inside; 10 turns, or the deal is off). Strong and full of
  hatred (opinion -90 or lower): no, they want revenge.
- **Peace** brings the tribe's and its villages' anger down to 300. A
  gift: anger down by a tenth of the gold, and remembered.
- **The terms** come in the usual offer popup, with the conditions in
  words; the companion page lists them too.
- **Tested:** `TribeEnvoyRulesTest` (standing, losing takes peace, even
  wants gold, strong sets conditions or takes gold when there is
  nothing to ask back, revenge, a losing tribe still takes peace,
  gifts, fit), `AITribeEnvoyTest` (a losing tribe makes peace at once;
  a strong tribe's terms with a colony and a gun in its land: gold
  back, accepted, colony disbanded, land theirs, colonists free, peace
  only once the gun leaves; a gift cools anger, the wait, no
  alliances). All 735 tests green. **Not checked:** in the game.
- **Next:** 8.2 (joint wars, leaving a coalition), asking a tribe to
  fight for you, 8.4 (trade in the same window).

### Step 8.2 built: join my war, leave the coalition, tribes fight for you (2026-10-17)
- **Join my war** (envoy window, a nation or a tribe you are not at war
  with): pick a nation you fight that they do not fight and are not
  allied with. A nation scores the war with its own diplomat; if it has
  no reason of its own the price is 1500 gold, more if it likes the
  enemy (up to 3500), less if it dislikes it (down to 500); twice that
  when you are not allies. A war it wanted anyway is free. It says no
  above 10000 gold, or when allied with the enemy. Once agreed it
  declares war and holds it 10 turns like a war of its own.
- **Leave the coalition against me** (only shown for a member, not the
  leader): it leaves for its buy-out price (what the coalition is
  worth to it): alliances with the members end, its war with you ends,
  the members remember it broke its word.
- **Tribes fight for you**: an ally fights your wars anyway, so yes, for
  free. Otherwise, if it likes you: its price and only as your ally
  (you will be called to help in its wars too). If it dislikes you: no.
  The price is by what you get: 100 gold per brave it has, x1.5
  against a European nation, half against its old rival, from half to
  twice as much by how it likes you compared with the enemy; at least
  100 (4 braves against France: 600). Mischa found the first version
  (the old missionary incite price, 5000-10000) far too dear: "20
  cannons for 4 warriors".
- The answers, terms and companion lines name the enemy. The envoy
  status now says which nations are in the coalition against you.
- **Tested:** `EnvoyRulesTest` (war price without a reason, misfit
  reasons, buy-out price), `TribeEnvoyRulesTest` (war prices per brave, ally
  free, price plus alliance, no if disliked, already at war),
  `AIJointWarEnvoyTest` (war for enough gold and held; their price as
  terms, taken: war; misfits: no gold lost, not against an ally; buy a
  member out of a coalition, the leader cannot be; a tribe's price and
  alliance, then the next war free). All 744 tests green. **Not
  checked:** in the game.
- **Next:** 8.4 (trade in the same window).

### Step 8.4 built: trade in the envoy window (2026-10-17)
- **One window for diplomacy and trade**: after picking a nation in
  Send an Envoy, the list also has "Trade: a price agreement" (straight
  to the goods and price for that nation) and the notice board ("goods
  I want", "goods I sell", seen by all nations).
- **Trade needs no envoy**: a nation whose envoy is still waiting can be
  picked for trade (only the trade lines show); a tribe that is waiting
  stays greyed out.
- The old Post a Trade Offer menu item still works the same.
- **Tested:** the window is client wiring only, around the existing
  agreement and notice-board code (their tests unchanged and green).
  All 744 tests green. **Not checked:** in the game.
- **Step 8 is done.** Not built from the plan: a button by each nation
  in Foreign Affairs; goods (not just gold) in envoy deals.

### Tribes on the Nations and Military tabs (2026-10-17)
- **Tribe cards next to the nations** (Nations tab, dashed border, a
  "tribe" tag): settlements (and how many you have seen), about how
  many braves, their army in soldiers' worth with the same bar as the
  nations, an Army chip (weaker / about even / stronger than you), the
  stance, and their braves in sight (plain, armed, mounted, dragoons).
- **Military tab**: the tribes join the Army bars (in italics), so you
  see the Iroquois next to France in one list.
- **Honest**: the settlement count is what the game's Native Affairs
  report shows; braves per settlement are the middle of the sizes the
  rules give (the capital bigger); braves in sight count as they are
  (an armed brave hits harder), the rest as plain braves (1.5, a
  soldier 3). Only tribes you met (`CompanionTribeStrength`,
  `Estimates.tribeBraves` / `tribeStrength`).
- **Tested:** `NationEstimateTest.testTribeEstimates`,
  `TribeStrengthTest` (settlements, the estimate, a brave in sight,
  unmet tribes left out). All 746 tests green. **Not checked:** on the
  live page. **Not done:** a history chart for the tribes (the nations'
  chart reads old saves; tribes start from now if wanted).

### War of Spanish Succession, closer to Utrecht (2026-10-17)
- **Why**: in Mischa's game France handed all 7 colonies, units and
  missions to England in one turn and left the game ("sudden, not
  realistic"). That is the old event: once any nation passes 50%
  rebels, the weakest computer nation (by score) gives everything to
  the strongest. History: at Utrecht (1713) France ceded Newfoundland,
  Acadia and Hudson Bay but kept Canada and Louisiana; small outposts
  emptied (Plaisance), most settlers of the big places stayed.
- **Now**: when the line is passed, a war breaks out in Europe between
  the two (everyone gets the news). 6 turns later the treaty: the loser
  cedes about 2 in 5 of its colonies, the ones nearest the winner's
  colonies, and always keeps one. Colonies under 3 colonists are
  abandoned (the settlers sail home, the colony disappears). From the
  others a tenth of the people sail home (Mischa chose 10%; the skilled
  go first: officials, officers, merchants), and so do the garrison and
  ships in port. Ceded colonies start at 0% rebels (their bells were for
  the old crown, so the winner gets no free score and big ones may
  suffer the tory penalty). The loser stays in the game, at peace with
  the winner. Still once a game.
- **Game option** "War of Spanish Succession" (on by default) switches
  it off (`model.option.spanishSuccession`; old saves: on).
- Code: `common/diplomacy/SuccessionRules` (pure), `server/model/
  Succession` (the treaty), `ServerGame.csSuccession` (war, then
  treaty; the pending war is saved on the game as `successionPending`).
- **Tested:** `SuccessionTest` (the rules; the treaty: nearest colony
  ceded, a small one abandoned, a tenth sails home, no rebels left,
  garrison home, the loser alive and at peace; war first, treaty
  later). All 749 tests green. **Not checked:** in a game.

### Your game and map options are remembered (2026-10-17)
- **Why:** Mischa set up a big map (75 x 150, high seas 3) and his game
  options, and wants them as the default for the next game.
- **Found:** the Game Options and Map Generator Options dialogs already
  save what you pick (on OK) to `game_options.xml` and
  `map_generator_options.xml` in the FreeCol folder (Documents\Freecol\
  freecol), but the game never read them back (FreeCol's own loading
  code is switched off).
- **Now:** a new game starts from those two files, once (the new-game
  screen, `StartGamePanel.applySavedOptions`), and tells the server.
  The dialogs still change everything; OK saves the new choice as the
  default; Reset in the game options goes back to the rules' defaults.
  An older file without a newer option keeps that option's default.
- Set on Mischa's PC: map 75 x 150, high seas 3, natural disasters 10%
  (old files kept as `*.before-claude.xml`).
- **Tested:** `SavedOptionsTest` (the map size saved and read back; an
  older game options file keeps the Spanish Succession default). All
  751 tests green. **Not checked:** in the game.

### Notice-board deals: move into their colony to deliver (2026-10-17)
- **Why:** Mischa took a Russian "wanted: 100 lumber at Unalashka" and
  tried to sail into the colony: "cannot go in". Same with wagon trains.
  Without Jan de Witt the game forbids trading in a foreign colony, and
  the deal only completed when a *ship* stood *next to* the colony at
  the start of the Russians' turn (his merchantman was already next to
  it, so ending the turn would have done it, but nothing said so).
- **Now:** moving a ship or wagon train into their colony hands the
  deal over at once (the goods, or for a "for sale" deal the room and
  the gold; part of the goods is paid pro rata), with the usual
  messages. No Jan de Witt needed for that. With nothing to deliver
  there, the move is refused as before. Wagon trains next to the colony
  now count too, not only ships. The promise message says so.
- Code: `DeliverMessage`, `InGameController.deliver` (server),
  `OfferPlanner.deliverNow` / `complete`, client
  `InGameController.deliverAt` (from the "no access" and trade moves).
- **Fix the same night (Mischa: "still doesn't work"):** he moved the
  ship with the mouse. The map's goto never finds a path *into* a
  foreign colony, so dragging or clicking onto Unalashka did nothing
  (the arrow-key move worked; checked by loading his save in a headless
  client). Now dragging or clicking a ship or wagon train onto another
  nation's colony goes to the nearest tile next to it (the path shows
  while dragging); if it gets there with moves left the deal is handed
  over at once, else it waits there and the deal is done on their turn.
  With no deal there: the usual "no access" message.
  (`SwingGUI.updateGotoTile` / `traverseGotoPath`,
  `InGameController.isDeliveryTarget` / `pathNextTo` / `deliverTo`.)
- **Tested:** `AIOffersTest.testDeliverNowByMovingIn` (nobody there,
  part, the rest, done and remembered, not at another colony),
  `testWagonDelivers`. All 753 tests green. **Not checked:** in the
  game.

### Wagon trains and price agreements; open notices on the spot (2026-10-18)
- **Why:** Mischa's wagon trains at Unalashka got the game's "no trading
  rights" message. His save: the lumber notice was already done; what
  ran was a *price agreement* for lumber, which only asked "how many?"
  for a *ship* next to a *coastal* colony; the two fur notices were
  still open (never taken), and moving in only handled taken deals.
- **Now:**
  - Price agreements work with wagon trains too, at any of their
    colonies (coastal or not), on their turn when the carrier waits next
    to it, or at once when it moves into the colony: the "how many do
    you sell?" question comes straight away.
  - A notice still open to you (wanted or for sale) is taken and
    completed on the spot when you move in with the goods (or the room
    and the gold); if you can not complete it, it stays open.
  - With nothing to hand over, a plain message says so ("take their
    offer first"), instead of the game's "no trading rights", which
    suggested deals need Jan de Witt.
- **Tested:** `AIAgreementsTest.testWagonSellsUnderAgreement` (asked at
  once on moving in, sold, asked again on their turn),
  `AIOffersTest.testOpenNoticeTakenByMovingIn` (no goods: stays open;
  with goods: taken and done). All 755 tests green. **Not checked:** in
  the game.

### War of Spanish Succession: random pair, chance of leaving (2026-10-18)
- **Why (Mischa, after the first one in his game: Russia ceded 2
  colonies of 4 to Denmark, nobody left):** "10% should be a
  distribution, not a hard number: 0 or 3 out of 4 can happen"; "why
  does the weakest always fight the strongest? More fun random." Not
  wanted: the nearest nation as winner.
- **Now:** when the line is passed, two computer nations (with
  colonies) are picked at random and go to war in Europe; the news
  names both, not who will lose. At the treaty, 6 turns later, the war
  is decided: the stronger (army and navy) more likely wins, never sure
  (strength + 1 over both + 2: equal 50%, three times stronger about
  75%). Each colonist of a ceded colony sails home with a 1 in 10
  chance (the most skilled go first), at least one stays.
- **Tested:** `SuccessionTest` (leaving over 2000 rolls: about 0.4 of 4,
  sometimes 2 or 3, never all, often nobody; win chances; the treaty
  with fixed rolls; war then treaty with either side winning). All 755
  tests green.
