# Ship loans (plan)

A computer nation that lost its ships and cannot pay for a new one asks
another nation for a loan. The lender fronts the gold, the ship is
bought in the borrower's Europe at once, and the borrower pays it back
with rent. The human player is the main lender; computer nations may
lend to each other (second stage).

Why: the AI cheats are gone (branch `claude/remove-ai-cheats`, merged
into this branch). An AI with no ships and no gold could stay stuck.

Branch: `claude/ship-loans`, from `claude/fix-offer-freeze` (the newest
work: envoys, succession) with `claude/remove-ai-cheats` merged in.

## Where

- `common/diplomacy/Loan`: one loan and its pure rules (schedule,
  ceiling, counter, payments, default, seizure value, early payoff,
  war freeze). Saved as one line, like `Offer`.
- `common/diplomacy/LoanRules`: the candidate ("means") filter, the
  ceiling and the computer lender's rate.
- `common/diplomacy/CreditRecord`: a nation's public record and its
  standing (Good, New, Wary, Bad).
- `server/model/CreditRecords`: the game-wide store (like
  `LandClaims`); each computer nation saves its own record with its
  AI player.
- `server/ai/diplomacy/LoanDesk`: the borrower's turn (ask, pay, pay
  off early, default, seize), the human's answers, computer lenders.
- Loans live in the borrower's `EuropeanAIPlayer.getLoans()` (lazy
  getter, saved as an attribute). Lenders find them through the AI
  players, so both sides see the same object.
- Offers: `Offer.Kind.LOAN` (the request, then a counter) and
  `Offer.Kind.LOAN_DEFAULT` (seize / forgive / wait), both `direct()`,
  in the borrower's offer book, answered through `OfferMessage` /
  `InGameController.answerOffer` / `OfferPlanner.answer`.
  For LOAN: `goods` = ship type, `amount` = principal, `gold` = ship
  price, `deadline` = term, `delivered` = rent (tenths of a percent),
  `placeName` = the borrower's gold, fleet and credit for the dialog.
  The player's answer `amount` = rent * 100 + term.
  For LOAN_DEFAULT: answer `amount` 0 seize, 1 forgive, 2 wait.

## When a nation asks

Computer, colonial, not REF; a colony or units waiting in Europe; no
transport ship that can sail; cannot pay for one now; no loan running
or in default; 30 turns since its last loan closed; not asked in the
last 10 turns (after every candidate said no).

Principal = cheapest transport's price minus `spendable()`, rounded up
to 50, at least 100. The ship type is fixed at the request.

## Candidates (pure, `LoanRules.candidate`)

Contact, not at war, neither REF, lender alive; lender gold >= principal
(computer lender: `spendable()`, and opinion of the borrower at least
neutral); no loan of the borrower in default with that lender
(blacklist); computer lender: standing not Bad; lender has < 3 loans
running; borrower has none running. Liked most first.

One at a time: the human gets a dialog; a refusal (or no answer in 5
turns) blocks that lender for 10 turns and the borrower tries the next
on its next turn. Computer lenders answer in the same turn.

## Terms

| What | Number |
| --- | --- |
| Rent | 0.0% to 6.0% of the balance per turn, tenths; dialog default 2.0% |
| Term | 15 to 40 turns, default 30 |
| Installment | principal / term + rent * balance |
| Total extra | about rent * (term + 1) / 2 of the principal (2%, 30 turns: 31%) |
| Ceiling | 3.0%; Conqueror (proud) and Trading empire (greedy) +0.5%; Independence first (careful) -0.5%; +1.0% with no ships at all and a load waiting; +1.0% Bad standing asking the human |
| Counter | above the ceiling the borrower answers with the ceiling |
| Computer lender's rate | 2.0% (Trading empire 2.5%), +1.0% for a Wary borrower, never under 1.5% |
| Lending characters | Trading empire, Balance keeper, Settler (not Conqueror, not Independence first); opinion at least friendly (30) |

Payments only from gold above the reserve. The next installment is a
promise in `Treasury` (so it is kept back from shopping); when paying,
that promise is not counted against itself. All or nothing.

## Repayment, early payoff, default

- Missed: the amount stays due (principal part and rent). 3 missed in a
  row = default.
- Paid off early when `spendable()` >= balance + rent owed + this turn's
  rent + cushion (300; 100 when the rent is above 3.0%). Never while
  frozen or in default.
- Default: the credit record notes it, LOAN_DEFAULT for the lender,
  BAD_PAYER for everyone in contact, blacklist. A computer lender
  seizes. The human is asked: seize (default), forgive, wait (10 turns;
  unanswered for 5 turns = wait). Paying all arrears while waiting
  cures it (back to running, "cured" on the record, blacklist lifted).
- Seizure: when the ship is docked in Europe it changes owner
  (`ServerPlayer.csChangeOwner`, AI objects follow by
  `AIMain.ownerChanged`); passengers stay on the borrower's dock; goods
  go with the ship. Value = half its Europe price (half again if
  damaged), off the balance; more than the balance: the loan closes.
  Ship gone (sunk, captured): the whole balance stays a claim.
- What is left: a computer lender opens a grievance (cause DEBT,
  ultimatum for the claim). The human sees it in the report and on the
  page.
- Borrower or lender dies: VOID.

## War between the two

Payments and rent freeze (FROZEN). At peace: computer-computer, the
lender cancels the debt if it was losing; the borrower has it cancelled
if it was winning and is a Conqueror; else payments resume. A borrower
offering peace terms to the human lender asks for the debt to be
cancelled when it is the stronger side and a Conqueror. `DealValue.debt`
= half the balance.

## Credit record and standing

Per nation: repaid on time, repaid early, defaults (turn, forgiven,
cured), seizures, in default now. Standing: Good (2+ repaid, no default
in 100 turns), Bad (in default now, or a plain default under 30 turns),
Wary (a plain default 30-100 turns ago, or a forgiven or cured one in
100 turns), New otherwise. Defaults over 100 turns old are forgotten.

## Memories (weight, fade turns, trust)

| Kind | Numbers | Who about whom |
| --- | --- | --- |
| LOAN_GIVEN | 15, 40, 10 (x2 with no ships at all) | borrower about lender |
| LOAN_PAID | 1, 20, 1 per installment; x5 when repaid, x8 early | lender about borrower |
| LOAN_SQUEEZED | -3, 30, -2 per 0.5% above 3.0% (x2 from 5.0%) | borrower about lender |
| LOAN_DEFAULT | -30, 60, -30 | lender about borrower |
| LOAN_SEIZED | -10, 30, 0 (x2 for a proud Conqueror) | borrower about lender |
| LOAN_FORGIVEN | 25, 60, 20 | borrower about lender |
| BAD_PAYER | -3, 40, -3 | everyone in contact about a defaulter |
| GOOD_PAYER | 2, 30, 2 | everyone in contact about a nation that repaid |

Rent under 1.0% also gives a GIFT memory. LOAN_SEIZED is small because
most nations find a seizure hard but fair; a proud nation (Conqueror)
does not, so it remembers it twice as strongly.

## War loans (added 2026-10-26, Mischa's choices)

Gold for soldiers, no ship. Only they ask (the player does not offer).
- **When:** a computer nation at war with a European nation (not the
  King), with a transport ship (no ship: a ship loan comes first), no
  loan running, 30 turns after its last, short of the gold for 3
  dragoons. Principal = 3 dragoons' price minus `spendable()`, rounded
  up to 50, at most 3000.
- **Who:** the same candidates as ship loans (an enemy is never asked).
  The dialog says "at war with X ... for soldiers", and the chip says
  "losing the war" when it is (its ceiling then +1.0%).
- **The gold:** spent at once on dragoons in its Europe (the reserve is
  not counted for this), the rest stays in its treasury.
- **Default:** no ship, so goods. The player picks: the question lists
  the goods in all their colonies with what each fetches in the
  player's Europe market after tax; goods boycotted for the player are
  listed but can not be picked. One kind at a time, as much as covers
  the debt; asked again while something is owed and something worth
  taking is left; "take nothing more" leaves the rest as a claim. The
  player gets the gold (the market itself is not moved). A computer
  lender takes the goods worth most to it.

## Not in this round

Players offering loans on their own; loans for other things (colonists);
computer lenders forgiving or waiting; partial early payoff; price
differences between Europes; a "cancel their debt" line in the
player's own peace envoy.
