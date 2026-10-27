# T-026 — Transactional outbox in payment-service

- Use case: UC4 (`docs/spec.md`) · Phase: 7 · Type: feat
- Issue: #56 · Branch: `feat/T-026-payment-outbox`
- Depends on: T-025

## Goal

Initiating a payment stores the payment and its `PaymentInitiated` event in
one database transaction, and a relay publishes that command to Kafka (ADR 0013).

## Acceptance criteria

- **AC4.1** — Given a payment initiation, when it commits, then the payment
  row and its outgoing message are in the same transaction: if the
  transaction rolls back, neither exists (named test); if Kafka is down, the
  payment is still created and the message is published once Kafka is back.
- **B1** — Shared module `messaging-support` (ADR 0013): outbox writer
  (`outbox_messages` columns as in ADR 0013) and a polling relay that holds a
  transaction-level advisory lock so one instance publishes at a time;
  published rows are marked, not deleted. The inbox part of the module comes
  with its first consumer (T-027).
- **B2** — `payment-service` changeset adds `outbox_messages`;
  `InitiatePaymentService` writes `PaymentInitiated` (key `paymentId`); an
  idempotent replay (AC3.1) writes no second message — named test.
- **B3** — Relay IT with Testcontainers PostgreSQL + Kafka: messages arrive in
  insertion order per key; while one relay holds the lock, a second instance
  publishes nothing.

## Scope

- In: `messaging-support` (outbox), payment outbound port/adapter, changeset, ITs.
- Out: consuming anything (T-027, T-029); outbox cleanup/retention.

## Design notes

- The use case depends on an outbound port (e.g. `PaymentEventPublisher`);
  the outbox is its adapter, so the domain never sees Kafka.
- The relay schedule and batch size are configuration with test-friendly
  values in the IT profile.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
