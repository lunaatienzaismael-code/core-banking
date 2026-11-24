# ADR 0014: Risk Assessment Service and Velocity Tracking

- Status: Proposed by agent
- Date: 2026-11-24
- Deciders: Project owner (merge of the plan PR)
- Extends: [ADR 0012](0012-payment-saga-and-fund-reservation.md) (the risk step),
  [ADR 0013](0013-messaging-outbox-inbox-contracts.md) (messaging)

## Context

UC5 (AC5.1–AC5.3) puts a risk check before funds are reserved. ADR 0012 fixed
the place (`INITIATED` → risk → `RISK_APPROVED`) and said that until Phase 8
every payment is approved. This ADR fixes how `risk-service` is called and
where velocity counters live. It introduces **new infrastructure (Redis)** and
a new module, which `AUTONOMY.md` reserves for the owner: merging the plan PR
is that approval.

## Options Considered

### How the orchestrator reaches risk-service

| Option | Pros | Cons |
|--------|------|------|
| Command/reply over Kafka (`AssessRisk` → `RiskApproved` / `RiskRejected`) | Same pattern as the ledger steps; outbox + inbox already exist; no temporal coupling | One more hop |
| Synchronous HTTP call inside the saga step | Simple | Dual-write risk; risk outage blocks the saga consumer |

### Velocity window

| Option | Pros | Cons |
|--------|------|------|
| Fixed window counter (`INCR` + `EXPIRE`, atomic in one Lua script) | One key per debtor, O(1), easy to test | Allows a burst at a window boundary (up to 2× limit) |
| Sliding window log (sorted set per debtor) | Exact | More memory and commands |

Banks and card processors use sliding or token-bucket limits in a fast store
for velocity checks, with the amount rule as a separate static limit.

## Decision

- Command/reply over Kafka, reusing ADR 0013 (outbox, inbox, topics keyed by
  `paymentId`). `PaymentInitiated` stays for other consumers; the saga sends
  `AssessRisk` and `RISK_APPROVED` is reached on `RiskApproved`;
  `RiskRejected` → `REJECTED` with the reason.
- `risk-service` owns its decisions: amount threshold from configuration,
  velocity in Redis with a fixed window (atomic Lua script) keyed by debtor
  account. The boundary-burst weakness is accepted and documented; a sliding
  window is a later change behind the same port.
- Redis holds only derived, expirable counters; no money state. If Redis is
  unavailable the assessment fails and the message retries (ADR 0013); it
  never defaults to approve.
- `risk-service` has its own database for the inbox/outbox (ADR 0004).

## Consequences

- New module, new compose service, new Avro contracts (BACKWARD, ADR 0013).
- Rule order: amount first (no Redis hit for oversized payments), then velocity.
- Counters count assessed payments, so a redelivered `AssessRisk` must not
  increment twice: the inbox guards it.
