# Plan: living diplomacy and native tribes

Status: **designed, not built** (2026-10-13). Nothing in this file is
in the code yet. The working copy of this design is the Claude Doc
"FreeCol: living diplomacy design"; this file is the snapshot kept
with the mod, so the plan travels with the source.

## Why

The other European nations feel like scenery: wars start and stop
without a reason you can see, nobody fights for land, and nobody ever
comes to you with an offer. The goal is nations that want things,
remember what you did, and act on it: offer trades, sell you what they
cannot sell in Europe, make agreements, form alliances, and gang up on
whoever gets too strong, including you.

- "They often just declare war out of nowhere, then stop it. There is
  no real fight for territory."
- "Maybe it tries to sell me their boycotted goods, or trade with me at
  all."
- "The stronger you are compared to the rest, the more likely it is
  people will team up against you."

## What the game already has

Most of the pieces exist; what is missing is a computer that starts
anything. Checked in the FreeCol 1.2.0 source.

| Piece | What it does today | Use for us |
| --- | --- | --- |
| Diplomacy screen (`DiplomaticTrade`) | Swap gold, goods, colonies, units, and change stance between two nations, with counter-offers | The channel for every new offer, both ways |
| Stances | Uncontacted, peace, cease-fire, alliance, war | Alliances exist but nobody seeks them |
| Incite (`InciteTradeItem`) | Pay a nation to declare war on a third | The seed of coalitions |
| Tension | A number per pair of nations, 0 to 1100, raised by land taken, attacks, insults; cools by itself; above 1000 means war | Becomes one input of many, not the only one |
| AI accepting a trade | Accepts when the sum of item values is at least 0; never proposes (`EuropeanAIPlayer.acceptDiplomaticTrade`) | Needs to propose, with reasons |
| AI stance | Follows tension only (`AIPlayer.determineStance`) | Follows goals and a score |
| The King | Randomly declares war and peace for every colonial nation (Monarch `DECLARE_WAR` / `DECLARE_PEACE`) | Optional: only when tension is real |
| AI soldiers | Seek colonies within 8 moves, overland; never ship an army to another island | Needs a plan for landings |
| Bug | Enemy units in the open are never chosen as targets (`UnitSeekAndDestroyMission.scorePath` throws the score away) | Fix first |

## The idea: every nation is a player with a mind

Each computer nation gets a small "diplomat" that, once per turn,
looks at the world, updates what it thinks of each other nation,
checks its goals, lists what it could do, scores each option, and does
the best one if it scores high enough. Same loop for every nation,
different weights per character, so the French behave differently from
the Spanish.

The diplomat's turn:

1. Look at the world (honest view only).
2. Update opinion, trust and fear of every other nation from new events.
3. Check its goals: keep, drop or add.
4. List the possible actions, "do nothing" included.
5. Score each action.
6. Do the best one if it scores above the threshold.
7. Remember the answers it gets.

The answers to its moves become memories, so what you do this turn
changes what it scores next turn.

What the diplomat keeps per other nation:

- **Opinion** (−100 to +100): how much it likes you. Built from
  memories, not one number that cools by itself.
- **Memories**: dated events with a weight that fades over time: "sank
  our ship" (−30, fades over 20 turns), "honoured the trade deal"
  (+10), "refused our offer" (−3), "fought beside us" (+25).
- **Trust** (0 to 100): did you keep your agreements? Broken deals hit
  trust hard and slowly.
- **Fear** (0 to 100): how dangerous you look, from strength compared
  with them and how close your colonies are.
- **Usefulness**: what you can give it (goods it lacks, a common enemy,
  a market for its surplus).

What it knows is honest: the same information you get from the Foreign
Affairs report, the map, and its own contacts. No peeking.

## Goals

Each nation holds one long-term ambition for many turns and two or
three short-term goals that come and go. Goals decide which actions get
a bonus in the score; they also make behaviour readable ("Spain wants
Cuba").

**Long-term ambitions** (picked at the start from the nation's
character, re-chosen rarely, e.g. every 50 turns or after a big shock):

| Ambition | What it pushes for | Typical nation |
| --- | --- | --- |
| Trading empire | Rich trade, agreements, few wars, a big fleet of merchant ships | Dutch, English |
| Conqueror | Take colonies and land, wars it expects to win | Spanish |
| Balance keeper | Nobody grows too strong; joins coalitions against the leader | French |
| Settler | Many colonies, peace with neighbours, land deals with natives | English |
| Independence first | Bells, a strong army, avoid wars that weaken it | Any, late game |

**Short-term goals** (each turn the diplomat adds a goal when a trigger
fires and drops it when met or stale after about 10 turns):

| Short-term goal | Trigger | Actions it favours |
| --- | --- | --- |
| Sell a surplus | Goods piling up, or boycotted in Europe | Offer goods to a neighbour at a discount |
| Get a missing good | A colony short of tools, muskets, horses | Ask to buy, offer a fixed exchange deal |
| Take a colony | A weak enemy colony within reach, at war | Ship an army there, ask allies to join |
| Defend | Enemy army near its colony | Ask an ally for help, offer peace, buy muskets |
| Contain the leader | One nation far ahead in strength | Propose alliance to the others, incite war on the leader |
| Make peace | War going badly, or war weariness high | Offer cease-fire, pay gold, give back a colony |
| Revenge | A memory of a big loss caused by someone | Declare war when ready, incite others |

## The score function and weights

Every possible action gets a score: what it is expected to gain,
weighed by what this nation cares about, plus a bonus if it serves a
current goal, minus the risk. The best action above a threshold (start
with 20) is taken; at most one big move (war, alliance, peace) per
turn.

```
score(a) = Σ_k w_k · Δ_k(a) + goal(a) + 0.3 · opinion − ρ · risk(a)
```

- Δ gold, Δ trade, Δ land, Δ security, Δ relations: the expected
  change each action brings, in points (100 gold = 1 point; one colony
  = 40 points; one rival soldier fewer near home = 5 points).
- goal(a): +30 if the action serves a short-term goal, +15 if it serves
  the ambition.
- opinion: towards the nation the action concerns (−100 to +100), so
  friends get better offers.
- risk: chance it fails times what is lost, from the honest strength
  figures; ρ = how careful the character is.

**Weights per character** (the w and ρ above; starting values to tune):

| Character | Gold | Trade | Land | Security | Relations | Risk aversion ρ |
| --- | --- | --- | --- | --- | --- | --- |
| Trading empire | 1.0 | 1.5 | 0.3 | 0.8 | 1.0 | 1.2 |
| Conqueror | 0.6 | 0.4 | 1.5 | 0.6 | 0.4 | 0.6 |
| Balance keeper | 0.6 | 0.8 | 0.6 | 1.5 | 0.8 | 1.0 |
| Settler | 0.8 | 0.8 | 1.2 | 1.0 | 1.0 | 1.0 |

**Ganging up on the strongest.** Threat combines military and economic
share, so a nation that is rich but weak is not feared like one that is
rich and armed:

```
threat(X) = 0.6 · army_X / Σ army + 0.4 · output_X / Σ output
```

A nation considers joining a coalition against X when threat(X) is
above 0.35, more readily if it fears X and dislikes it, and only with
partners it trusts. (Open question: should wealth count for more than
40%?)

**Worked example, Mischa's game (Spring 1662):** army in soldiers'
worth Dutch 4, French 15, English 14, Spanish 29; output Dutch 418,
French ≈ 314, English ≈ 290, Spanish ≈ 290 gold a turn. Dutch threat =
0.6 × 4/62 + 0.4 × 418/1312 = 0.04 + 0.13 = 0.17: nobody gangs up on
you yet. Spain: 0.6 × 29/62 + 0.4 × 290/1312 = 0.28 + 0.09 = 0.37, just
over the line: the others would start looking for allies against Spain.

## New interactions

All of these go through the existing diplomacy screen, so you can
accept, refuse or counter; the computer can now start them too. Every
answer becomes a memory.

| Interaction | Who starts | What happens | Built from |
| --- | --- | --- | --- |
| Goods offer | They (surplus or boycott) | "We have 200 furs we cannot sell in Europe: 400 gold?" Delivered at their nearest colony port or to your ship | Goods + gold items |
| Purchase request | They (short of a good) | "We need 100 tools, we pay 600 gold" | Goods + gold items |
| Trade agreement | Either | A fixed exchange for a number of turns: 50 coats a turn for 300 gold, or 100 furs for 50 tools. Settled each turn from a named colony; a missed delivery is a broken deal | New agreement item + a per-turn settlement |
| Defensive pact | Either | Each joins the war if the other is attacked; refusing to join breaks the pact | Alliance stance + a promise |
| Joint war | Either | "Join us against France: 1000 gold and Port-Royal after the war" | Incite + colony item |
| Coalition | Balance keepers | Two or three nations ally against the strongest; you may be invited, or be the target | Several alliances + one target |
| Grievance and ultimatum | They | No war out of nowhere: first a complaint ("your soldiers near our border"), then terms ("withdraw or pay 500"), then war if ignored | New message types + memories |
| Peace terms | The side losing | Cease-fire for gold, a colony back, or a trade deal | Existing peace items |
| Gift and thanks | Either | Small gifts raise opinion; help in a war is remembered for long | Gold item + memory |

On islands, war only means something if armies cross the sea: a "Take a
colony" goal therefore plans a landing (gather soldiers and artillery at
a port, load a ship, sail, land next to the target, attack), and the
defender's allies can intercept the fleet.

## How you see it

You can read why a nation acts, in words, not numbers, as with the
moods in Relations.

- **Nation cards on the companion page** get a mood line ("France: wary
  of you, wants tools, angry at Spain"), their ambition ("trading
  empire"), and open offers and deals.
- **Memories you can hover**: "Remembers: you sank the Saint-Louis
  (1660), you honoured the furs deal (1661)".
- **A Diplomacy widget**: deals running (what, until when, delivered on
  time or not), alliances and coalitions as a small graph of who stands
  with whom.
- **Offers arrive as a message** at the start of your turn, opening the
  diplomacy screen; routine offers can go to the companion log instead.
- **Warnings before war**: a grievance shows on their card first, so a
  war never comes out of nowhere.

## Build plan

Each step is playable on its own and gets unit tests; the diplomat's
decisions are logged so we can see why it did something.

0. **Replay your saves** (see below), so every later step is measured
   the same way.
1. **Fix the targeting bug** and let wars be fought: soldiers may hunt
   enemy units, and a "Take a colony" goal can ship an army to another
   island. Small to medium.
2. **Opinion, memories, trust, fear** per nation pair, from game
   events, shown on the companion cards. Medium.
3. **The score loop and goals**, with only trade actions at first:
   goods offers, purchase requests, boycott sales. Medium.
4. **Trade agreements** with per-turn settlement and broken-deal
   memories. Medium.
5. **Alliances, joint wars and coalitions**, using the threat formula;
   invitations to you. Large.
6. **Grievances and ultimatums before war**; an option so the King's
   random wars only happen with real tension. Medium.
7. **Native tribes** (section below). Large.
8. **Tuning**: run long computer-only games and check that wars have
   causes, coalitions form against runaway leaders, and nobody spams
   offers.

## Decisions (2026-10-13)

| Question | Decision |
| --- | --- |
| How often may a nation make you an offer? | At most one per nation per 5 turns, routine ones to the companion log. Nations stay committed (see Commitment). |
| Deals between computer nations too? | Yes. |
| Switch to turn the system off? | No. |
| Should the AI know more on Hard? | Open. Today Hard does not make it smarter or better informed; it gets free stuff: each turn a chance of a lifted boycott, gold for a scout or pioneer, a free land unit, a free warship (Very easy 0%, Easy 5%, Medium 10%, Hard 15%, Very hard 20% per item; `EuropeanAIPlayer.cheat`). Proposal: keep that, keep the diplomat honest on every level, and let harder levels make it more ruthless (coalitions against you form sooner, grudges last longer). |
| A native settlement that joins you becomes? | An allied native settlement: it stays native, and you trade with each other cheaply. |

**Commitment.** A nation sticks to what it chose:

- A short-term goal is kept for at least 10 turns and the ambition for
  at least 50, unless a shock hits: war declared on it, a colony lost,
  an ally betrays it.
- A new goal must beat the current one by at least 30% to replace it,
  so small swings in the score do not flip its behaviour.
- An offer stays on the table with the same terms for 5 turns; a
  running deal is honoured to its end unless the other side breaks it.
- Breaking its own deal costs the nation trust with everyone who hears
  of it, so it rarely does.

## Step 0: replay and compare, from your saves

Mischa's game has an autosave for every turn: 244 of them
(`Autosave-6c7a41f5_Dutch_*`), from 1493 to Spring 1669. Step 0 replays
them in pairs, from copies in the sandbox. The game there saves into its
own folder, so his saves are never written to.

1. Read turn N and turn N+1 side by side (no game running, just the
   two files).
2. List what changed for every nation: stances, war and peace (and
   whether the King declared it), colonies founded, taken or grown,
   armies moved, fights, goods sold in Europe, gold, dealings with
   natives.
3. Split the changes into what the player did and what each computer
   nation did. A war the King forced is marked as forced, not as a
   choice.
4. Run the diplomat on turn N for every nation, the player included,
   with only what that nation could know. It lists its options with
   scores, "do nothing" included.
5. Compare. Was the player's move in its top 3? Did it want something
   the player never did? For computer nations: how does its choice
   differ from what today's AI did?
6. Judge with hindsight, using all the data from turn N+1 onward (we
   may look at everything here, because we are judging, not playing):
   did the war pay off, would the attack it wanted have stood a chance?

The result is one row per turn: the player's move, the diplomat's top 3
with scores, agree or not, and why. Then tune the weights so it agrees
more where the player's moves worked out, and less where they did not.

Honest limits: a few dozen diplomatic or military choices in 244 turns;
most turns are economy. Enough to check that the scores make sense and
to set rough weights, not to fit them precisely. The player's style is
one character (closest to trading empire), so the other characters
still need the forward test.

**Forward test.** Then load a save, let the computer play every nation
for 100 turns headless, and check:

- Every war has a cause in the log (a grievance, a goal, a coalition).
- Stance changes per nation pair: at most one per 20 turns on average.
- Offers: about one per nation per 5 turns, none repeated with new
  terms before they expire.
- When one nation grows far ahead (e.g. Spain's army in the 1662 save),
  a coalition forms against it within about 20 turns.

## Native tribes

- "They are very boring at the moment. In my latest save the tribes are
  so isolated they hardly play into the game."
- "Certain settlements might break off to join a European nation, or
  perhaps the entire tribe (influenced by the missionaries)."
- "If one tribe is about to die against the English, maybe other tribes
  come to its aid."

**What the game does today** (checked in the source): a tribe's stance
towards everyone follows only its own tension (`NativeAIPlayer
.determineStances`). Tribes never help or coordinate with each other. A
missionary can pay a settlement to attack a European (incite). Converts
leave one at a time for your nearest colony
(`ServerIndianSettlement`). Burning a capital makes the whole tribe
surrender.

**Historical precedent.** Tribes did ally, often, but rarely all
together. Old rivalries decided sides as much as the Europeans did, and
Europeans used them. Remnants of beaten tribes were usually taken in by
neighbours.

| Event | What happened | Idea for the game |
| --- | --- | --- |
| Tuscarora join the Iroquois (c. 1722) | Beaten by Carolina in 1711–15, they moved north and were taken in as the sixth nation of the Confederacy | A dying tribe's people move to a friendly tribe |
| Pueblo Revolt (1680) | About two dozen pueblos, speaking different languages, rose together under Popé and drove the Spanish out of New Mexico for 12 years | Several tribes rise together against the most hated European, after warnings |
| King Philip's War (1675–76) | The Wampanoag were joined by the Nipmuck and Narragansett, but the Mohegan and Pequot fought for the English and the Mohawk attacked Metacom | Alliances split along old rivalries: never everyone |
| Pontiac's War (1763) | Odawa, Ojibwe, Potawatomi, Huron, Delaware, Shawnee and Seneca took British forts after France lost Canada | When a tribe's European friend loses, it turns on the winner |
| Tecumseh's confederacy (1805–13) | Many tribes united and fought alongside Britain in 1812 | A tribal alliance can ally with a European nation |
| Tlaxcala (1519–21) | A whole people joined Cortés against their enemies, the Aztecs | A whole tribe joins a European against a rival tribe |
| Kahnawake (from 1667) | Catholic Mohawk converts left their towns, settled near Montreal and fought for the French | A mission settlement breaks away and joins a European nation |
| Praying towns (Natick 1651, 14 towns by 1674) | Christian Algonquian towns under English rule; in 1675 they were distrusted and interned on Deer Island | Mission settlements become yours, but others may resent it |
| Jesuit missions in Paraguay (1609–1767) | Guaraní mission towns with their own militia beat slave raiders at Mbororé (1641) | A mission settlement defends itself and becomes a buffer |
| Yamasee War (1715–17) | Many tribes fought South Carolina; the Cherokee switched sides and joined the colonists against the Creek | A coalition can be broken by a good offer |

**Ideas** (the same diplomat with a native character, honest view too):

- **Tribes have opinions of each other:** neighbours, an old rival
  picked at the start, a shared enemy.
- **Call for help:** when a tribe loses a settlement or its capital is
  threatened, friendly tribes send braves or declare war on the
  attacker, more readily if they fear that European too.
- **Refugees:** when a settlement is destroyed, its survivors move to
  the nearest friendly settlement instead of vanishing.
- **A settlement breaks off:** long happiness with your mission, or
  abandoned by a tribe that lost to you, and it asks to join you. It
  becomes an **allied native settlement** (decided): it stays native,
  and you trade with each other cheaply.
- **A whole tribe joins:** when most of its settlements have your
  missions and are happy, the chief offers alliance and protection: its
  braves fight with you, you defend them.
- **Uprising:** when several tribes hate the same European, they rise
  together, with warnings first.
- **Readable:** tribe cards on the companion page ("the Cherokee would
  come to the Iroquois' aid").
- **Replay from the saves too:** how each tribe's tension moved over
  244 turns, and how often a tribe was close to dying with nobody coming
  to help.
