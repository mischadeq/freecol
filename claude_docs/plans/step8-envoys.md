# Step 8: the player's envoys (2026-10-17)

Mischa wants to make peace, alliances and deals with nations and tribes
"just as easy as they do". The computer nations already send offers by
messenger (peace with gold, alliances, joint wars, buy-outs); the
player gets the same envoy, judged by the same diplomat.

## Decisions (Mischa, 2026-10-17)

- **One envoy per nation or tribe every 5 turns**, like the computer
  nations' one offer per 5 turns. The game tells you when you can send
  to a nation again (a message at the start of the turn; the envoy
  window shows "in 3 turns").
- **The answer takes a turn**: the envoy leaves now, the answer comes at
  the start of your next turn. Sending is free.
- **Counter-offers above a line of trust**: a nation that trusts you
  enough and only wants more names its price, and its conditions (a
  tribe: "we would attack the Inca, but for a great deal of gold, and
  only as your ally"). A nation that does not trust you just says no,
  with the reason.
- **Tribes want to survive first.** A tribe that is losing makes peace
  humbly (maybe even with a gift). A tribe in a position of strength
  asks more: gold, and possibly that you abandon a colony on its land or
  pull your soldiers back. Extra gains are a bonus, survival the goal.

## How it works

- **The envoy window** (Diplomacy menu, and a button by each nation in
  Foreign Affairs): pick a nation or tribe (with "envoy ready" or "in N
  turns"), what you propose, gold to sweeten it, and for a joint war
  the enemy. Send.
- **An envoy is an `Offer`** from the player in the target's offer book
  (saved with the game), with new kinds: `ENVOY_PEACE`,
  `ENVOY_ALLIANCE`, `ENVOY_GIFT`, `ENVOY_WAR` (join me against X),
  `ENVOY_LEAVE` (leave the coalition against me), and for tribes the
  same peace, gift and war. `against` holds the enemy.
- **Answered on the target's next turn** (Europeans in `OfferPlanner`,
  tribes in `TribePlanner`): accept and carry it out, refuse with a
  reason, or counter with `Offer.Kind.TERMS` ("our terms: N gold, and
  ..."), which the player answers in the usual offer popup; accepting
  pays and carries the deal out.
- **The judgement** (pure rules in `common/diplomacy/EnvoyRules`):
  - value = what the diplomat scores the move at (`DealValue.stance`
    for peace and alliance, the war move for a joint war, the buy-out
    price for leaving a coalition) + the gold offered;
  - value >= 0: yes;
  - a war it chose itself less than 10 turns ago (`Commitments`): "not
    yet";
  - trust low or opinion very low: no, "they do not trust you";
  - otherwise it names its price (the missing value, rounded up to 50);
    for a joint war by someone not allied, the price is doubled and
    "as allies" is a condition;
  - a gift is always taken: memory `GIFT`, tension down by gold / 10.
- **Tribes**: strength from what the tribe knows (its braves against
  your soldiers near its villages, villages it lost to you lately,
  anger). Losing: peace at once, perhaps a small gift back. Even: peace
  for gold or goods. Strong and angry: gold, and a condition (pull back
  the soldiers near its villages, or abandon a colony on its land).
- **The alert**: at the start of a turn, "An envoy can go to France
  again" for nations you sent to, once their 5 turns are over.

## In steps

1. **Nations: peace, alliance, gift.** Envoy window, sending, the
   5-turn wait and the alert, answers next turn with reasons, counter
   terms, tests. (`EnvoyRules`, `EnvoyMessage`, `OfferPlanner`.)
2. **Nations: joint war, leave a coalition.**
3. **Tribes: peace (survival first), gift, fight a tribe or nation for
   you.**
4. **Trade in the same window**: the notice board and price agreements
   move into the envoy window.

## Open questions (for step 3)

- "Abandon a colony on our land": what happens to it: it is disbanded
  (colonists leave as free units), or given to the tribe? And "pull
  back your soldiers": peace starts once no soldier of yours stands
  within 2 tiles of their villages for a turn?
