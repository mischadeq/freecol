# The companion page: what each tab shows, and where it comes from

Written 2026-10-14 for Mischa. The page runs on a second screen next to
the game and only shows: the player acts in the game. Rule: it shows
only what the player could know (see "Honest view" in `CLAUDE.md`).
"Public" below means every nation knows it in the game too (stances,
wars and alliances, coalitions, the Foreign Affairs report).

Keys: 1 to 8 switch tabs; L, R and C open the Log, Relations and
Colopedia drawer.

## The tabs

| # | Tab | What it is for | What it shows | Where it comes from | Honest? |
| --- | --- | --- | --- | --- | --- |
| 1 | Overview | Your own mix | Any widgets you pick (Widgets button) | as below | yes |
| 2 | Colonies | Running your colonies | Colony cards with advice chips, empire goods | your colonies | yes (your own) |
| 3 | Trade | Moving goods | Map, offers, supply network, route charts, Europe prices | your units, routes, market; offers you were shown | yes |
| 4 | Nations | How you compare | Cards (figures, mood chips, wants, remembers, feels), ranking table, chart back to the start | the Foreign Affairs report, colonies you have seen, your own fights; moods from the computer nations (single player) | yes, see notes |
| 5 | Diplomacy | Who stands where | You and each nation (stance, mood, trust, notes), wars and alliances between the others, coalitions, offers, the tribes, what is coming | public stances, the computer nations' moods and memories of you, the tribes you met | yes |
| 6 | Military | Forces | Soldiers and frigates per nation (estimates), sea fights | Foreign Affairs strength, combat messages | yes |
| 7 | Independence | The war with the King | Your army against the King's, rebels per colony, help after declaring | your units, the King's army (the game shows it), game options | yes |
| 8 | History | The game so far | Map replay of land and colonies, land chart | turns recorded while the page runs, and your old autosaves | yes: only what you had seen then |

Drawers: **Log** (your turn report and routine messages), **Relations**
(every European nation and tribe, per settlement: mood, mission,
skill, wanted goods, most hated: only after a visit), **Colopedia**.

## Honesty check (2026-10-14)

Checked every place the page reads another nation's data.

- **Fixed: units "seen now".** The Nations cards counted another
  nation's units from the list the game last heard of, not from the
  tiles in sight. Now only the tiles you see now.
- **Fixed: foreign units on the map.** Now only on tiles in sight.
- **Fixed: their goals.** The cards showed every short-term goal of a
  computer nation, war plans too ("take a colony from you", "revenge
  on you"). Now only what a nation makes known: goods it wants to sell
  or buy (it posts them on the notice board anyway) and that it wants
  peace with you. War plans will show as grievances and ultimatums
  (step 6) or a public coalition.
- **Checked, fine:** native skill, wanted goods and most hated (the
  game sends them only after a visit or scouting); native mood (the
  game shows it on the settlement panel); the Foreign Affairs figures
  (the game's own report, also for nations not met); the King's armies
  (shown by the game, intervention force is a game option); sea fights
  (from your combat messages); where nations live (your sightings and
  colonies you explored); offers (only those you were shown, took or
  posted); old autosaves (each tile as you saw it then).
- **On purpose:** a computer nation's mood and trust towards you, and
  what it remembers about you, are shown in words (Mischa's decision,
  step 2): the game shows tension with the tribes the same way. Only
  in single-player games, where the computer nations run in the same
  program.
- **Removed:** a developer file name ("claude_docs/tribes.md") in the
  Relations drawer.

## Not yet implemented (shown greyed on the Diplomacy tab)

| Feature | Step | Where it will show |
| --- | --- | --- |
| Relations graph | 5d | Diplomacy |
| Joint attack plans of a coalition | after 5 | Diplomacy |
| Grievances and ultimatums | 6 | Diplomacy, nation cards |
| Trade agreements | 4 | Diplomacy, Trade |
| Friends and rivals among the tribes | 7 | Tribes |
| Unrest meter, uprisings | 7 | Tribes, turn report |
| Calls for help and refugees | 7 | Tribes, turn report |
| Allied settlements | 7 | Tribes, Relations |

When a feature is built, replace its `soon(...)` box in
`data/companion/companion.js` (`renderDiplomacy`, `renderTribes`) with
the real thing and update this table.
