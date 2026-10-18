# LedgerFlow — Specification

> Status: living document. Acceptance criteria are refined per phase as use
> cases are implemented. All example data (IBANs, names, amounts) is fictitious.

## Purpose

LedgerFlow is an event-driven, SEPA-style payments platform built around a
**double-entry ledger**. It demonstrates how a payment core keeps money exactly
accounted for while coordinating asynchronous, distributed processing.

It is a backend portfolio project. The priority is what a technical reviewer in
European banking/fintech values: reasoned decisions (ADRs), correct distributed
consistency, serious tests, and a clean Git history of small, readable PRs.

## Actors

1. **API consumer (trusted channel)** — an authenticated upstream system (a
   channel / BFF), *not* an end user browsing. It initiates transfers and reads
   account data through the REST API.
2. **Back-office operator** — queries accounts and movements and runs/reviews
   reconciliation.
3. **Internal event consumers** — internal systems that react to final payment
   events (introduced from Phase 7).

## Assumptions and scope boundaries

- **Authentication/authorization is out of the skeleton.** A gateway is assumed
  to validate the caller and pass a trusted identity; services trust it. Adding
  real OAuth2/JWT is an explicit, optional later decision, not part of the core.
- **No-overdraft is a property of an account**, not a global ledger invariant:
  the domain models whether an account may go negative. Customer accounts are
  constrained; suspense/internal accounts may differ.
- **Postings are the source of truth for balances.** Each account also keeps
  materialised debit/credit counters, updated in the same database
  transaction as its postings and always equal to their sums (ADR 0009).
- **Sign convention.** Every posting line is stored as a signed amount:
  debit = positive, credit = negative. A balanced transaction therefore sums to
  zero, and the whole ledger sums to zero per currency.
- **Normal balance.** Every account has a *normal balance side* (debit or
  credit) given by its type: asset/expense accounts are debit-normal;
  liability/equity/income accounts are credit-normal. A customer deposit
  account is a **liability** of the bank, hence credit-normal. The reported
  balance is expressed from the account's normal side, so a customer with
  funds sees a positive balance.
- Ledger invariants hold **per currency**: a balanced transaction and the
  end-of-day zero-sum are evaluated within a single ISO-4217 currency, never by
  summing across currencies.

## Out of scope

- End-user UI, customer onboarding/KYC, sessions, rate limiting.
- Real authentication/authorization (assumed at the gateway).
- Multi-currency FX conversion and settlement with real schemes.
- Real bank connectivity; all counterparties and IBANs are fictitious.

---

## Use cases and acceptance criteria

Acceptance criteria use Given/When/Then and are intended to be verifiable by an
automated test. Amounts are integer **minor units** with an ISO-4217 currency
(ADR 0004).

### UC1 — Open an account and query its balance and movements
*Priority 1 · Phase 3*

- **AC1.1 Open account**
  - Given a request to open an account with a currency, an account type
    (which fixes its normal balance side) and an overdraft policy
  - When the account is created
  - Then it has a unique id, the given currency, a zero balance, and an empty
    movement list.
- **AC1.2 Balance matches postings**
  - Given an account with recorded postings
  - When its balance is queried
  - Then the balance equals Σ(debits) − Σ(credits) of that account in its
    currency for a debit-normal account, or Σ(credits) − Σ(debits) for a
    credit-normal account. Any stored counters change only together with
    postings, so the reported balance always matches the postings.
- **AC1.3 Query movements**
  - Given an account with postings
  - When its movements are queried
  - Then each posting line for that account is returned in chronological order
    with amount, currency, direction (debit/credit) and the owning transaction.
- **AC1.4 Unknown account**
  - Given an id that does not exist
  - When balance or movements are queried
  - Then the API returns a Problem Details (RFC 9457) 404 response.

### UC2 — Record a double-entry transaction between accounts
*Priority 2 · Phase 5*

- **AC2.1 Balanced transaction**
  - Given two accounts in the same currency
  - When a transaction with debit and credit lines is recorded
  - Then it is accepted only if total debits equal total credits in that
    currency; otherwise it is rejected and nothing is persisted.
- **AC2.2 No money created or destroyed**
  - Given any accepted transaction
  - Then the sum of all postings it creates is zero per currency.
- **AC2.3 No overdraft on constrained accounts**
  - Given a customer account with a no-overdraft policy and insufficient balance
  - When a transaction would drive its normal-side balance below zero
  - Then the transaction is rejected with a Problem Details response and no
    posting is persisted.
- **AC2.4 Currency consistency**
  - Given lines referencing different currencies
  - When the transaction is recorded
  - Then it is rejected.
- **AC2.5 Safe concurrency**
  - Given N concurrent transactions touching the same account(s)
  - When they are processed
  - Then no update is lost (atomic conditional updates, ADR 0009) and the
    final balance equals the exact sum of all accepted transactions.

### UC3 — Initiate a transfer with Idempotency-Key and a payment state machine
*Priority 3 · Phase 6*

- **AC3.1 Idempotent initiation**
  - Given the same `Idempotency-Key` sent twice for the same transfer
  - When the transfer is initiated
  - Then exactly one payment is created and both responses are identical.
- **AC3.2 Key conflict**
  - Given a reused `Idempotency-Key` with a *different* request body
  - Then the API rejects it with a Problem Details conflict response.
- **AC3.3 State machine**
  - Given a payment
  - Then it only transitions along allowed states; illegal transitions are
    rejected.

### UC4 — Process the transfer asynchronously (outbox, events, saga)
*Priority 4 · Phase 7*

- **AC4.1 Transactional outbox** — domain state change and the outgoing event
  are committed in the same database transaction; no dual-write.
- **AC4.2 Idempotent consumers** — redelivery of the same event produces no
  duplicate effect.
- **AC4.3 Saga with compensation** — a failure after funds are reserved triggers
  a compensating action that releases them; no money is left reserved or lost.
- **AC4.4 Retries and DLT** — poison messages end up on a dead-letter topic
  after bounded retries.

### UC5 — Risk control before reserving funds
*Priority 5 · Phase 8*

- **AC5.1 Amount rule** — transfers above a configured threshold are rejected.
- **AC5.2 Velocity rule** — too many transfers within a window (tracked in
  Redis) are rejected.
- **AC5.3 Risk precedes reservation** — funds are reserved only after risk
  approval.

### UC6 — End-of-day reconciliation
*Priority 6 · Phase 9*

- **AC6.1 Ledger sums to zero** — for each currency, the sum of all postings is
  zero.
- **AC6.2 No stuck payments** — no payment remains in a non-terminal state older
  than a configured age threshold; any such payment is reported.
