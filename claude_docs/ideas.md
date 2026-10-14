# FreeCol UI mod: ideas and status

Goal: make FreeCol's UI workable and give proper overviews.
Branch: `colony-overview` on the fork `mischadeq/freecol`.

Last updated: 2026-10-13

Status legend: **Done** = written and seen working in the game.
**In progress** = being worked on now. **To be tested** = written and
committed, not yet checked in the game. **Planned** = designed, with a
plan in `claude_docs/plans/`, not built. **Idea** = not started.

Aim: release the mod once Mischa is content with it.

## Done

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

## In progress

Nothing at the moment.

## To be tested

Written, but not yet tried by Mischa in a real game. Move an item to
Done once it has been seen working.

"Auto-checked" means Claude ran the real game in an automated session
(50 AI turns, took over AI colonies in debug mode, added wagons and
soldiers, opened every report and took screenshots) and it worked
there. That is a smoke test, not a play test.

### Not yet committed (written 2026-10-12, second round)

- [ ] **Groups march together; Rush to Destination.** A normal go-to
      order for a group now keeps it together: the slowest member moves
      first each turn and the faster ones go no further than its tile.
      Orders > Rush to Destination (Shift+R) lets every member go at
      full speed, so the fastest arrive first; the info panel then
      shows "(rushing)". The next normal go-to order makes the group
      march together again. Auto-checked: after the first go-to order
      all four members of a test army ended the turn on the same tile;
      the Rush menu item is present. Not checked: several turns of
      marching, and the rush itself moving units ahead.
- [ ] **Map on the Logistics page.** A map strip at the top, zoomed to
      the area the trade routes cover. Each route in its own colour as
      a dashed line through its stops with an arrow for the direction;
      the route names below get the same colour. Wagon trains and
      ships as circles at their real position, with the path they will
      take to their next stop, the goods they carry and the turns to
      get there. Hover for details; click to close the report and look
      at that spot on the main map. Auto-checked: renders and fills the
      strip. Not checked: a carrier on its way (path and turns), hover,
      click.

### Not yet committed (written 2026-10-12, third round)

- [ ] **Execute Orders button.** New round button at the right end of
      the bottom bar (fast-forward sign). A red number shows how many
      units still have go-to orders or trade routes to carry out this
      turn; greyed out when there are none. Same as Orders > Execute Go
      To Orders (O). Sandbox-checked: count 4 at turn start, button
      moved the army, count back to 0.
- [ ] **End-turn warning.** Ending the turn while units still have
      orders asks "End turn anyway / Not yet". Option: Preferences >
      Other > "Warn about orders not carried out". Sandbox-checked:
      "Not yet" keeps the turn and the orders.
- [ ] **Unit badges as symbols.** ! waiting for orders, zz no moves
      left, arrow on its way, circular arrow trade route, eye sentry,
      shield outline fortifying, filled shield fortified, double
      chevron skipped, hammer pioneer at work, plus under repair,
      house in a colony. Only your own units; other nations keep the
      letters. Option: Preferences > Interface > Map View > "Unit
      badges as symbols" (off = classic letters).
- [ ] **Badge explanations on hover.** Rest the mouse on your units on
      the map: a tooltip explains each unit's badge in words. Option:
      "Explain unit badges on hover".
- [ ] **View > Unit Badges.** A legend of all badges with the classic
      letter next to each.
- Sandbox results for Round 3 (2026-10-12): an arrow-key step moved
  all four army members (step 13 OK); marching together kept the
  scout with the soldiers; Rush sent the scout ahead (3 tiles further).

### Not yet committed (written 2026-10-12, fourth round)

- [ ] **Companion page for a second monitor.** FreeCol now serves a
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
- [ ] **Colopedia on the companion page** (Mischa: "connect the wiki").
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
- [ ] **Borderless full screen.** Exclusive full screen made the game
      flicker black whenever the other monitor got the focus (Windows
      minimises it, FreeCol restores it). New option Preferences >
      Display > "Borderless full screen (for two monitors)", on by
      default: a window without borders over the whole screen instead.
      Restart after changing. Not testable in the sandbox (no full
      screen there): Mischa to check.
- [ ] **Undo: Game > Restart This Turn / Go Back One Turn.** FreeCol
      has no undo (the server decides everything), but it already saves
      at the start of every turn (Autosave-<game>_<nation>_<year>.fsg).
      These two menu items reload that save after a confirmation, for
      mistakes like disbanding the wrong unit. Sandbox-checked: Restart
      This Turn reloads the game at the start of the turn; with no save
      for a turn it says so.
- [ ] **Borderless full screen leaves the taskbar free.** Windows drew
      its taskbar over the bottom of the borderless game (hiding the
      order buttons) when the other monitor had the focus. The game now
      fills the screen above the taskbar. Mischa to check.
- [ ] **Prices in the amount dialog (Europe).** When buying from or
      selling to the market in Europe (Shift+drag), the dialog shows the
      price as you type: "Cost: 20 × 3 = 60 gold, gold afterwards ..."
      (no tax on purchases) or "Sale: ... tax 10% = ..., you receive ...
      gold afterwards ...", and warns about boycotts. Same formulas as
      the server (ServerPlayer.buyInEurope / sellInEurope).
      Sandbox-checked (screenshots of both).
- [ ] **Treasury line on the Europe screen**, next to the buttons: gold,
      tax, taxes paid so far this game, trade balance (sales after tax
      minus purchases), from the market's own records. Updates every
      second. Sandbox-checked.
- [ ] **Europe market section on the companion page**: tax, taxes paid
      and trade balance; a price chart per goods (you sell for / you
      buy for, step lines, hover for the turn, colours validated for the
      dark background, buy line dashed); a table of all goods with sell
      and buy price, change over 10 turns, how much you sold, tax paid.
      Click a row to chart it. The game does not keep old prices, so
      they are recorded each turn to companion/prices-<game>.csv in the
      FreeCol user folder: the history starts with this version and
      grows as you play. Sandbox-checked with seeded history.
- [ ] **Relations tab on the companion page** (R, or the Relations
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
- [ ] **claude_docs/tribes.md**: the eight tribes, from the rules and
      the trade code: settlements, temper, region, richness, skills with
      odds, what they sell, how prices work, mood, haggling odds,
      teaching rules (capitals teach repeatedly), missionary vs scout vs
      soldier.
- [ ] **claude_docs/freecol-advisor-prompt.md**: a prompt to paste into
      Claude Haiku 5.5 (or a Project) to ask FreeCol questions. Contains
      the verified core rules, the tribe guide, our extra tools, and
      tables of buildings, units and founding fathers generated from the
      rule files.
- [ ] **Fix: asking natives for a lower price when buying failed.**
      Reported by Mischa (90 furs at an Iroquois village). FreeCol bug,
      not ours: the haggle offer is sent with the price unset, it
      overwrites the server's copy of the trade, and the server then
      found nothing buyable ("Can not buy"). Selling already skipped
      that check while haggling; buying now does the same
      (server/control/InGameController.nativeTrade). Unit test
      NativeTradeHaggleTest fails without the fix with the exact error
      from Mischa's log, passes with it. Natives may still refuse to
      haggle further, that is the normal rule.
- [ ] **Fix: wagons on an edited route counted as "no carrier".**
      FreeCol's own TradeRoute.getAssignedUnits() compares route objects
      by identity, and after a route is edited the client keeps two
      copies. The Colony Overview, Logistics report, supply warnings and
      companion page now match carriers by route id instead. Found in the
      sandbox: the game warned "no wagon train is assigned" for a route
      with two wagons. Unit test added.
- [ ] **Companion page: nations ranking.** A section under Colonies
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
- [ ] **Gift part of a load to the natives.** Choosing a gift now asks
      how much to give (all of it by default) instead of always handing
      over the whole hold. The server values the part itself, from its
      own copy of the trade, so a client can not inflate the value, and
      the rest stays on offer. Files: NativeTradeItem (partMatcher,
      withAmount), NativeTrade (removeGiftFromUnit), server
      InGameController and NativeAIPlayer (match a part), client
      InGameController (amount dialog). Unit test NativeTradeGiftTest.
- [ ] **Colony advice that keeps the supply network intact** (Mischa:
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
- [ ] **Companion page says more with less** (Mischa: "a load of
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
- [ ] **High balling or low balling in the trade dialog.** When trading
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
- [ ] **Fix: the Trade Negotiation dialog was cut off on the right**
      (the right column and the Send button). The window was sized
      before the wrapped texts in the middle were laid out; it is now
      sized again once they are. The texts in the middle wrap narrower.
- [ ] **Routine colony messages go to the companion page** (Mischa: too
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
- [ ] **Warehouse dialog explains its numbers** (Mischa: "I just see 10
      90 50"). Each box now labels them Low %, High % and Keep (and
      Import with enhanced trade routes), with the amount in store under
      the icon instead of beside the numbers; one line under the title
      says what they mean: Low and High are warnings as a share of the
      warehouse, Keep is an amount that stays, trade routes and the
      custom house take only what is above it. Tool tips say the same.
- [ ] **Trade routes do not haul goods to a full colony** (Mischa: a
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

### Written 2026-10-13

- [ ] **Full screen on the right monitor** (Mischa: View > Full screen
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

- [ ] **Fewer supply warnings: only delays that make a delivery late.**
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

- [ ] **What each route moved, as a small graph.** Every unload by a
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

- [ ] **New trade route editor** (Mischa: the old dialog is clunky;
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

- [ ] **Fix: a soldier at the palisade could not be made a student**
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

- [ ] **Companion page in tabs, with plain-language nations and a
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

- [ ] **Nations in words, like the moods in Relations** (Mischa: "content"
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

- [ ] **Independence tab on the companion page** (Mischa: worried about
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

- [ ] **Test version: the unexplored map as an old chart** (Mischa: the
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

### Committed in 0893f2ac5 / 860678de9

- [ ] **Unit groups: armies, wagon trains, fleets.**
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
- [ ] **Logistics report** (Report menu, Shift+F7). Every trade route
      with its stops and what each stop loads, what it delivers to
      which colony and when (green in time, red too late or nothing
      coming), its carriers with group, position, cargo and next
      deliveries, and the wagon trains and ships without a route.
      Auto-checked: renders correctly, including a route with no
      carriers. Not seen yet: a carrier actually on its way with a
      delivery time.
- [ ] **Goods Overview report** (Report menu, Shift+F8). One row per
      goods type, one column per colony, totals in front. Each cell
      shows stock and change per turn; red for overflow or nearly
      gone, magenta for draining or nearly full. Auto-checked: renders
      correctly with 4 colonies. Not seen yet: many colonies (the
      table scrolls sideways).
- [ ] **Unit Overview report** (Report menu, Shift+F10). Every unit
      not at work inside a colony: group, where, what it is doing,
      moves left. Filters: All, Idle, Soldiers, Wagon trains, Ships,
      In a group. Click a unit to select it on the map. Auto-checked:
      renders, filters work, destinations show turns to go. Not
      checked: clicking a unit.
- [x] **Colony Overview made narrower.** Tested by Mischa 2026-10-12: OK. Only the main product is
      named, the other two show icon and amount; three stocks instead
      of four; tighter spacing. Auto-checked: the horizontal scrollbar
      is gone at a 1500 pixel wide window.

### Committed

Quick checks (2 minutes, `java -jar FreeCol.jar --debug-run 60`, click
Start Game, then Report > Colony Overview or Shift+F6):

- [x] **Smaller building icons.** Tested by Mischa 2026-10-12: OK. Auto-checked: rows are even.
- [x] **Building column colours.** Tested by Mischa 2026-10-12: OK. Red only for "Nothing"; magenta for
      "needs N more ..."; dark gray when it is simply counting turns.
      Auto-checked for "Nothing" only.
- [x] **Sortable columns.** Tested by Mischa 2026-10-12: OK. Click each of the seven headers: the rows
      reorder, an arrow appears on the sorted header, a second click
      reverses. Close and reopen the report: the sort is remembered.
      Auto-checked for "Mainly produces" and "Needs".

Longer check (about 15 minutes, needs your own colonies and a trade
route; start with `java -jar FreeCol.jar --debug` and use Debug > Add
Gold to buy a wagon train):

- [ ] **Supply forecast in the overview.** Colony B burns lumber
      (colonist in the carpenter's house), a wagon on a trade route
      loads lumber at A and unloads at B. In the overview, lumber under
      Needs for B is green and the tooltip names the wagon, the route
      and the arrival time.
- [ ] **Broken supply line in the overview.** Take the wagon off the
      route: lumber for B turns red, the tooltip says no wagon train
      or ship is assigned.
- [x] **Start-of-turn messages appear in the real game.** Auto-checked
      on 2026-10-12: after ending a turn the game produced "St. John's
      relies on trade route Test run for Lumber, but none of its
      carriers will bring any ..." and "Charlesbourg Royal relies on
      trade route Back haul for Lumber, but no wagon train or ship is
      assigned to that route any more ...". Still worth seeing on
      screen yourself.
- [ ] **Start-of-turn message: no carrier.** End the turn after taking
      the wagon off. A message says B relies on the route but nothing
      is assigned to it. It does not repeat on the following turns.
- [ ] **Start-of-turn message: nothing coming.** Wagon assigned, but A
      has no lumber to spare.
- [ ] **Start-of-turn message: too late.** B runs out before the wagon
      can arrive.
- [ ] **Start-of-turn message: delayed.** Block or hold the wagon for
      a turn while it is on its way, with so little in store that the
      hold-up makes it late (since 2026-10-13 a hold-up that still
      arrives in time gives no message).
- [ ] **Building column, goods on their way.** A colony whose build
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

## Decided

- **Needs column stays icons only.** There are few enough goods to
  remember, so no names there.
- **Food stays in "Mainly produces" for now.** To reconsider later:
  it shows up as a main product for many colonies (Food +3, +4), which
  is accurate but says little about what a colony is specialised in.
  One option is to hide it unless it is a real surplus, say +8 or more.

## Planned (designed, not built)

### Living diplomacy and native tribes (2026-10-13)
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

## Ideas, not started

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

### Wars and the computer players (checked in the source, 2026-10-13)
- The King declares war and peace on your behalf at random (Monarch
  DECLARE_WAR / DECLARE_PEACE); computer nations follow tension, which
  cools by itself. Not a difficulty matter (Easy meddles slightly less).
- Computer soldiers only seek targets within 8 moves, overland or from
  the ship they are on: no planned landings, so island maps see almost
  no land war. Bug: UnitSeekAndDestroyMission.scorePath drops the score
  for enemy units in the open (only colonies are ever targeted).
- Ideas: fix that bug; let the computer ship an army to an enemy colony
  when at war; option so the King's wars need real tension.

### Housekeeping
- Decide later whether any of this is worth offering upstream. If so,
  `claude_docs` should stay out of that pull request.
- Release: Mischa wants to release the mod once he is content with it.
  Before that: a list of what changed for players, and the options to
  turn features on or off.
