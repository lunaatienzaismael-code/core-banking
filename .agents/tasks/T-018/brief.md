# T-018 — ADRs for idempotent initiation and the payment state machine

- Use case: UC3 (`docs/spec.md`) · Phase: 6 · Type: docs
- Issue: #42 · Branch: `docs/T-018-payment-adrs`
- Depends on: none

## Goal

The two decisions UC3 rests on are recorded as ADRs before any payment code
exists, and the spec's AC3.2 wording matches the chosen status code.

## Acceptance criteria

- **B1** — ADR 0010 *Idempotent payment initiation*: `Idempotency-Key` request
  header (required, 1–255 chars); key and SHA-256 fingerprint of the canonical
  request stored as `idempotency_key` (unique) and `request_hash` columns of the
  `payments` row, written in the same database transaction as the payment;
  concurrent duplicates resolved by `insert … on conflict do nothing` then
  reading the stored payment (no "in progress" state); replay → identical
  `201`, body and `Location`; same key with a different body → `422` Problem
  Details; missing header → `400`; no expiry (the key lives as long as the
  payment). Options considered: generic idempotency table storing responses
  (Stripe style), key in a cache, `409` vs `422` (IETF
  `draft-ietf-httpapi-idempotency-key-header`).
- **B2** — ADR 0011 *Payment state machine*: states `INITIATED`,
  `RISK_APPROVED`, `FUNDS_RESERVED`, `COMPLETED` (terminal), `REJECTED`
  (terminal, before any reservation), `FAILED` (terminal, after a reservation
  that was released); the allowed transitions as a table; illegal transitions
  rejected by the domain; enum + transition table in the domain, `@Version`
  optimistic locking on the payment row; who drives each transition (Phase 7
  saga, Phase 8 risk). Options considered: Spring State Machine, state pattern
  classes, status column without rules.
- **B3** — ADR 0010 or 0011 also records the `payment-service` boundary: own
  database `payment-db` (ADR 0004); no synchronous call to the ledger at
  initiation (format validation only, accounts are checked by the saga in
  Phase 7); accounts referenced by ledger account id; its own `Money` value
  object rather than a shared-kernel module (revisit with a third service).
- **B4** — `docs/spec.md` AC3.2 says `422` Problem Details instead of
  "conflict"; `docs/adr/README.md` index updated.

## Scope

- In: `docs/adr/0010-*.md`, `docs/adr/0011-*.md`, `docs/adr/README.md`,
  `docs/spec.md` (AC3.2 only).
- Out: any code (T-019 onwards).

## Design notes

- Follow the structure and tone of ADR 0009 (context, options with trade-offs,
  decision, consequences, how banks / payment APIs do it).
- Every factual claim about external APIs or drafts must be one the evaluator
  can check from the ADR's own references (evaluator check "Documentation
  claims").

## Definition of done

- [ ] Every criterion above is visible in the ADRs/spec
- [ ] `test-runner` reports `pass` (Spotless)
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
