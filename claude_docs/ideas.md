# FreeCol UI mod: ideas and status

Goal: make FreeCol's UI workable and give proper overviews.
Branch: `colony-overview` on the fork `mischadeq/freecol`.

Last updated: 2026-10-12

Status legend: **Done** = written and seen working in the game.
**In progress** = being worked on now. **To be tested** = written and
committed, not yet checked in the game. **Idea** = not started.

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

Nothing at the moment.

## To be tested

Written, but not yet tried by Mischa in a real game. Move an item to
Done once it has been seen working.

"Auto-checked" means Claude ran the real game in an automated session
(50 AI turns, took over AI colonies in debug mode, added wagons and
soldiers, opened every report and took screenshots) and it worked
there. That is a smoke test, not a play test.

### Not yet committed (written 2026-10-12)

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
- [ ] **Colony Overview made narrower.** Only the main product is
      named, the other two show icon and amount; three stocks instead
      of four; tighter spacing. Auto-checked: the horizontal scrollbar
      is gone at a 1500 pixel wide window.

### Committed

Quick checks (2 minutes, `java -jar FreeCol.jar --debug-run 60`, click
Start Game, then Report > Colony Overview or Shift+F6):

- [ ] **Smaller building icons.** Auto-checked: rows are even.
- [ ] **Building column colours.** Red only for "Nothing"; magenta for
      "needs N more ..."; dark gray when it is simply counting turns.
      Auto-checked for "Nothing" only.
- [ ] **Sortable columns.** Click each of the seven headers: the rows
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
- [ ] **Start-of-turn message: no carrier.** End the turn after taking
      the wagon off. A message says B relies on the route but nothing
      is assigned to it. It does not repeat on the following turns.
- [ ] **Start-of-turn message: nothing coming.** Wagon assigned, but A
      has no lumber to spare.
- [ ] **Start-of-turn message: too late.** B runs out before the wagon
      can arrive.
- [ ] **Start-of-turn message: delayed.** Block or hold the wagon for
      a turn while it is on its way.
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

## Ideas, not started

### Unit groups, next steps
- Choose which units on a tile join a group, instead of all of them.
- Show the group on the map (a banner or number on the unit).
- Keep a group together: the faster members wait for the slowest.
- "Attack with the group" as an explicit order.
- A list of groups with a button to jump to each.

### Logistics, next steps
- Suggested routes: link a colony that needs a good to one with a
  surplus, straight from the overview.
- Better trade route setup dialog (the current one is clunky).
- Open the trade route editor from the Logistics report.
- What each route actually moved over the last turns.
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

### Housekeeping
- The folders `Freecol\freecol` and `Freecol\mods` are empty git repos
  wrapped around the real clones. Harmless, but could be cleaned up.
- Decide later whether any of this is worth offering upstream. If so,
  `claude_docs` should stay out of that pull request.
