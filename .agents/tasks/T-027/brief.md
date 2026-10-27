# T-027 — Ledger reserves funds from a command, idempotently

- Use case: UC4 (`docs/spec.md`) · Phase: 7 · Type: feat
- Issue: #57 · Branch: `feat/T-027-ledger-reserve-funds`
- Depends on: T-025, T-026

## Goal

The ledger consumes the reserve-funds command, moves the amount from the
debtor to the *payments in transit* account (ADR 0012) and replies through its
own outbox; a redelivered command has no second effect.

## Acceptance criteria

- **AC4.2** — Given a reserve-funds command already processed, when Kafka
  redelivers it, then no second transaction is recorded and the reply is not
  duplicated in effect (named test over the counters of ADR 0009).
- **B1** — The *payments in transit* account exists per supported currency
  (changeset seed or idempotent startup provisioning — decide and record in
  the PR), type LIABILITY.
- **B2** — Inbox in `messaging-support` (ADR 0013): table
  `processed_messages` (message id primary key) written in the same
  transaction as the ledger transaction and the reply.
- **B3** — Replies: funds reserved (with the ledger transaction id) or
  reservation refused with a reason for insufficient funds, unknown account
  and currency mismatch — one named test per reason.
- **B4** — The ledger uses the `messaging-support` outbox for its replies (changeset adds
  `outbox_messages` to `ledger-db`).

## Scope

- In: ledger Kafka inbound adapter, use case, inbox, outbox wiring, changesets.
- Out: post-transfer and release-funds commands (T-028); retries/DLT (T-031).

## Design notes

- The Kafka listener is an inbound adapter calling an inbound port; it reuses
  `RecordTransactionUseCase` logic rather than duplicating posting rules.
- Domain refusals become refused replies, not exceptions that trigger
  retries.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
