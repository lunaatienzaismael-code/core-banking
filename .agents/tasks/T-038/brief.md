# T-038 — Saga asks risk-service before reserving funds

- Use case: UC5 (`docs/spec.md`) · Phase: 8 · Type: feat
- Issue: #90 · Branch: `feat/T-038-saga-risk-step`
- Depends on: T-036

## Goal

Saga asks risk-service before reserving funds (ADR 0014).

## Acceptance criteria

- **AC5.3** — Given an initiated payment, When risk approves, Then `ReserveFunds` is sent only after `RiskApproved`; on `RiskRejected` the payment is `REJECTED` and no funds are reserved.
- **B1** — Replaces auto-approval; ADR 0011 table and ADR 0012 updated; stale/redelivered replies ignored.

## Scope

- In: payment-service saga, contracts use, changesets
- Out: risk rules

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
