# Steps 2 and 3: moods you can see, then the computer acts and makes offers

Written 2026-10-14, after "the computer arms its colonists and buys
ships" (commit 4f7b2581d). Read `CLAUDE.md` and the design in
`ai-diplomacy-and-natives.md` first. This plan merges the design's
build steps 2 and 3 with acting on war and peace, as agreed with Mischa.

Each step is one or more rounds; each round ends with tests, an
`ideas.md` entry and a commit (see `CLAUDE.md`).

## Where things stand

- The diplomat (`common/diplomacy`) scores every move for every nation
  each turn, from an honest view.
- The computer **acts** on: attacking a colony (`InvasionPlanner`),
  scouting (`ScoutPlanner`), arming colonists and buying a ship
  (`WarPreparations`).
- It does **not** act yet on: declaring war, making peace, alliances,
  paying a nation to fight another, trade offers, arming tribes. War
  and peace still come from the original `EuropeanAIPlayer.determineStances`
  (tension) and from the King.
- **Memories exist only in the replay**: `tools/replay/Replay.remember`
  makes `Memory` objects from what changed between two saves. In a live
  game every planner passes `null` memories to `WorldViewBuilder.build`,
  so opinion comes from tension alone.
- `Memory.Kind` today: WAR, COLONY_TAKEN, PEACE, UNIT_LOST,
  SHIP_DAMAGED, SETTLEMENT_DESTROYED. Trust exists on `NationView`
  (default 50) but nothing changes it yet.

## Step 2: moods and memories, live, on the companion cards

Goal: every computer nation remembers what others did to it, during a
real game, and the player can read the result: "France: wary of you.
Remembers: you sank 2 of their ships (1660), you made peace (1662)."

### 2a. Live memories on the server

- New `common/diplomacy/Memories` (a list per nation, like `Bearings`):
  add, fade (each `Kind` has a weight and a fade time), serialize to one
  line for an XML attribute, parse.
- Record them each turn in `EuropeanAIPlayer.startWorking`: keep last
  turn's `Snapshot` (move `Snapshot`/`TurnDiff` from `tools/replay` to
  `common/diplomacy` if needed, or call them from there) and turn the
  diff into memories, the same way `Replay.remember` does. One code path
  for replay and game.
- Add the kinds that matter for trust: `DEAL_KEPT`, `DEAL_BROKEN`,
  `GIFT`, `TRADE` (they sold to us or bought from us),
  `HELPED_IN_WAR` (fought our enemy), `REFUSED_OFFER`. Weights and fade
  times as constants with a comment each.
- Trust from memories: start at `Relations.DEFAULT_TRUST`, kept deals
  raise it, broken ones cut it hard. Fill `NationView.trust`.
- Save with the game: an attribute on the AI player, like `sightings`
  and `keptArmed` (lazy getter, see "Things that bit us" in
  `CLAUDE.md`).
- Pass the live memories to every `WorldViewBuilder.build` call
  (`InvasionPlanner`, `ScoutPlanner`, `WarPreparations`): replace the
  `null`.
- Better: build the `WorldView`, `Situation` and ranking **once per
  turn** in `EuropeanAIPlayer` and let the planners share it. Today each
  planner builds its own.

### 2b. Moods on the companion page

- What a nation thinks of the player is public enough to show, like the
  game's own tension (Mischa agreed moods are shown "in words, not
  numbers"). The client runs the server in the same program in a
  single-player game: `FreeColClient.getFreeColServer()` gives the AI
  players. In a multiplayer game, show nothing.
- Per nation card on the Nations tab (`CompanionNations`,
  `data/companion/companion.js`):
  - a mood chip: friendly / neutral / wary / hostile (from opinion),
    and trust: trusts you / careful / does not trust you;
  - their ambition (the `NationCharacter`: trading empire, conqueror, ...)
    and current goals in words;
  - "Remembers": the 3 strongest memories about you, with year, on hover
    all of them.
- Their feelings about each other (France angry at Spain) only when the
  player could know: they are at war, or allied (public stances).

### 2c. Tests and done

- Unit: memories fade, serialize/parse, trust goes up and down, a diff
  of two snapshots gives the right memories, save and load keeps them
  (see `ScoutPlannerTest` for a save/load round trip).
- Companion: the nations JSON has `mood`, `trust`, `remembers` for
  computer nations and nothing about hidden things.
- Done when Mischa sees moods change on the cards while he plays: sink
  a French ship, the French card turns wary and remembers it.

## Step 3: the computer acts on war and peace, and makes offers

### 3a. War and peace between computer nations

- In `EuropeanAIPlayer.determineStances`, for another **computer**
  nation: take DECLARE_WAR / MAKE_PEACE / ALLIANCE from the diplomat
  when they score at least `Diplomat.THRESHOLD` (20), instead of the
  tension rule.
- Commitment (design, "Decisions"): a choice holds at least 10 turns; a
  new choice must beat the current one by 30%; a shock (war declared on
  us, a colony lost, an ally betraying us) lifts the hold. Keep the
  last choice and its turn in the AI player (saved).
- **Toward the human player, keep the old rule for war in this step.**
  War on the player should come with a grievance and an ultimatum first
  (design step 6), so it never comes out of nowhere.
- The King's wars stay as they are (an option for them is step 6).
- Check with the replay on Mischa's saves: wars between computer
  nations should now have a reason (the "own wars with a reason" count).

### 3b. Offers to the player

- Through the game's own diplomacy screen: `AIMessage.askDiplomacy(aiPlayer,
  our, other, DiplomaticTrade)` proposes a trade; `our`/`other` are
  units or colonies. `AIPlayer` already uses it (around line 330).
- **Checked (2026-10-14):** the game needs a unit next to the other's
  colony; `DiplomacyMessage.serverHandler` refuses an offer otherwise
  ("not adjacent", "Both units null").
- **Decided with Mischa (2026-10-14): a mix.**
  - **Peace terms and gold-only deals come by messenger:** instant, at
    the start of the player's turn, no unit needed. This needs a new
    server path (a session without the adjacency check, from the
    computer's capital colony to the player's).
  - **Goods require ships:** the goods are loaded on a ship that sails
    to the player's nearest coastal colony; the trade screen opens when
    it is next to the colony. The player can watch it come. If the ship
    is sunk or turned away, no deal. Purchase requests (BUY_GOODS) also
    send a ship, empty, with the gold.
- Offers, from the diplomat's moves that score 5 or more:
  - **Peace terms** when the computer is losing a war with the player
    (MAKE_PEACE), with gold if its score says so.
  - **Goods offer** (SELL_GOODS): its surplus or boycotted goods, for
    gold, at the price the diplomat used.
  - **Purchase request** (BUY_GOODS): goods it is short of.
- Rules (design, "Decisions"): at most one offer per nation per 5
  turns; the same terms stay on the table for 5 turns; routine offers
  can go to the companion log instead of a popup (Mischa to choose).
- Every answer becomes a memory: accepted trade (TRADE), refused
  (REFUSED_OFFER); a deal not delivered (DEAL_BROKEN).
- The diplomat's answer to the **player's** offers: today
  `EuropeanAIPlayer.acceptDiplomaticTrade` decides. Let it ask the
  diplomat (score of the deal as an Action) so the same values are used
  both ways.

### 3c. Tests and done

- Unit: commitment holds 10 turns and needs a 30% better score; a shock
  lifts it; no war on the human from the diplomat in this step; offers
  respect the 5-turn limit; an accepted offer moves goods and gold and
  leaves a memory.
- In a game: computer nations make and end wars with each other for a
  reason that shows on their cards, and the player gets the occasional
  offer that makes sense ("200 furs for 400 gold" from a nation with a
  fur surplus and a boycott).

## Not in these steps

Trade agreements over many turns (design step 4), coalitions (5),
grievances and ultimatums and the King's-wars option (6), natives (7),
long computer-only tuning games (8). A nation with no usable guess of
where an enemy lives still does not explore; buying soldiers in Europe
still follows the old rules.
