# FreeCol UI mod: ideas and status

Goal: make FreeCol's UI workable and give proper overviews.
Branch: `colony-overview` on the fork `mischadeq/freecol`.

Last updated: 2026-10-11

Status legend: **Done** = written and seen working in the game.
**In progress** = being worked on now. **Idea** = not started.

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

## In progress

- **Colony Overview polish.**
  - Smaller, fixed-size building icons and no horizontal scrollbar
    (written, needs a look in the game).
  - Building column colours: red only for "nothing being built",
    magenta for "waiting for goods" (written, needs a look in the
    game).
- **Supply-aware warnings** (Mischa's idea, 2026-10-11). A shortage
  should only raise attention when no resupply is on its way in time.
  Written and unit tested (6 tests pass), not yet seen in the game.
  - `SupplyForecast`: walks every wagon train and ship along its trade
    route from where it is now, and works out which goods it will
    unload at which colony after how many turns.
  - Colony Overview, Needs column: green when a trade route resupplies
    in time; red when a route should deliver but has no carrier, has
    nothing coming, or will be too late. The tooltip names the carrier,
    the route and the arrival time.
  - Colony Overview, Building column: "Tools arriving in 3 turns"
    instead of "needs 20 more Tools" when enough is on its way.
  - `SupplyWatch`: message at the start of the turn when a supply line
    stops covering a colony: last carrier taken off the route, nothing
    coming, next delivery too late, or a carrier delayed. Sent when the
    situation changes, not every turn. Message type "Missing goods".
  - Known limits:
    - The forecast assumes carriers visit every stop in order. The
      game lets them skip stops with no work, so times are estimates.
    - "Delayed" compares with last turn's expectation, which is kept
      in memory only. After loading a save the first turn has nothing
      to compare with.
    - A carrier that spends a turn loading counts as one turn delayed.
      If that is too noisy, add a threshold.
    - A delay on the very last leg into the colony is not detected.
    - A broken supply line is noticed at the start of the next turn,
      not at the moment the wagon is reassigned. The overview shows it
      immediately.
- **Nothing is committed yet.** All changes are uncommitted on the
  `colony-overview` branch.

## Decided

- **Needs column stays icons only.** There are few enough goods to
  remember, so no names there.
- **Food stays in "Mainly produces" for now.** To reconsider later:
  it shows up as a main product for many colonies (Food +3, +4), which
  is accurate but says little about what a colony is specialised in.
  One option is to hide it unless it is a real surplus, say +8 or more.

## Ideas, not started

### Colony overview
- Sortable columns (click a header to sort by size, product, etc.).
- A totals row for the whole empire.
- Option to show gross production instead of net.
- Show hammers / construction capacity as a product.

### Logistics and wagon trains
- Logistics view: every trade route, the wagons and ships on it, and
  what they actually moved recently.
- Wagon "trains": several wagons handled as one for assignment, so a
  train of four goes onto a route in one action.
- Suggested routes: link a colony that needs a good to one with a
  surplus, straight from the overview.
- Better trade route setup dialog (the current one is clunky).
- Check the wagon limit in the base rules. The `shippableWagons` mod's
  copy of the wagon definition limits wagon trains to fewer than the
  number of colonies. If the base rules have it too, a small companion
  rules mod could lift it.

### Armies and unit groups
- Client-side groups: name a group, select it as one, give one move
  order that every member follows. Units stay separate for the engine,
  so rules, AI, saves and multiplayer are untouched. (Chosen over true
  stacks, which would need server, combat and AI changes.)
- The same grouping code should power wagon trains.
- A "form group" order button. The `plantForest` mod shows how to add
  an order button with its four image states.

### Other overviews
- Empire-wide goods view: where goods are, which warehouses are about
  to overflow, where the shortages are.
- Unit overview: filterable list of all units, idle units, what each
  wagon and ship is doing, click to jump to it.
- Turn messages: group and filter end-of-turn reports, click to go to
  the colony concerned.

### Colony screen and map
- Colony screen: show what a tile or building would yield before
  committing a worker to it.
- Map overlays: tile ownership, potential production, colonies with
  problems.

### Housekeeping
- The folders `Freecol\freecol` and `Freecol\mods` are empty git repos
  wrapped around the real clones. Harmless, but could be cleaned up.
- Decide later whether any of this is worth offering upstream. If so,
  `claude_docs` should stay out of that pull request.
