# Breakaway tribes: mission villages leave their tribe and found a new one

Status: plan (2026-10-27), not built. Builds on step 7d (a settlement
breaks off and joins you) and 7e (a whole tribe joins you) in
`step5-step7.md`.

## Why

Mischa is at war with the Sioux (since turn 216) and has had missions in
four Sioux villages for about 100 turns (Pahin, Wakpala Sapa, NiN S'ude,
Mnisota). Those villages were perfectly happy with him in peace (alarm 0)
but never broke off: in peace the tribe isn't angry, and in war every
village's alarm jumps to the maximum (1000) the moment war starts. The
7d rule ("happy, while the tribe is at war with you") can almost never
fire.

He wants converted villages to be able to break off **during** the war,
and when several of them go together, to form **a real new tribe**:
named by him, allied to him, at war with the tribe it left.

History has this: the Catholic Mohawks who left the Iroquois for the
Kahnawake mission near Montreal (1667 on) and fought beside the French;
the Seminole, formed from Creek bands that split away.

## What the player sees

1. A village with your mission stays calmer when its tribe goes to war
   with you.
2. A few turns into the war, a message: **"Pahin, Wakpala Sapa and
   NiN S'ude want to leave the Sioux and live as a people of their own,
   under your protection."** Accept or decline.
3. On accepting, a dialog asks for **the new tribe's name**, with a
   suggestion ("Praying Sioux"). You can type anything.
4. The three villages change colour on the map and become the new
   tribe: their braves, their land, their goods go with them. Your
   missions stay.
5. The new tribe is **your ally** (the 5a pact: it joins your wars, you
   answer its calls) and **at war with the Sioux**.
6. It has its own card on the companion page (Tribes), in Foreign
   Affairs and in the Log: "The Praying Sioux broke away from the
   Sioux (3 villages), allied to the Dutch."
7. Later, another Sioux village with your mission that qualifies is
   asked to **join** the new tribe instead of becoming a lone allied
   village.

One village alone still becomes an allied village (7d), as now. It is
folded into the new tribe if one forms later.

## Rules

### A. The trigger (fix to 7d)

- **Half the war shock.** When a tribe goes to war with a European, its
  villages with that European's mission take **half** the war's alarm
  (+500 instead of +1000). Other villages are unchanged.
- **Content is enough in war.** A village may break off when:
  - your mission has been there **20+ turns** (as now),
  - its tribe is weak or angry with you (as now; being at war counts),
  - its alarm with you is **happy**, or, **while its tribe is at war
    with you, content or better** (600 or less; now: 100 or less).
- In Mischa's turn-221 save (alarm 858-883, falling about 45 a turn),
  the four villages qualify in **6-7 turns** with the new rule alone,
  without the half shock (that only helps in new wars).

### B. Forming the tribe

- **When:** 2 or more villages of the same tribe qualify for the same
  European within 3 turns of each other, or 1 qualifies while that
  tribe already has a village allied to the same European. Those
  villages are offered together (one offer).
- **For the player:** an offer (`Offer.Kind.BREAKAWAY`) with the village
  names; accepting opens the naming dialog; declining leaves them as
  they are (asked again after 10 turns, as other offers).
- **For computer nations:** the same, accepted automatically, named
  "<tribe> of <first village>" (e.g. "Sioux of Pahin"). (See open
  question 1.)
- **At most 3 breakaway tribes per game** (three spare nation slots,
  see below). With none left, the villages become allied villages as
  now.

### C. The new tribe

- **Look:** the parent's: same villages, braves, skills, character
  (`TribeCharacter`, so Sioux breakaways are warlike), own colour.
- **Relations at birth:**
  - with you: **alliance** (5a pact), tension 0;
  - with the parent: **war**; each is the other's old rival;
  - with everyone else: the parent's stance (peace, or war with whom
    the parent is at war, except you);
  - it knows everyone the parent knew (contact) and has the parent's
    map knowledge at that moment (honest view: no more).
- **Memories:** starts fresh, plus "you protected us" (a strong TREATY
  memory of you).
- **Villages:** alarm with you set to happy; the missionaries stay;
  the parent's memories and alarm stay with the parent.
- **The parent's side:** a one-time anger at you (as when a village
  breaks off), and wants its villages back: raids on them count as
  calls to arms for you (the 5a pact already covers that).
- **Its own diplomacy:** it runs `TribePlanner` like any tribe: it can
  make peace with the parent after 10+ turns, call you to its wars,
  leave you if you ignore its calls (5a). Should it be able to rejoin
  the parent? (Open question 3.)
- **When the war ends between you and the parent:** the breakaway
  stays at war with the parent until it makes its own peace (it is its
  own war now).
- Dies like any tribe when its last village is gone.

## How (technical)

### Trigger

- `ServerPlayer.csModifyTension`: where tension is spread to the
  tribe's villages, a war-sized change (`>= Tension.WAR_MODIFIER`) is
  halved for villages whose missionary belongs to that player.
- `TribePlanner.breaksOffTo`: content-or-better (`<= Tension.Level.
  CONTENT`) while `me.getStance(p) == WAR`.

### Spare nations

- Three new native nations in the classic spec:
  `model.nation.breakaway1..3` (own colours, not used by others), a
  placeholder `nation-type` (a native type).
- **Never at game start:** `NationOptions` marks them `NOT_AVAILABLE`
  (today every non-European nation is `AI_ONLY`, which would put them
  on the map). Check the map generator and the start-game panel too.
- Messages: a default name and ruler for each slot; resources: the
  common native coat of arms; a generic chief picture.
- Old saves: the spec is not stored in the save, so the new nations
  simply exist when an old game loads.

### Creating the player

- Like the King's army (`InGameController.createREFPlayer`):
  `FreeColServer.makeAIPlayer(nation)`, then
  `ServerGame.updatePlayers` sends the new player to every client.
- Set `nationType` to the parent's (saved per player already).
- The name: reuse `independentNationName` (saved already; rebels use
  it) and let `Player.getNationLabel` use it for a native player that
  has one; also set the player name. Store "broke away from Sioux, under
  the Dutch" on the new tribe's AI (`NativeAIPlayer`: `breakawayFrom`,
  `protector`), lazy getters (see "Things that bit us").
- Contact (`Player.makeContact`), stances (`csChangeStance`), tension,
  explored tiles copied from the parent.

### Moving the villages

- `Settlement.changeOwner` already allows native to native. A new
  `ServerIndianSettlement.csChangeOwner`, after `ServerColony.
  csChangeOwner`: the tiles (`changeOwnership`), the braves living there
  and out on the land (`getOwnedUnits`, `ServerPlayer.csChangeOwner`
  per unit), the goods stay, alarm reset, `alliedPlayer` cleared (the
  whole tribe is allied now), visibility and tile updates for everyone
  who sees them.
- `TribeNews`: new kind `BROKE_AWAY`; `NationNews` line for the cards.

### The offer and the dialog

- `Offer.Kind.BREAKAWAY` (several villages), answered through
  `InGameController.answerOffer` as `SETTLEMENT_JOINS` is today; the
  client shows a confirm dialog, then a name dialog (like naming a new
  region), and sends the name with the answer (`OfferMessage`).
- `Offer.Kind.JOIN_BREAKAWAY` for later villages joining.

### Companion page

- The new tribe appears in Tribes on its own (`getLiveNativePlayerList`)
  with its name; its card says "broke away from the Sioux, turn 228,
  under your protection". Log news line. Check `CompanionGraph` (tribe
  relations graph) and the History tab with a player that appears
  mid-game.

## Steps

1. **Trigger** (small, one commit): half shock, content in war. Tests.
   Mischa can try it on his save right away: allied villages in 6-7
   turns.
2. **The new tribe** (big): spare nations, kept out of new games;
   creating the player; moving villages and braves; stances; name
   dialog; offer; save and load. Tests, and a run on a copy of
   Mischa's 1656 save (server side, in a test) to see the split work on
   a real map.
3. **Living with it:** later villages joining, the parent wanting them
   back, calls to arms, companion card, news, Foreign Affairs, computer
   nations doing the same.

## Tests

- War with a mission village: its alarm rises by half; others by all.
- `breaksOffTo` in war: content is enough; in peace: happy needed.
- Two qualifying villages: one BREAKAWAY offer, not two SETTLEMENT_JOINS.
- Accepting: one more live native player, named as typed; the villages,
  their braves and tiles owned by it; nation type the parent's;
  alliance with the protector, war with the parent; the missionaries
  still the protector's.
- Save and load: the name, nation type, `breakawayFrom` and `protector`
  survive.
- A new game never places a breakaway nation.
- A fourth breakaway with all three slots used: allied villages instead.
- Honest view: the new tribe's map knowledge is the parent's, no more.

## Risks

- **A player appearing mid-game** is rare in FreeCol (only the King's
  army). Clients, the companion archive and reports that keep per-player
  lists may assume a fixed set. Check: Foreign Affairs report, companion
  charts and History, `CompanionArchive` from old autosaves.
- **Moving settlement units** between natives isn't done anywhere yet;
  braves on the move (war parties, traders) must not be lost or left
  with a dead home.
- The AI parent tribe must not be left with plans pointing at villages
  it no longer owns (missions, `TribePlanner` targets).

## Open questions (my suggestion in brackets)

1. **Computer nations too?** A French mission could create "Sioux of
   Pahin" allied to France. [Yes, in step 3, named automatically.]
2. **Naming:** free text, or pick from suggestions? [Free text with a
   suggestion filled in.]
3. **Rejoining:** may the breakaway go back to its parent? [Yes, if you
   leave it: no missions of yours left in it and two calls for help
   ignored; then it rejoins with all its villages.]
4. **The breakaway's colour:** fixed per slot, or pick one? [Fixed.]
5. **Three slots** enough? [Yes; more is only more spec entries.]
