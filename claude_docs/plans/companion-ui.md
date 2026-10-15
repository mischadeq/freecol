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
- **Tribes (step 7):** their mood, trust, unrest against you and what
  they remember of you are shown like the European moods. Their
  friends among the tribes show only when you have a mission with them
  (your missionaries tell you); their old rival also when the two are
  at war (wars are public). News: who came to whose aid, who sided
  with the attacker, refugees, uprisings, settlements breaking off;
  not who stayed home (that is the caller's business). Allied
  settlements are public (saved on the settlement, sent to everyone).
- **Relations graph (5d):** nations and tribes you met; wars and
  alliances between the others come from the server (public, as in the
  Foreign Affairs report), so in multiplayer only your own; old rivals
  only as the tribe cards show them; allied settlements (public);
  coalitions (public).
- **Joint strike:** a coalition's target colony and landing turn show
  only to its members, never to the target.
- **Coalition buy-out price:** a member's asking price to leave is
  shown only on coalitions against you: the member lets the target
  know its price, nobody else.
- **Removed:** a developer file name ("claude_docs/tribes.md") in the
  Relations drawer.

## Not yet implemented (shown greyed on the Diplomacy tab)

| Feature | Step | Where it will show |
| --- | --- | --- |
| Grievances and ultimatums | 6 | Diplomacy, nation cards |
| Trade agreements | 4 | Diplomacy, Trade |
| A tribe taking back a breakaway settlement | after 7 | Tribes |

Built in step 7 (2026-10-15), on the tribe cards of the Diplomacy tab:
character, mood and trust meters, unrest meter (turns before they may
rise), what they remember of you, old rival and friends, allied
settlements, recent news (help, siding with the attacker, refugees,
uprisings, settlements breaking off). Also built: each coalition
member's asking price to leave a coalition against you ("leaves for
2,200 gold", step 5 buy-out).

When a feature is built, replace its `soon(...)` box in
`data/companion/companion.js` (`renderDiplomacy`, `renderTribes`) with
the real thing and update this table.
