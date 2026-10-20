# T-020 — Payment domain model and state machine

- Use case: UC3 (`docs/spec.md`) · Phase: 6 · Type: feat
- Issue: #44 · Branch: `feat/T-020-payment-domain`
- Depends on: T-019

## Goal

A framework-free `Payment` aggregate that can only move along the transitions
ADR 0011 allows.

## Acceptance criteria

- **AC3.3** — Given a payment, then it only transitions along allowed states;
  illegal transitions are rejected.
- **B1** — `Payment.initiate(...)` creates a payment in `INITIATED` with typed
  `PaymentId`, debtor and creditor account ids (distinct), a positive `Money`
  (`long` minor units + ISO-4217, ADR 0004) and the time it was initiated.
- **B2** — One named test per allowed transition and a parameterised test over
  **every** illegal (from, to) pair, including any transition out of a terminal
  state (java-testing rule 11); the rejection is a `DomainException` subtype
  whose message names both states.
- **B3** — Value objects reject invalid input: non-positive amount, unknown
  currency, same debtor and creditor.

## Scope

- In: `com.ledgerflow.payment.domain` and its unit tests.
- Out: persistence, HTTP, idempotency (T-021, T-022).

## Design notes

- `Money` is the payment service's own (ADR from T-018); keep it to what
  payments need (positive amount, currency) — no arithmetic it does not use.
- Transition methods named after the business event (`approveRisk`,
  `reserveFunds`, `complete`, `reject`, `fail`) rather than a generic
  `transitionTo`, unless ADR 0011 says otherwise.
- PIT gate applies (90 %).

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
