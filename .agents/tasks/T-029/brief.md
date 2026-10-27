# T-029 — Payment saga, happy and rejected paths

- Use case: UC4 (`docs/spec.md`) · Phase: 7 · Type: feat
- Issue: #59 · Branch: `feat/T-029-payment-saga`
- Depends on: T-026, T-028

## Goal

`payment-service` drives an initiated payment to `COMPLETED` or `REJECTED`
by reacting to the ledger's replies (ADR 0012), visible through
`GET /payments/{id}`.

## Acceptance criteria

- **B1** — Happy path IT (both services' adapters against Testcontainers
  Kafka): initiated → `RISK_APPROVED` (auto-approved until Phase 8) →
  `FUNDS_RESERVED` → `COMPLETED`; the debtor is debited and the creditor
  credited by the amount.
- **B2** — Reservation refused (insufficient funds) → `REJECTED` with the
  reason; no money moved.
- **B3** — Replies go through the payment inbox; a redelivered reply does not
  re-run a step, and an out-of-order or stale reply is ignored by the ADR 0011
  transition table, not crashed on (named tests).
- **B4** — Each transition and its next command are written in one
  transaction through the outbox (AC4.1 pattern) with optimistic locking on
  the payment (ADR 0011).

## Scope

- In: payment Kafka inbound adapter, saga use case, inbox, changesets.
- Out: compensation (T-030), retries/DLT (T-031), risk rules (Phase 8).

## Design notes

- The cross-service IT may run the ledger side through its real adapter or a
  test double publishing replies; pick the cheaper one that still proves the
  money moved, and say why in the PR.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
