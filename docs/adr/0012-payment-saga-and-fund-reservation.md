# ADR 0012: Payment Saga and Fund Reservation

- Status: Accepted
- Date: 2026-10-27
- Deciders: Project owner
- Extends: [ADR 0011](0011-payment-state-machine.md) (who drives each
  transition), [ADR 0009](0009-materialised-balance.md) (how the ledger moves
  money)
- Related: [ADR 0013](0013-messaging-outbox-inbox-contracts.md) (how the
  messages travel)

## Context

A payment initiated in `payment-service` (UC3) must move money in
`ledger-service`, which owns a different database (ADR 0004). No database
transaction spans both, so the transfer is a sequence of local transactions —
a saga [1] — where each step either succeeds or is undone by a compensating
step.

- **AC4.3** — a failure after funds are reserved triggers a compensating action
  that releases them; no money is left reserved or lost.

Three questions have to be answered before any messaging code exists:

1. **Who coordinates the steps** — a central orchestrator or the services
   reacting to each other's events.
2. **What "reserve funds" means in a double-entry ledger** whose only write
   operation today is a balanced transaction (UC2) and whose balance is a
   materialised counter guarded against overdraft (ADR 0009).
3. **Which reply drives which ADR 0011 transition**, and whether the
   `RELEASING` state ADR 0011 anticipated is needed.

## Options Considered

### Coordination

- **Choreography** — each service publishes events and the others react
  (payment initiated → ledger reserves → ledger posts…), with no component that
  knows the whole flow [2].
  - Pros: no central component; services only know events.
  - Cons: the flow exists only as the sum of subscriptions; compensation logic
    is spread across services; the ledger would need to know payment rules
    (when to post, when to release), which belong to payments.
- **Orchestration in `payment-service`** — the payment saga is an object in
  the payment service that sends commands to the ledger and reacts to its
  replies [2]; its state is the payment's status (ADR 0011).
  - Pros: the whole flow and its compensation read in one place; the ledger
    stays a generic "move money" service with no payment knowledge; the
    payment status already is the saga state, so nothing new is persisted.
  - Cons: the orchestrator is a point where logic concentrates; the ledger's
    command contract must be designed for it.
- **Workflow engine (e.g. Temporal [3])** — the saga is durable code run by an
  external engine that persists every step and retries activities.
  - Pros: retries, timers and history for free; long-running flows are easy.
  - Cons: a new runtime cluster to operate and test; the payment status would
    be duplicated in the engine's history; hides the outbox/inbox mechanics
    this project exists to show.

### Fund reservation

- **Transit account** — the reservation is an ordinary balanced transaction
  that debits the debtor's account and credits an internal *payments in
  transit* account. Completion moves the amount from transit to the creditor;
  compensation moves it from transit back to the debtor.
  - Pros: reuses the UC2 write path, its balance check and its overdraft guard
    (ADR 0009): an insufficient-funds reservation is refused exactly as a
    transfer is; every movement of money is a posting, so reconciliation
    (UC6) sees reserved money as a balance on a named account; no new domain
    concept in the ledger.
  - Cons: the debtor's movements show the reservation and, after a
    compensation, the release, as two postings; a completed payment takes
    two ledger transactions instead of one, and a compensated one three.
- **Holds (available vs ledger balance)** — the ledger keeps a list of active
  holds per account; the *available* balance is the balance minus the holds;
  the reservation writes no posting until the transfer settles.
  - Pros: the customer's movements show only the final transfer; a hold can
    expire on its own.
  - Cons: a second balance concept the ledger does not have; the overdraft
    guard of ADR 0009 would have to check balance minus holds atomically, a
    new invariant and new concurrency proof; reserved money is not visible in
    postings, so UC6's zero-sum check cannot see it.

## Decision

**Orchestration in `payment-service`, with a transit account in the ledger.**

### Transit account

- One *payments in transit* account per supported currency, type `LIABILITY`
  (credit-normal), overdraft not allowed. Its normal-side balance is the total
  amount currently reserved in that currency; it is never negative.
- The ledger provisions it lazily: its id is derived from the currency (a
  name-based UUID of `ledgerflow:payments-in-transit:<code>`), and the first
  reservation in a currency inserts it with `ON CONFLICT DO NOTHING` in the
  reservation's own transaction. Any currency works without a seed, and
  concurrent first reservations cannot create two accounts.
- Each payment that reaches a terminal state leaves the transit balance where
  it found it: `COMPLETED` moved the amount on to the creditor; `FAILED`
  moved it back to the debtor; `REJECTED` never reached it.

### Ledger commands and replies

The ledger exposes three commands, each answered by exactly one reply. Every
command carries the `paymentId`; the ledger knows nothing else about
payments.

| Command | Ledger transaction | Replies |
|---------|--------------------|---------|
| `ReserveFunds` | debtor → transit | `FundsReserved` or `ReservationRefused` (insufficient funds, unknown account, currency mismatch) |
| `PostTransfer` | transit → creditor | `TransferPosted` or `PostingRefused` (unknown creditor, currency mismatch) |
| `ReleaseFunds` | transit → debtor | `FundsReleased` |

A refusal is a reply, not an error: the ledger commits nothing, records the
command as processed and answers. `ReleaseFunds` cannot be refused for a
reservation the ledger itself made, because it reverses a committed posting
on accounts that already passed the same checks; a failure there is a
technical error handled by retries (ADR 0013), never a business outcome.

### Saga steps

| Payment status | Input | Transition (ADR 0011) | Next message |
|----------------|-------|-----------------------|--------------|
| — | `POST /payments` | creates `INITIATED` | `PaymentInitiated` event |
| `INITIATED` | `PaymentInitiated` | `approveRisk` → `RISK_APPROVED` | `ReserveFunds` |
| `RISK_APPROVED` | `FundsReserved` | `reserveFunds` → `FUNDS_RESERVED` | `PostTransfer` |
| `RISK_APPROVED` | `ReservationRefused` | `reject(reason)` → `REJECTED` | — |
| `FUNDS_RESERVED` | `TransferPosted` | `complete` → `COMPLETED` | — |
| `FUNDS_RESERVED` | `PostingRefused` | none | `ReleaseFunds` (carries the reason) |
| `FUNDS_RESERVED` | `FundsReleased` | `fail(reason)` → `FAILED` | — |

- **Risk step.** Initiation commits the payment and a `PaymentInitiated`
  event; the orchestrator consumes it, approves risk and sends `ReserveFunds`.
  Until Phase 8 every payment is approved. Phase 8 replaces the approval with
  a request to `risk-service` at the same point, and `PaymentInitiated` is
  the event a notification service can already consume. `POST /payments` keeps
  answering `INITIATED` (UC3).
- **One step, one local transaction.** Each row of the table is one database
  transaction in `payment-service`: record the incoming message as processed,
  apply the transition, write the next message to the outbox (ADR 0013). The
  payment's optimistic lock (ADR 0011) serialises two inputs for one payment.
- **Unexpected inputs are ignored, not failed.** An input whose transition is
  illegal for the current status (a duplicate that escaped the inbox, a stale
  reply) is logged and recorded as processed; the transition table already
  guarantees it changes nothing.
- **No timeouts in Phase 7.** A reply that never arrives leaves the payment in
  a non-terminal status; UC6's stuck-payment report (AC6.2) finds it.

### No `RELEASING` state

ADR 0011 named a `RELEASING` state as the expected extension if the saga
needed to show "compensation in progress". It is not added: between
`PostingRefused` and `FundsReleased` the payment stays `FUNDS_RESERVED`, which
is literally true (the money is still in transit), and the refusal reason
travels in `ReleaseFunds` and comes back in `FundsReleased`, so no column
holds it in between. The window is one round trip.
**Re-evaluation trigger:** support or UC6 needing to tell "reserved, posting
pending" from "reserved, release pending" — then ADR 0011 gains `RELEASING`.

## Consequences

- AC4.3 is provable end to end: after a refused posting, the debtor's balance
  equals its value before the payment and the transit balance is back to
  where it was.
- The ledger gains no payment concept and no new balance rule; its new
  inbound adapter only turns commands into the UC2 transactions it already
  records.
- A debtor's movements show the reservation as a debit and, if compensated,
  the release as a credit; the API does not hide them.
- UC6 (AC6.1) still holds as stated: the transit account is a ledger account
  like any other, so postings sum to zero per currency, reserved money
  included. A reconciliation can also compare the transit balance with the
  sum of `FUNDS_RESERVED` payments.
- Phase 8 changes one saga step (risk) without touching the others.

## References

1. H. Garcia-Molina, K. Salem, *Sagas*, ACM SIGMOD 1987 —
   <https://www.cs.cornell.edu/andru/cs711/2002fa/reading/sagas.pdf>
2. C. Richardson, *Pattern: Saga* (choreography and orchestration) —
   <https://microservices.io/patterns/data/saga.html>
3. Temporal documentation —
   <https://docs.temporal.io/>
