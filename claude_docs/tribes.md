# Native tribes: trading, skills, temper, and how they live in the mod

A guide to the eight native nations in FreeCol 1.2 (the standard
"FreeCol" and "classic" rules), and to what our mod changes. Everything
here comes from this repository: the tribe data from
`data/rules/classic/specification.xml`, the trading rules from
`IndianSettlement.java` and `NativeAIPlayer.java`, the mod's tribe
diplomacy from `TribePlanner`, `TribeRelations`, `TribeCharacter`,
`TribeEnvoyRules` and `LandClaims`. So it describes what the game
really does, not folklore.

Last updated: 2026-10-17. Parts marked **(mod)** are ours; the rest is
the base game.

Where to see it in the game and on the companion page:
- **Relations drawer (R):** every tribe you have met, the mood of each
  settlement, what it teaches and wants once you have visited. Click a
  goods icon for its Colopedia entry.
- **Diplomacy tab (mod):** a card per tribe (character, mood, trust,
  unrest, what they remember of you, rival and friends, allied
  settlements, news) and the relations graph.
- **Nations tab and Military tab (mod):** a strength card per tribe
  (settlements, about how many braves, army in soldiers' worth) next to
  the European nations, and the tribes in the army bars.
- **Report > Send an Envoy (mod):** peace, a gift, or asking a tribe to
  fight for you.

## The eight tribes at a glance

| Tribe | Settlements | How many | Temper (game) | Character (mod) | Where on the map | Richness (trade bonus) |
|---|---|---|---|---|---|---|
| Apache | camps (4-6 people) | average | **aggressive** | warlike | centre | low (1, capital 2) |
| Sioux | camps (4-6) | average | average | warlike | north-west | low (1, capital 2) |
| Tupi | camps (4-6) | **many** | **peaceful** | traders | south-east, south | low (1, capital 2) |
| Arawak | villages (6-8) | few | **aggressive** | peaceful | east | medium (2, capital 3) |
| Cherokee | villages (6-8) | average | **peaceful** | proud | north | medium (2, capital 3) |
| Iroquois | villages (6-8) | average | average | proud | north-east | medium (2, capital 3) |
| Inca | cities (8-12) | few | **peaceful** | warlike | south-west | **high** (3, capital 4) |
| Aztec | cities (8-12) | few | **aggressive** | warlike | west | **high** (3, capital 4) |

Two different things: the game's **temper** (aggression in the rules)
still drives how readily their braves raid and demand tribute. The
mod's **character** (decided with Mischa) drives their dealings with
other tribes: how fast a grudge grows into war (warlike 1.4x,
proud 1.0x, traders 0.8x, peaceful 0.6x), how often they bring gifts
(peaceful 1.5x, traders 1.2x, warlike 0.7x) and how readily they answer
a call for help (warlike 1.2x).

"Trade bonus" is added to the base price of 12 per unit when they value
goods, so cities pay noticeably more than camps for the same load.
Capitals (marked with a star in the Relations tab) are larger, pay more
and defend twice as well.

## What each tribe teaches

A settlement teaches one skill, picked when the game starts. The chances
below are the tribe's base odds; the land around the settlement shifts
them (a village by the sea is more likely to teach fishing). Walk a free
colonist (or indentured servant) into the settlement to learn it; you
find out which skill it is by visiting with any unit or by scouting.

- An ordinary settlement teaches **once**, then the skill is gone.
- A **capital** teaches **again and again**: send colonist after
  colonist.
- With **your mission** in the settlement and the game option "enhanced
  missionaries" on, an ordinary settlement keeps teaching too.
- An **angry** settlement teaches nothing; a **hateful** one kills the
  student.

| Tribe | Most likely skills |
|---|---|
| Apache | Master Cotton Planter 56%, Expert Farmer 28%, Sugar Planter 8%, Tobacco Planter 8% |
| Sioux | Master Tobacco Planter 47%, Expert Fur Trapper 23%, **Seasoned Scout 23%**, Expert Farmer 7% |
| Tupi | Master Sugar Planter 43%, **Seasoned Scout 22%**, Master Cotton Planter 22%, Fur Trapper 7%, Tobacco Planter 7% |
| Arawak | Master Sugar Planter 61%, Expert Fisherman 30%, Master Fur Trader 9% |
| Cherokee | Master Cotton Planter 34%, Expert Fisherman 17%, Expert Ore Miner 17%, **Seasoned Scout 17%**, Tobacco Planter 5%, Farmer 5%, Fur Trader 5% |
| Iroquois | **Expert Fur Trapper 36%**, Expert Farmer 18%, **Seasoned Scout 18%**, and 5% each: Cotton Planter, Tobacco Planter, Fisherman, Ore Miner, Fur Trader |
| Inca | **Expert Silver Miner 38%**, Expert Farmer 19%, Expert Fisherman 19%, Expert Ore Miner 19%, Master Weaver 6% |
| Aztec | **Expert Silver Miner 41%**, Expert Farmer 20%, Expert Fisherman 20%, Weaver 6%, Sugar Planter 6%, Ore Miner 6% |

Tip: Expert Farmers and Fishermen are worth the most early (food means
growth). A Seasoned Scout learned for free saves buying one in Europe.

## What they sell

Natives sell what their land produces, so look at the terrain around the
settlement. Going by what each tribe is good at, expect roughly:

- **Apache, Tupi, Cherokee:** cotton, sugar (Tupi), some furs.
- **Sioux:** tobacco and furs.
- **Arawak:** sugar.
- **Iroquois:** furs (their speciality), food.
- **Inca and Aztec:** silver (very valuable in Europe), ore.

They never sell trade goods. Buying raw goods cheaply from them and
selling them in Europe (or processing them first: furs into coats,
cotton into cloth) is a classic early income.

## What they love to buy (how prices work)

1. **The three wanted goods.** Each settlement names three goods it wants
   most (visible after a visit, and in the Relations tab). It pays
   **+50%** for the first, **+25%** for the second and **+10%** for the
   third. This is the single biggest factor: check before you load the
   wagon.
2. **Things they cannot make.** They value goods they lack. Manufactured
   and luxury goods (**cloth, coats, rum, cigars**) and **trade goods**
   are wanted in large amounts (they stock up "for ten years"). If a
   settlement grows the raw material itself (cotton next to a cotton
   field), it values the finished product (cloth) less.
3. **Raw goods are worth half.** Food, grain, fish, sugar, tobacco,
   cotton, furs, lumber, ore and silver are "farmed" goods: natives pay
   only half the normal rate for them.
4. **Weapons and horses fetch top price** when the braves still need
   arming. Profitable, but you are arming them, which can backfire if
   relations sour or if they fight your neighbours on your doorstep.
5. **Richer settlements pay more:** cities over villages over camps,
   capitals over ordinary settlements (see trade bonus above).
6. **Wagons beat ships.** Trading from a ship carries a 20% price
   penalty (game option "ship trade penalty", default -20%).
7. **A missionary helps:** with the game option "enhanced missionaries"
   on, a mission of yours in the settlement improves your prices.

**Shopping list for a first trade run:** trade goods (cheap in Europe),
cloth, coats, rum or cigars, matched against the settlement's three
wanted goods.

## Mood, and how it changes the deal

| Mood | Trading |
|---|---|
| Happy, Content | normal prices |
| Displeased | they pay half and charge double |
| Angry | they sell nothing; they only buy if you bring weapons, at a third of the price |
| Hateful | no trade; expect raids |

What raises their anger (with the mod's land rule): colonies and
worked tiles on land **taken from them without paying**, your armed
units inside their borders or right next to a village, attacking or
demanding tribute, other Europeans stirring them up. **(mod)** A colony
that only shares a border with their land angers nobody (Cuper's Cove
next to the Arawak); land you bought, or that the game gave you, does
not count. Land you held before this rule counts as taken only when it
lies inside a village's own land. What calms them: gifts (choose "Gift"
in the trade menu, now also part of a load; or an envoy with gold),
a mission, paying for land, peace through an envoy, and the founding
father **Pocahontas** (resets all tension). **Peter Minuit** lets you
take land without paying and without making them angry.

## Haggling

"Ask for a lower price" (when buying) or "ask for more" (when selling)
moves the price 10% your way each time it succeeds. The chance they
refuse grows: **25%** the first time, **40%** the second, **50%** the
third. A refusal ends the trade for this turn and the wagon loses its
moves. Rule of thumb: haggle once on a big deal, then accept.

(Our mod fixed a FreeCol bug where asking for a lower price on the only
goods a settlement sells was always rejected.)

## Missionary, scout, soldier

- **Scout** (colonist with 50 horses), "speak with chief": usually a gift
  of gold or a map of the area, rarely the scout is killed. A Seasoned
  Scout does better. Also reveals the skill and wanted goods.
- **Free colonist**: learns the settlement's skill (once per ordinary
  settlement, any number of times at a capital).
- **Missionary** (blessed in Europe or a colony with a church; a Jesuit
  Missionary is best): calms the tribe, produces converts over time who
  join your colonies (good outdoor workers), and with "enhanced
  missionaries" improves trade.
- **Soldier or dragoon**: can demand tribute or attack. Destroying a
  settlement yields treasure (cities and capitals a lot, the Inca and
  Aztec capitals a fortune) but makes the whole tribe and others hostile.
  Settlements defend with +50%, capitals +100%, Inca and Aztec capitals
  +200%.

## How tribes live in this mod

### Among themselves
- **They know each other** from the start and are at peace.
- **Old rival:** each tribe picks the tribe whose settlements come
  nearest. Settlements of two tribes within 4 tiles share a border;
  each border sours them a little more every turn.
- **Opinions** of other tribes: tension, the rival (-30), borders (-3
  each, up to 5), a European both hate or fight (+15), memories.
- **Wars between tribes are rare:** only on the old rival, only with a
  grudge (tension 600 or a bad memory), and only once the opinion falls
  to the character's line (warlike -39, proud -55, traders -69,
  peaceful -92). Peace after about 15 turns unless the hatred is
  complete.
- **Calls for help:** a tribe that lost a settlement to a European, or
  has enemy soldiers next to its capital, calls the others. Each weighs
  kinship (+30), its opinion of the caller, its character, its liking
  of the attacker, fear and distance; at 10 or more it declares war and
  sends 2 braves. The same call is heard once per 10 turns. An old
  rival that loathes the caller (-50) and likes the attacker sides with
  the attacker (King Philip's War).
- **Refugees:** half the people of a destroyed settlement flee to the
  nearest settlement of their tribe; the last settlement's people go to
  the tribe they like best.

### With you
- **Memories:** a settlement destroyed, land taken, a mission set up,
  arms sold to them (or to their rival), gifts, help, peace.
- **A settlement breaks off and joins you** after your happy mission
  has been there 20 turns and its tribe is weak or angry with you. An
  allied settlement stays native: free land for you, trade without
  anger, its braves never attack you and fight your enemies within 4
  tiles. Its tribe resents it and may march to take it back (anger
  800): defend it (war) or let it go.
- **A whole tribe becomes your ally** when two thirds of its
  settlements have your happy mission and it likes you, or when you
  both fight its old rival. Your wars become its wars; its calls for
  help come to you, and refusing ends the alliance.
- **Unrest and uprisings:** when two tribes (not rivals of each other)
  hate you (opinion -40 or lower) with a real grievance, the turn
  report says "The tribes speak of war against you". After 5 turns (7
  very easy ... 3 very hard) one rises: war and 3 braves at your
  nearest colony. Gifts and giving back land calm it.
- **Pontiac's rule:** a tribe whose European friend loses a colony
  near it to another European turns on the winner.
- **Paying a tribe to fight another tribe:** your missionary's incite
  lists the other tribes too (cheaper against their old rival);
  computer nations at war with a tribe do the same.

### Envoys to a tribe (Report > Send an Envoy)
One envoy per tribe every 5 turns, free; the answer comes at the start
of your next turn, and gold you send comes back on a no.
- **Peace, survival first.** The tribe compares its braves with your
  soldiers within 4 tiles of its villages.
  - Losing (2 braves per 3 of your soldiers or fewer, or a village lost
    to you in the last 20 turns): peace at once, humbly.
  - Even: peace for gold, half its anger above 300 (anger 900: 300
    gold).
  - Strong (1.5 braves per soldier or more): twice the gold, and if it
    dislikes you, conditions: give back the land you took, abandon a
    colony on its land (disbanded, colonists leave as free units, the
    land is theirs), pull your soldiers out of its land (peace the turn
    none is inside; 10 turns or the deal is off). Strong and full of
    hatred (opinion -90 or lower): no, they want revenge.
  - Peace brings the tribe's and its villages' anger down to 300.
- **A gift:** always taken; anger down by a tenth of the gold, and
  remembered.
- **Fight for me:** pick a nation or tribe you are at war with. An
  allied tribe joins for free (it fights your wars anyway). Otherwise,
  if it likes you: 100 gold per brave it has, 1.5x against a European
  nation, half against its old rival, from half to twice by how it
  likes you compared with the enemy, at least 100 (4 braves against
  France: 600); and only as your ally. If it dislikes you: no.

### How strong they are (companion page)
The game tells you how many settlements a tribe has (the Native Affairs
report), not how many braves. The page estimates: each settlement at
the middle of the sizes the rules give (a camp 4-6, a village 6-8, a
city 8-12, the capital bigger), braves you see counted as they are
(armed and mounted ones hit harder), the rest as plain braves. A plain
brave attacks with 1.5, a soldier with 3, so a tribe's army is shown
in soldiers' worth on the same scale as the nations.

## Which tribe is easiest to live next to

- **Peaceful:** Tupi, Cherokee, Inca. Good neighbours and trading
  partners.
- **Average:** Sioux, Iroquois. Fine if treated well; the Iroquois are
  strong villages, worth keeping as friends.
- **Aggressive:** Apache, Arawak, Aztec. Pay for land, keep a soldier
  fortified in nearby colonies, and consider an early mission.
- **(mod)** Mind the character too: the warlike tribes (Apache, Sioux,
  Inca, Aztec) carry grudges into wars with their neighbours and answer
  calls for help readily; the peaceful Arawak and the trading Tupi are
  slow to fight each other and bring gifts more often.
