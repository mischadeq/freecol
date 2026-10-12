# Native tribes: trading, skills and temper

A guide to the eight native nations in FreeCol 1.2 (the standard
"FreeCol" and "classic" rules). Everything here comes from this
repository: the tribe data from `data/rules/classic/specification.xml`,
the trading rules from `IndianSettlement.java` and `NativeAIPlayer.java`.
So it describes what the game really does, not folklore.

In the game: the companion page's **Relations** tab (R) shows every tribe
you have met, the mood of each settlement, what it teaches and what it
wants once you have visited. Click a goods icon there for its Colopedia
entry.

## The eight tribes at a glance

| Tribe | Settlements | How many | Temper | Where on the map | Richness (trade bonus) |
|---|---|---|---|---|---|
| Apache | camps (4-6 people) | average | **aggressive** | centre | low (1, capital 2) |
| Sioux | camps (4-6) | average | average | north-west | low (1, capital 2) |
| Tupi | camps (4-6) | **many** | **peaceful** | south-east, south | low (1, capital 2) |
| Arawak | villages (6-8) | few | **aggressive** | east | medium (2, capital 3) |
| Cherokee | villages (6-8) | average | **peaceful** | north | medium (2, capital 3) |
| Iroquois | villages (6-8) | average | average | north-east | medium (2, capital 3) |
| Inca | cities (8-12) | few | **peaceful** | south-west | **high** (3, capital 4) |
| Aztec | cities (8-12) | few | **aggressive** | west | **high** (3, capital 4) |

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

What raises their anger: taking land without paying, founding colonies
close to them, military units nearby, attacking or demanding tribute,
other Europeans stirring them up. What calms them: gifts (choose "Gift"
in the trade menu), a mission, paying for land, and the founding father
**Pocahontas** (resets all tension). **Peter Minuit** lets you take land
without paying and without making them angry.

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

## Which tribe is easiest to live next to

- **Peaceful:** Tupi, Cherokee, Inca. Good neighbours and trading
  partners.
- **Average:** Sioux, Iroquois. Fine if treated well; the Iroquois are
  strong villages, worth keeping as friends.
- **Aggressive:** Apache, Arawak, Aztec. Pay for land, keep a soldier
  fortified in nearby colonies, and consider an early mission.
