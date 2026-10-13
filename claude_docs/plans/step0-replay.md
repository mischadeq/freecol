# Step 0 build plan: replay your saves, score every turn

Status: **planned** (2026-10-13), to build and run on 2026-10-14.
Part of `ai-diplomacy-and-natives.md` (read that first for the idea).

Goal for tomorrow: a tool that reads the 244 autosaves of Mischa's game
in pairs, lists what every nation did each turn, runs a real diplomat
score function on the turn before, and writes a report: one row per
turn per nation, with what they did, the diplomat's top 3 with the score
broken down, agree or not, and how it turned out (hindsight).

Nothing in the game changes in step 0. The diplomat only reads; the
computer players keep playing exactly as today.

## What is in a save (checked in the Spring 1669 autosave)

| Data | In the save? | Used for |
| --- | --- | --- |
| Stance per pair of nations | yes (`<stance>` per player) | war and peace changes |
| Tension per pair | yes, for computer players (`<tension>`, 0 to 1100) | opinion, who declared a war |
| History events, with turn and other nation | yes (`<historyEvent>`: declare_war, make_peace, found_colony, conquer_colony, destroy_settlement, ...) | memories, wars |
| Goods sold in Europe, cumulative per nation | yes (`<marketData sales incomeAfterTaxes>`) | trade per turn (difference between saves) |
| Gold, tax, liberty bells, score | yes (player attributes) | economy |
| Every unit with owner, type, role, place | yes (map tiles, colonies, Europe) | armies, losses (unit ids stay the same between saves) |
| Colonies (owner, size, buildings, stock) and native settlements (alarm, missionary) | yes | land, missions |
| What each nation has explored | yes (`<cachedTile player=...>` per tile; `Tile.isExploredBy`) | the honest view |
| The computer players' plans (missions) | yes (`<aiMain>`), but only readable with a running server | skipped in step 0 |
| Combat messages, diplomatic trades (gold or goods swapped) | **no** | can only be guessed |
| Who declared a war | **no** (both sides get the same history event) | guessed, see A3 |

A first look already says something: in the 1669 save the 344 units of
the computer players have 0 seek-and-destroy or privateer missions. They
are not trying to fight anyone (113 work in colonies, 100 defend).

## New files

Pure logic goes in a new package `net.sf.freecol.common.diplomacy`. It
uses no server and no client code, so the same diplomat can later run
inside the computer players (step 3) and show its thinking on the
companion page.

| File | What it does |
| --- | --- |
| `Character.java` | The characters (trading empire, conqueror, balance keeper, settler, independence first) with their weights; picks one from the nation type (A8) |
| `Estimates.java` | Soldier and frigate power, output, per-turn rates, verdict words; moved out of the companion's `NationEstimate` so both use the same numbers |
| `CombatMath.java` | Attack and defence with the game's modifiers, win chance; moved out of `CompanionIndependence` |
| `NationView.java` | What one nation knows of another: army, navy, output, gold, stance, the colonies it has seen (size, defence guess), how far away; each figure marked exact or estimated |
| `WorldView.java` | One nation's whole picture for one turn: itself (exact) plus a `NationView` per other nation |
| `WorldViewBuilder.java` | Builds a `WorldView` from a game for one nation: **honest** (Foreign Affairs figures through the game's own `NationSummary`, colonies only on tiles it explored) or **hindsight** (everything, for judging only) |
| `Memory.java` | A dated event with a weight that fades (e.g. "declared war on us", −30 over 30 turns) |
| `Relations.java` | Opinion, trust and fear of each other nation, from tension plus memories |
| `Threat.java` | The threat formula and who would join a coalition against whom |
| `Goal.java`, `Goals.java` | Short-term goals with their triggers, the long-term ambition, and the commitment rules |
| `Action.java` | One possible move: its kind, target nation, goods, amount, price, colony |
| `ActionGenerator.java` | Lists the moves that make sense for a nation this turn, "do nothing" always included |
| `Effects.java` | For each move, the expected change in gold, trade, land, security and relations over the next 10 turns (formulas below) |
| `Risk.java` | Chance a move fails times what is lost, using `CombatMath` (a small seeded simulation for attacks on colonies) |
| `Scorer.java`, `ScoredAction.java` | The score function; keeps every part of the sum so the report can show why |
| `Diplomat.java` | One call: world view in, ranked moves out |

The replay tool goes in `net.sf.freecol.tools.replay` (next to the
existing tools; it is not part of the game):

| File | What it does |
| --- | --- |
| `SaveSeries.java` | Finds the autosaves of one game, orders them by turn (from the year and season in the name, checked against the turn inside), skips gaps; reads each with `FreeColServer.readGame(file, null, null)`, the same read-only way the map editor loads a map |
| `TurnDiff.java` | Everything that changed between turn N and N+1 for every nation (list in "Observed moves" below) |
| `ObservedAction.java`, `ActionExtractor.java` | Turns a diff into moves in the same terms as `Action`, with who did it and whether it was forced (the King) |
| `Hindsight.java` | How a move turned out over the next 10 saves: gold, colonies, army, how long a war lasted, colonies lost |
| `ReplayReport.java` | Writes `replay.csv` and `replay.html` (one row per turn per nation, filters per nation, totals at the top) |
| `ReplayMain.java` | `java -cp FreeCol.jar net.sf.freecol.tools.replay.ReplayMain <save folder> <game id> <out folder>`; runs without a display |

## Changed files

- `client/companion/NationEstimate.java` and
  `client/companion/CompanionIndependence.java`: keep their public
  methods but call `Estimates` and `CombatMath`. The companion page
  shows exactly the same numbers (the existing tests prove it).
- `build.xml`: an ant target `replay` with the three arguments.
- `claude_docs/ideas.md`: the round's entry.
- **Nations themselves: no changes in step 0.** No game rules, no
  specification, no AI code. The character of each nation is picked in
  code from its nation type. Giving the computer players the diplomat
  comes in step 3, once the replay shows the scores make sense.

## The score function, concrete

For a move *a* by nation *me* towards nation *X*:

```
score(a) = w_gold·Δgold + w_trade·Δtrade + w_land·Δland
         + w_security·Δsecurity + w_relations·Δrelations
         + goal(a) + 0.3·side(a)·opinion(me→X) − ρ·risk(a)
```

- Everything is in points over the next **10 turns**: 100 gold = 1
  point; a colony = 40 points + 3 per colonist; one enemy soldier that
  can reach you = −5 points of security.
- `side(a)` is +1 for friendly moves (trade, peace, alliance) and −1 for
  hostile ones (war, incite against X), so liking X makes trade more
  attractive and war less.
- `goal(a)`: +30 if the move serves an active short-term goal, +15 if it
  serves the ambition.
- A move is made if its score is at least **20** and beats "do nothing"
  (always 0). At most one big move (war, peace, alliance) per turn.

Building blocks (all from the nation's honest view):

- **Army** A and **navy** in soldiers' and frigates' worth (Foreign
  Affairs strength ÷ 3 and ÷ 24, as on the companion page).
- **Output** O in gold a turn (colonists × our gold per colonist).
- **Nearness** near(me, X): 1 if X has a colony within 10 tiles of one of
  mine on the same land, 0.5 if only reachable by sea, 0.2 if far.
- **Exposure**: Σ over nations at war with me of A × near.
- **Fear** of X: 100 × A_X ÷ (A_me + A_X) × near(me, X).
- **Threat** of X: 0.6 × X's share of all armies + 0.4 × X's share of all
  output (European nations only, not the King's forces).
- **Taking a colony**, P_take: a seeded simulation of half my army
  attacking X's weakest colony in reach, each fight won with
  attack ÷ (attack + defence); the colony falls when its defenders are
  beaten. Across the sea it also needs ships with room for them.

| Move | When it is listed | Δgold | Δtrade | Δland | Δsecurity | Δrelations | Risk |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Do nothing | always | 0 | 0 | 0 | 0 | 0 | 0 |
| Declare war on X | at peace, contacted | −1% of my output a turn (trade disrupted) | 0 | P_take × colony value | −5 × A_X × near | −10 with X; −3 with each nation that likes X | (1 − P_take) × units lost × 5 + X's chance to take my weakest colony × its value |
| Attack a colony of X | already at war | 0 | 0 | P_take × colony value | 0 | −3 with X | as above, without the war itself |
| Make peace / cease-fire with X | at war | +1% of output a turn | 0 | −(what the war could still win) | +5 × A_X × near | +5 with X | 0 |
| Alliance with X | at peace, opinion ≥ 20 | 0 | 0 | 0 | +2.5 × A_X if we share an enemy; −1.5 × the armies of X's enemies near me | +5 with X | (1 − trust ÷ 100) × 10 |
| Pay X to fight Z (incite) | X and Z at peace, my opinion of Z ≤ −20 | −price | 0 | 0 | +2.5 × min(A_X, A_Z) × near(me, Z) | −5 with Z if found out (30%) | price × (1 − trust in X ÷ 100) |
| Sell goods to X | surplus or boycotted, X short of it | amount × 0.8 × Europe price | the same | 0 | 0 | +2 with X | refused: chance × 1 point |
| Buy goods from X | short of tools, muskets or horses | −amount × 1.2 × price + 1.5 × amount × price (what it unblocks) | amount × price | 0 | 0 | +2 with X | refused: chance × 1 point |
| Trade agreement with X | as the two above, both ways | per turn × 10 turns × trust ÷ 100 | the same | 0 | 0 | +5 with X | broken: (1 − trust ÷ 100) × 10 |
| Arm k soldiers | muskets in store or gold | −cost of the muskets | 0 | 0 | +5 × k if exposure > 0 or fear > 40, else +1.5 × k | 0 | 0 |

The last row is not diplomacy, but it is the most common military
choice (Mischa's army went from 4 to 18 soldiers' worth between 1662
and 1669), so the replay can compare it.

**Goals and their triggers** (checked every turn):

| Goal | Trigger |
| --- | --- |
| Sell a surplus | a good above 80% of a colony's warehouse, or boycotted with more than 100 in store |
| Get a missing good | tools, muskets or horses falling short within 3 turns, or a building waiting for tools |
| Take a colony | at war with X and P_take ≥ 0.5 for one of X's colonies |
| Defend | enemy units within 3 tiles of my colony (only tiles I can see) |
| Contain the leader | another nation's threat ≥ 0.35 |
| Make peace | at war and exposure > my army, or 20 turns at war without gaining a colony |
| Revenge | a memory about X of −40 or worse in the last 30 turns |

**Opinion** (−100 to +100) = 100 − tension × 200 ÷ 1100, plus the
memories that have not faded yet:

| Memory (from the history events and the diffs) | Weight | Fades over |
| --- | --- | --- |
| X declared war on us | −30 | 30 turns |
| X took our colony | −60 | 60 turns |
| X made peace with us | +10 | 20 turns |
| We lost a unit to X | −5 | 10 turns |

**Characters** by nation type (the four classic ones are the four
European nations in this game):

| Nation type | Character |
| --- | --- |
| trade (Dutch) | Trading empire |
| cooperation (French) | Balance keeper |
| immigration (English) | Settler |
| conquest (Spanish) | Conqueror |
| naval, building, agriculture, furTrapping, default | Trading empire, Settler, Settler, Trading empire, Settler |

Weights as in the design (trading empire: gold 1.0, trade 1.5, land 0.3,
security 0.8, relations 1.0, ρ 1.2; and so on).

## Observed moves (what the diff finds)

| Change between N and N+1 | Becomes |
| --- | --- |
| Stance peace → war | a war between the two, with the declarer guessed (A3) |
| Stance war → peace or cease-fire | peace |
| A colony changes owner / disappears | colony taken / destroyed |
| Military units of a nation at war missing in N+1 | units lost in that war |
| A ship under repair in N+1 that was not in N | ship damaged |
| Army up by 1 soldier's worth or more | armed k soldiers |
| Goods sold in Europe (market difference) | trade, logged, not compared |
| A missionary appears or leaves a settlement; a settlement destroyed; tension changes with natives | native moves, logged for the native-tribes step |

## How it is judged

- **Agreement**: of the turns where a nation made a foreign move, how
  often that move was in the diplomat's top 3. Separately for Mischa and
  for each computer nation.
- **Unprompted**: turns where the diplomat had a move scoring 20 or more
  but the nation did nothing.
- **Flip rate**: how often the diplomat's top move changes from one turn
  to the next. Tells us how strong the commitment rules must be.
- **Wars with a reason**: for each real war, did the diplomat score war
  for that pair above 20 in the turns before?
- **Hindsight**: for each move made, its realised gain over the next 10
  turns with that nation's own weights; "worked out" if positive.

Then the weights are tuned by hand with Mischa: agree more with moves
that worked out, less with those that did not.

## Assumptions

Numbered so we can refer to them. Each says how we check it.

- **A1. An autosave is made at the start of Mischa's turn**, so save N+1
  minus save N is everything all nations did in turn N. Check: the turn
  inside each file is one higher than the one before.
- **A2. Saves from the older builds of the mod load fine.** The mod did
  not change the save format (its own data is in separate files).
  Check: load the oldest (1493) and newest (1669) first thing.
- **A3. Who declared a war is guessed.** A computer nation whose tension
  towards the other was 1000 or more (hateful) chose the war; a war with
  nobody hateful was the King's. For Mischa's own wars we ask him.
- **A4. Reading is read-only.** Saves are staged as copies;
  `readGame(file, null, null)` builds no server and no AI and writes
  nothing. Mischa's save folders are never written to.
- **A5. The computer players' missions are skipped** in step 0 (they need
  a running server). Enough to see what they did, not what they planned.
- **A6. The honest view is the game's own Foreign Affairs report**
  (`NationSummary`: colonies, units, military and naval strength, gold
  for everyone; rebels, fathers and tax only with Jan de Witt), plus the
  colonies on tiles that nation has explored.
- **A7. Opinion comes from tension plus memories.** Mischa has no
  tension towards Europeans (the game only keeps it for computer
  players), so the diplomat playing as the Dutch uses the other side's
  tension mirrored.
- **A8. Characters come from the nation type**, as in the table above.
- **A9. Trust starts at 50 for everyone.** No deals exist yet, so it
  cannot move in step 0.
- **A10. Diplomatic trades are invisible.** Gold or goods swapped through
  the diplomacy screen leave no trace in a save except a gold change, so
  they are not counted. Mischa tells us if he made any.
- **A11. "Reach" means the same land or enough ship room.** A colony on
  another island needs ships that can carry the attack force; today's
  computer players never do this, which the report should show.
- **A12. Points and prices are rough on purpose** (100 gold = 1 point, a
  colony 40 + 3 per colonist, a soldier near you −5, 10-turn horizon, no
  discounting). Tuning is what step 0 is for.
- **A13. It runs in minutes.** 244 saves of about 2 MB; only two games in
  memory at a time.
- **A14. Few data points for Mischa.** About 5 wars, a few peaces, the
  arming, the native wars: enough to see if the scores make sense, not
  to fit weights precisely. The computer nations add many more turns,
  but they play the old way.

## Questions for Mischa

1. Your game history has five war declarations. Which did **you**
   declare, and which did the King force on you?
   - turn 166, autumn 1628: France
   - turn 203, autumn 1647: England
   - turn 209, autumn 1650: Spain
   - turn 218, autumn 1654: the Iroquois
   - turn 225, spring 1658: France
2. Did you ever trade gold or goods with another European nation through
   the diplomacy screen?

## Tests

In `test/src/net/sf/freecol/common/diplomacy/` and
`test/src/net/sf/freecol/tools/replay/`:

- `ThreatTest`: the worked example (Dutch 0.17, Spain 0.37).
- `ScorerTest`: hand-made world views where war, peace, a sale or doing
  nothing must win; the score parts add up to the total.
- `RelationsTest`: opinion from tension; memories fade on time.
- `RiskTest`: the simulation's win chance matches attack ÷ (attack +
  defence) for one fight.
- `CharacterTest`: every nation type gets a character.
- `TurnDiffTest`, `ActionExtractorTest`: two small test games with a
  stance change, a captured colony and lost units.
- `SaveSeriesTest`: file names to turns (1493 is turn 2; spring 1600 is
  turn 109; spring 1669 is turn 247), gaps skipped.
- The existing companion tests stay green after the move to `Estimates`
  and `CombatMath`.

## Order of work tomorrow

1. `SaveSeries`, `TurnDiff`, `ActionExtractor` and a report of what
   happened only. That alone answers "what did the computer players
   actually do in 244 turns".
2. Move `Estimates` and `CombatMath`; `WorldViewBuilder` (honest and
   hindsight).
3. `Relations`, `Threat`, `Goals`, `Effects`, `Risk`, `Scorer`,
   `Diplomat`, with their tests.
4. Compare and judge: `Hindsight`, the full report, run over all 244
   saves, then look at it together.
