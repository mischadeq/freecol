# Steps 5 and 7: alliances that mean something, and living tribes

Written 2026-10-14, after step 3 (offers, notice board, trade ships).
Mischa wants these two most for his games; grievances (step 6) come
after, trade agreements (step 4) last. Read `CLAUDE.md` and the design
in `ai-diplomacy-and-natives.md` first.

Each round ends with tests, an `ideas.md` entry and a commit (see
`CLAUDE.md`). Questions for Mischa are at the end; each has a
proposal, so work can start before he answers.

## Where things stand (checked in the source)

- **An alliance means almost nothing.** `Stance.ALLIANCE` only stops
  units from evicting an ally from colony land
  (`InGameController`, around line 1141) and missions from attacking
  allies (`Mission`, around line 339). Allies never join each other's
  wars, and nothing ends an alliance except a new stance.
- The diplomat already scores `ALLIANCE` (shared enemy: security; the
  ally's enemies drag us in: minus) and `INCITE` (pay a nation to fight
  another). `Threat.leaderToContain` finds a runaway leader (threat at
  least 0.35: 60% army share, 40% output share) and the goal
  `CONTAIN_LEADER` gives +30 to moves against it. Nobody acts on
  INCITE or on coalitions yet.
- Between computer nations, alliances already come from the diplomat
  (step 3a, both sides must want it, choices hold 10 turns).
- **Tribes have no stance towards each other.** `ServerPlayer.csContact`
  ignores native-to-native contact, so tribes never have a war or an
  alliance with each other. (They should simply know each other from
  the start: see 7a.)
- A tribe's stance towards a European follows only its own tension
  (`NativeAIPlayer.determineStances`). Braves defend, wander, bring
  gifts and make demands (`NativeAIPlayer.startWorking`). Each
  settlement has its own alarm per European and a most-hated nation.
- Converts leave one at a time for the nearest colony
  (`ServerIndianSettlement.csStartTurn`, convert progress). Burning a
  capital makes the whole tribe surrender (`ServerPlayer`, around line
  2650). A destroyed settlement's people simply vanish.
- The diplomat already has a `TRIBE` character, sees tribes from the
  European side (`WorldViewBuilder.tribe`), and Europeans can arm a
  tribe against a rival (`ARM_TRIBE`).
- Offers to the player go through `OfferMessage` (a dialog at the
  start of the turn) and the turn report; the companion page only
  shows.

## Step 5: alliances, joint wars and coalitions

### 5a. Alliances are defensive pacts

- **A call to arms.** When war is declared on a nation (not by the
  King, see question 2), each of its allies gets a call to arms on its
  next turn: join the war against the attacker, or refuse.
  - A computer ally decides with its diplomat: new move `JOIN_WAR`
    (ally, enemy), scored like declaring war on the enemy plus keeping
    its word (trust and relations with the ally) minus the cost.
    Refusing is the risky choice for a nation that values relations.
  - The player gets the call as an offer dialog ("France calls on you:
    Spain has declared war on them. Join the war?").
  - Refusing ends the alliance (back to peace) and the ally remembers
    `DEAL_BROKEN` ("left us alone"). Joining is remembered as
    `HELPED_IN_WAR` (exists) and keeps the pact.
- **Allies fight together.**
  - `InvasionPlanner` may target colonies of the ally's enemy when we
    joined that war.
  - ~~A `DefendAllyMission` (soldiers standing in an ally's colony).~~
    **Never** (Mischa, 2026-10-14): no units in another nation's
    colony, not even an ally's.
- **Allies share what they see** in the honest view: an ally's
  sightings feed our `Bearings`, and an ally's explored colonies count
  as known. A small, cheap change with a big effect on war plans.
- **Ending an alliance.** Declaring war on an ally, or making a
  separate peace with the common enemy without asking, is betrayal:
  `DEAL_BROKEN` with the ally and a trust loss with everyone who sees it
  (alliances are public).
- **The player** can be invited to an alliance by a computer nation
  (offer by messenger, when its diplomat scores `ALLIANCE` with him at
  20 or more), and can propose one through the game's diplomacy screen
  (already judged by `DealValue`).
- Saved: the pacts are the game's own stances; open calls to arms are
  offers in the computer player's book (new kind `CALL_TO_ARMS`).

### 5b. Joint wars

- "Join us against France: 1000 gold now." The diplomat's `INCITE`
  becomes a real offer: to a computer nation (decided by its own
  diplomat: declaring war on the target plus the gold) or to the player
  (offer dialog).
- **A colony as a prize** (question 3): "and Port-Royal after the war".
  The promise is kept in the offer: if the inviter takes Port-Royal it
  hands it over at peace; if the partner takes it, the inviter does not
  ask for it. Kept: `DEAL_KEPT`; not kept: `DEAL_BROKEN`. First round:
  gold only; the colony prize in a second round.
- Partners do not make a separate peace with the target: the same
  betrayal rule as 5a.
- Accepting declares the war at once (the player agreed to it).

### 5c. Coalitions

- **Forming.** When a nation's threat passes the coalition line (0.35,
  see question 4), a nation with `CONTAIN_LEADER` (balance keepers
  first) invites others who fear the leader or dislike it: an alliance
  with a named target. A coalition exists from two members. The player
  is invited when he is not the target and not disliked.
- **Acting.** Members are allied (5a pacts). They go to war together
  when the target attacks any member; they attack together when their
  combined army is at least 1.2 times the target's and a member's
  diplomat scores the war at 20 or more (conquerors and balance keepers
  first).
- **Ending.** When the target's threat stays under 0.25 for 10 turns,
  or a member is bought out (a joint-war offer from the target that
  scores better), the Yamasee War rule.
- **Against the player.** It is public: the turn report says "France
  and Spain have formed a coalition against you", and the cards show
  it. This is one of the "more ruthless on harder levels" knobs.
- Saved: a small `Coalitions` record per computer player (target,
  members, since).

### 5d. Seen on the companion page

- **A relations graph** (Nations tab): nations as dots in their colour,
  lines for alliances (blue) and wars (red), a coalition as a shaded
  ring around its members with the target named. Only public stances.
  Tribes join the same graph in step 7.
- **Cards:** "Allied with France since 1650 (defensive pact)", "In a
  coalition against England", open calls to arms and joint-war offers
  in the Offers list.

### 5e. Tests and done

- Unit: a call to arms is answered by the diplomat; refusing ends the
  pact and is remembered; joining declares the war; betrayal costs
  trust; joint-war gold moves on acceptance; a coalition forms only
  above the line with at least two members, acts together, and
  dissolves under 0.25 for 10 turns; shared sightings reach the ally's
  bearings; calls to arms, offers and coalitions survive save and load.
- In a game: when someone attacks a computer nation, its allies join
  (or visibly refuse and the alliance breaks); a nation that runs away
  with the game faces a coalition; the player gets the occasional
  invitation and call to arms.

## Step 7: native tribes

The same diplomat, with a native character and an honest view, plus
the events from history in the design ("Native tribes").

### 7a. Tribes think and know each other

- **Tribes know each other from the start** (decided with Mischa,
  2026-10-14): they have lived there since forever, so every tribe
  knows every other tribe and all their settlements from turn one; no
  European-style first contact. At game start (and on loading an older
  save) every pair of tribes is set to peace, and each tribe's honest
  view includes all native settlements. Their stances towards each
  other can then change (war, alliance). The native-to-native exception
  in `csContact` stays: there is nothing to meet.
- **The diplomat for tribes.** `NativeAIPlayer` builds an `Outlook`
  each turn (honest view: its settlements, what its braves and lands
  see, its own alarm and tension). Characters per tribe from the
  nation type (question 6): warlike (more war, less trade), peaceful
  (more trade and gifts), traders.
- **Opinions between tribes:** neighbours rub (shared borders, a
  little minus over time), an old rival picked once at the start
  (nearest tribe, by the game's random), a shared enemy brings them
  together (both hate the same European: plus).
- **Memories for tribes:** a settlement destroyed, land taken by a
  colony, a mission set up or thrown out, arms sold to them or to their
  rival, gifts given, demands met or refused, help in a war. The same
  `Memories`, kept and saved by the native AI player.
- **Wars between tribes** stay rare: only old rivals with a grudge, or
  when a European pays one tribe to fight another (`ARM_TRIBE`,
  `INCITE`). Braves then raid the rival's settlements.

### 7b. Calls for help

- When a tribe loses a settlement, or enemy soldiers stand next to its
  capital, it calls on the tribes that like it. Each decides with its
  diplomat: opinion of the caller, fear of that European, distance, old
  rivalries. Never everyone: an old rival may even side with the
  European (King Philip's War).
- Helping: declare war on the attacker and send a war party (new
  `NativeWarPartyMission`: go to the threatened settlement, attack the
  attacker's units near it, then raid its nearest colony).
- Turn report: "The Cherokee come to the aid of the Iroquois."

### 7c. Refugees

- A destroyed settlement's people do not vanish: half of its units (at
  least one) move to the nearest settlement of their own tribe, or of a
  friendly tribe if none is left (the Tuscarora joining the Iroquois).
  A tribe that is wiped out leaves its survivors to its best friend.
  Server change where a settlement is destroyed (`ServerPlayer`).

### 7d. A settlement breaks off and joins you

- Conditions: your mission there for at least 20 turns, the settlement
  happy with you, and its own tribe weak or angry with it (it lost a
  war to you, its capital surrendered, or the tribe is at war with you
  while this settlement is not). The settlement asks (offer dialog).
- It becomes an **allied native settlement** (decided earlier): it
  stays native, flagged as allied to you (saved on the settlement):
  - it does not join its tribe's wars against you,
  - its braves help defend your colonies nearby,
  - you trade with it cheaply (no alarm on prices),
  - its own tribe and neighbours resent it (praying towns): a minus in
    their opinion of you.
- The tribe may try to take it back (a raid), which is a call to arms
  for you.

### 7e. A whole tribe joins you

- When at least two thirds of its settlements have your missions and
  are happy, and the tribe likes you, its chief offers alliance and
  protection (offer dialog). It becomes your ally under the 5a pact:
  its braves join your wars (call to arms), and you must answer its
  calls for help, or it leaves the alliance and remembers.
- Tlaxcala: a tribe at war with a rival tribe may join a European
  against that rival.

### 7f. Uprisings

- When several tribes hate the same European (high tension, memories
  of destroyed settlements and land taken), they rise together, never
  out of nowhere:
  - **Unrest** first: the turn report says "The tribes speak of war
    against you"; the tribe cards show an unrest meter. Gifts, giving
    back land or withdrawing missions calm it.
  - **Uprising** if unrest lasts (question 8: 5 turns): every tribe in
    it declares war the same turn and sends war parties at the nearest
    colonies. Old rivals stay out.
- **Pontiac's rule:** when a tribe's European friend loses badly
  (its colonies near the tribe taken by another European), the tribe
  turns on the winner.

### 7g. Seen on the companion page

- **Tribe cards** (Nations tab, after the European cards): mood
  towards you (the same chips), unrest meter, settlements and your
  missions, what they remember about you, friends and rivals among the
  tribes.
- **Honest:** a tribe's feelings about other tribes show only when
  public (war or alliance) or when you have a mission in that tribe:
  your missionaries tell you what it thinks.
- Tribes in the relations graph from 5d.

### 7h. Tests and done

- Unit: every tribe knows every other tribe and its settlements from
  the start (also after loading an older save); opinions between tribes
  (neighbours, rival, shared enemy); a call for help is answered by
  friends and not by rivals; refugees move to the right settlement;
  a settlement breaks off only under the conditions and stays native;
  a whole tribe joins and answers calls to arms; unrest comes before an
  uprising and rivals stay out; everything survives save and load.
- In a game: tribes stop being "isolated and boring": burning a
  settlement brings its friends; a long mission can win a settlement
  or a tribe; abuse brings an uprising, with a warning first.

## Order of the rounds

5a pacts and calls to arms; 5b joint wars (gold); 5c coalitions; 5d
graph and cards; then 7a tribes meet, think and remember; 7b calls for
help; 7c refugees; 7d a settlement joins; 7e a tribe joins; 7f
uprisings; 7g tribe cards. 5b's colony prize can come after 7 if time
is short.

## Decisions (Mischa, 2026-10-14)

1. **Call to arms to the player:** a dialog he answers (as in 5a).
2. **The King's wars do not call allies.** That is the interesting
   part: local allies against global enemies. When a King declares war
   on a nation that is allied in the New World (to us, or between two
   computer nations), each side chooses what to do:
   - the stance becomes war (the King's doing), but it is not counted
     as betrayal (no `DEAL_BROKEN`);
   - a loyal ally (long alliance, high trust, kept deals) keeps a
     **local truce**: it does not attack the ally's colonies or units
     and offers peace as soon as it can; its card says "at war by order
     of the King, keeps the peace here";
   - a disloyal one takes the chance and attacks; that is remembered
     as betrayal;
   - the player chooses by how he plays; attacking a former ally during
     a King's war counts the same way for the ally's memory.
   Add to 5a; test both a loyal and a disloyal ally.
3. **Colonies as prizes: yes, only conquered ones** (a division of the
   spoils): "join us against France, and the French colonies you take
   are yours; the ones we take we keep", or a named colony handed over
   when the inviter takes it. No promises of colonies that are not
   taken in that war. Second round of 5b.
4. **Coalition line by difficulty:** 0.40 (very easy), 0.38, 0.35
   (medium), 0.32, 0.30 (very hard).
5. **Allied native settlement: subservient.** It stays native, is
   treated as second class but under your protection: you use its land
   for free, trade with it cheaply, its braves help defend your
   colonies nearby; its tribe and neighbours resent it.
6. **Tribe characters:** warlike: Apache, Sioux, Aztec, Inca; peaceful:
   Arawak; traders: Tupi; the others (Cherokee, Iroquois) middling.
7. **Refugees:** half the settlement's units.
8. **Uprisings** (asked what they are): several tribes rising together
   against one European nation (the player or a computer nation), like
   the Pueblo Revolt of 1680. **In** (Mischa): 5 turns of unrest with
   warnings first, shorter on harder levels.

## After 5a-5c (Mischa, 2026-10-14)

- **5d, the relations graph:** yes, after step 7 (so the tribes are in
  it too).
- **Soldiers in an ally's colony:** never.
- **A coalition plans its attacks together:** yes, a later round:
  members pick the same target colony and time their landings, instead
  of each attacking on its own.
- **The target buying a member out** (asked what it means): the nation
  a coalition stands against offers one member gold, or a colony, to
  leave the coalition and stay out of the war (the Cherokee changing
  sides in the Yamasee War). Waiting for Mischa's answer.

## Not in these steps

Grievances and ultimatums before a European war on you (step 6),
trade agreements over many turns (step 4), long computer-only tuning
games (step 8), counter-offers in the offer dialog.
