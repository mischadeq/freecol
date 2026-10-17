# Mischa's FreeCol mod

A mod of FreeCol 1.2.0 (Java), branch `colony-overview`. It adds better
overviews and a companion web page on a second screen, and now a
diplomacy AI that makes the computer nations act with reasons.

Read these first, in this order:
1. `claude_docs/ideas.md`: what is built (newest entries just above
   "Ideas, not started") and what is waiting.
2. `claude_docs/plans/ai-diplomacy-and-natives.md`: the full design.
3. `claude_docs/plans/step2-step3.md`: steps 2 and 3 (built).
4. `claude_docs/plans/step5-step7.md`: the next two steps, in detail
   (alliances, then native tribes; both built). Then
   `step6-grievances.md` and `step4-price-agreements.md` (built).
5. `claude_docs/plans/companion-ui.md`: what each companion tab shows,
   where it comes from, the honesty check, and the "not yet
   implemented" boxes to replace as steps are built.

## Working rules

- Mischa plays and tests, Claude writes the code. Talk to him in plain,
  short English; he likes icons, chips and graphs over text.
- **Honest view**: a nation (or the player's companion page) only uses
  what it could know: its own data, explored tiles, what is in sight,
  public stances, the Foreign Affairs report. No peeking at hidden data.
- **Every round ends with**: unit tests (all green), an entry in
  `claude_docs/ideas.md` (what, why, what was tested, what is not done),
  one commit per feature.
- **Commits**: in Mischa's name:
  `git -c user.name="Mischa de Queljoe" -c user.email="mischa.de.queljoe@retailstars.nl" commit`.
  In a cloud session, work on your own branch; Mischa reviews and
  merges into `colony-overview` himself. Never force-push and never
  push to `colony-overview` directly.
- Plans go in `claude_docs/plans/`. Mischa wants to release the mod one
  day, so keep player-facing changes listed in `ideas.md`.

## Build and test

- Needs a JDK (11 or newer) and Apache Ant. If `ant` is missing:
  `apt-get install -y ant` (or download it from archive.apache.org).
- Build: `ant -q` (makes `FreeCol.jar`).
- One test suite: `ant -lib test/lib/junit.jar test -Dtest=common.diplomacy.AllTests`
  (also `tools.replay.AllTests`, `server.ai.AllTests`).
- Everything: `ant -lib test/lib/junit.jar test` (about a minute, 749
  tests at the last count). Results land in `build/TEST-*.txt`; delete
  those and `build/dependencies.txt` afterwards (they are untracked).
- Replay of a saved game (step 0 measuring tool; needs Mischa's
  autosaves, which are not in the repo, so he runs it on his PC):
  `ant replay -Dsaves=<folder> -Dprefix=<file prefix> -Dout=<folder> -Ddeclared=iroquois`.
  It writes `replay.html` with what each nation's diplomat would have
  done, turn by turn.

## Where things are

- `common/diplomacy/`: the diplomat. `WorldViewBuilder` builds what a
  nation knows (`WorldView`), `Situation` works out attacks and threats,
  `Diplomat.rank` scores every candidate `Action` (score = weighted
  gold, trade, land, security, relations, goals, opinion, minus risk;
  weights per `NationCharacter`). Thresholds: 20 for big moves, 5 for
  small ones. `Effects` holds what each move is worth. `Memory` and
  `Relations` give opinion; `Bearings`/`Sightings` guess where unseen
  nations live. Live memories: `Memories` (from `Snapshot` + `TurnDiff`,
  now here), trust from them. `Outlook` = view + situation + ranking,
  built once a turn by `EuropeanAIPlayer.getOutlook()`. `Commitments`
  (war/peace choices hold 10 turns), `Mood` (for the companion cards),
  `Offer` (peace terms, notice board, trade ships), `DealValue` (what
  a deal's items are worth to the diplomat).
- `server/ai/military/`: what the computer does with the diplomat's
  word: `InvasionPlanner` (attacks, also across the sea),
  `ScoutPlanner` (+ `mission/ScoutBearingMission`), `WarPreparations`
  (arm colonists, buy a ship). Called from
  `EuropeanAIPlayer.startWorking` and `MilitaryCoordinator`.
- `server/ai/diplomacy/OfferPlanner`: offers to the human player (peace
  by messenger, notices, trade voyages via
  `mission/TradeVoyageMission`), deliveries and deadlines, answers
  (`OfferMessage`), offers the player posts. War and peace between
  computer nations: `EuropeanAIPlayer.determineStances`.
- `server/ai/diplomacy/TribePlanner` (step 7): the tribes' diplomacy,
  run first in `NativeAIPlayer.startWorking`: they know each other,
  old rivals, memories (one shared `TurnRecord` a turn), wars between
  tribes, calls for help, allied settlements (`IndianSettlement.
  alliedPlayer`), tribe alliances, unrest and uprisings, Pontiac's
  rule, their own offer book (answered via `InGameController.
  answerOffer`). Pure rules in `common/diplomacy/TribeCharacter`,
  `TribeRelations`; `TribeMood` for the page; public news in
  `server/model/TribeNews`; refugees in `ServerPlayer.csRefugees`.
  Coalition buy-outs: `AlliancePlanner.buyOut`, priced by
  `Coalition.synergy`; joint strikes: `Coalition.jointTarget` /
  `strikeTurn`, kept by `InvasionPlanner`.
- `server/ai/diplomacy/GrievancePlanner` (step 6): every war a
  computer nation declares by its own choice (`determineStances`)
  goes complaint, ultimatum (`Offer.Kind.ULTIMATUM`), war; saved as
  `Grievance` in `EuropeanAIPlayer.getGrievances()`. Paying tribes to
  fight tribes: `AlliancePlanner.inciteTribes`,
  `InGameController.tribeIncitePrice`.
- Price agreements (step 4): `common/diplomacy/PriceAgreement` (the
  rules), `Offer.Kind.AGREEMENT` (running, in the buyer's offer book)
  and `SALE` (the "how many" dialog when a ship visits, answered with
  an amount via `OfferMessage`), handled in `OfferPlanner`
  (`considerAgreement`, `followAgreement`, `sell`).
- The player's envoys (step 8, Report > Send an Envoy): rules in
  `common/diplomacy/EnvoyRules` and `TribeEnvoyRules`, answered by
  `server/ai/diplomacy/EnvoyDesk` (nations, from `OfferPlanner.plan`)
  and `TribeEnvoyDesk` (tribes, from `TribePlanner.plan`); sent with
  `EnvoyMessage`; the dialog is `client/control/InGameController.
  sendEnvoy`. Land bought or taken from tribes: `server/model/LandClaims`.
- War of Spanish Succession (mod): `common/diplomacy/SuccessionRules`,
  `server/model/Succession`, `ServerGame.csSuccession` (game option
  `model.option.spanishSuccession`).
- Treasury: `common/diplomacy/Treasury`, `EuropeanAIPlayer.reserve()` /
  `spendable()`: shopping in Europe only spends gold above the reserve
  (deals may spend it).
- `tools/replay/`: replays a series of saves, compares the diplomat with
  what nations really did (uses `common/diplomacy/Snapshot`, `TurnDiff`).
- `client/companion/`: the companion web page (JSON from
  `CompanionNations`, `CompanionRelations`, `CompanionMoods`,
  `CompanionOffers`, ...; page in `data/companion/`). It is read only:
  the player answers and acts in the game. `CompanionArchive` keeps
  every turn (also read from old autosaves) for the long charts and the
  History tab. No emojis on the page: colours, meters and charts.

## Things that bit us

- `AIPlayer` subclasses read their XML inside the superclass
  constructor: a field with an initializer is set *after* reading and
  wipes what was read. Use lazy getters (see `EuropeanAIPlayer.bearings`).
- A new AI mission must be registered in `AIUnit.readChild` or it is
  lost on load.
- On the client, foreign units are only known while in sight: read the
  tiles in sight (`map.getTileList(me::canSee)`), not `p.getUnits()`.
- Map rows are half a tile high: halve y differences for distances and
  directions.
- `AIUnit.isAvailableForWork` lets the colony planner take muskets back
  from defenders; units the diplomat armed are kept armed
  (`EuropeanAIPlayer.isKeptArmed`).
