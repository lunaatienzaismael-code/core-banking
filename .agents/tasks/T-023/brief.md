# T-023 — ADRs for the payment saga and messaging

- Use case: UC4 (`docs/spec.md`) · Phase: 7 · Type: docs
- Issue: #53 · Branch: `docs/T-023-saga-messaging-adrs`
- Depends on: none

## Goal

The decisions UC4 rests on are recorded as ADRs before any messaging code
exists, closing the "Event-driven design" deferred decision.

## Acceptance criteria

- **B1** — ADR 0012 *Payment saga and fund reservation*: orchestrated saga
  owned by `payment-service` (options: choreography, orchestration, a workflow
  engine such as Temporal); funds reserved with a double-entry transaction
  from the debtor to an internal *payments in transit* account (LIABILITY,
  credit-normal) in the ledger, completed by transit → creditor and
  compensated by transit → debtor (options: transit/suspense account, ledger
  holds with an available balance as in card authorisation/capture); the
  saga steps and which ledger reply drives each ADR 0011 transition (risk
  auto-approved until Phase 8); when compensation runs (posting to the
  creditor refused after a reservation) and that the transit account returns
  to zero for every finished payment; whether ADR 0011's `RELEASING` extension
  is needed (its re-evaluation trigger).
- **B2** — ADR 0013 *Messaging: outbox, idempotent consumers and contracts*:
  transactional outbox table per service published by a polling relay (one
  publisher per service, ordered per key), at-least-once delivery (options:
  dual-write, polling relay, CDC with Debezium); idempotent consumers through an inbox
  table keyed by message id written in the consumer's transaction; topics,
  message names (commands vs replies) and partition key `paymentId` for
  per-payment ordering; Avro contracts in a shared `messaging-contracts`
  module, Confluent Schema Registry, BACKWARD compatibility checked in CI;
  bounded retries with backoff then a dead-letter topic, and which errors skip
  retries; the outbox and inbox as a small shared module (like
  `architecture-rules`) rather than duplicated per service.
- **B3** — `docs/adr/README.md` index updated and the deferred
  "Event-driven design" entry removed; `docs/roadmap.md` Phase 7 line names
  the ADRs.

## Scope

- In: `docs/adr/0012-*.md`, `docs/adr/0013-*.md`, `docs/adr/README.md`,
  `docs/roadmap.md`.
- Out: any code (T-025 onwards).

## Design notes

- Follow ADRs 0009–0011: context, options with trade-offs, decision,
  consequences, how banks / payment systems do it.
- Every factual claim about external tools or patterns must be checkable from
  the ADR's own references (evaluator check "Documentation claims").

## Definition of done

- [ ] Every criterion above is visible in the ADRs/roadmap
- [ ] `test-runner` reports `pass` (Spotless)
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
