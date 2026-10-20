# T-022 — Idempotent payment initiation

- Use case: UC3 (`docs/spec.md`) · Phase: 6 · Type: feat
- Issue: #46 · Branch: `feat/T-022-idempotency-key`
- Depends on: T-021

## Goal

Retrying `POST /payments` with the same `Idempotency-Key` never creates a
second payment, as ADR 0010 decides.

## Acceptance criteria

- **AC3.1** — Given the same `Idempotency-Key` sent twice for the same
  transfer, when the transfer is initiated, then exactly one payment is created
  and both responses are identical (status, body, `Location`).
- **AC3.2** — Given a reused `Idempotency-Key` with a different request body,
  then the API rejects it with a `422` Problem Details response and nothing
  changes.
- **B1** — Header required: missing or longer than 255 chars → `400` Problem
  Details.
- **B2** — Changeset adds `idempotency_key` (unique, named constraint) and
  `request_hash` to `payments`; the fingerprint is computed from the canonical
  command (not raw JSON bytes), so field order and whitespace do not matter —
  named test.
- **B3** — Concurrency IT (java-testing rule 8): N threads send the same key
  and body at once → exactly one row, every response `201` with the same body;
  run several times locally before review.

## Scope

- In: contract header, use case, persistence adapter, changeset, ITs.
- Out: key expiry; idempotency for other endpoints.

## Design notes

- `insert … on conflict (idempotency_key) do nothing`, then read the stored
  payment and compare `request_hash` (ADR 0010); no "in progress" state.
- A replay must not depend on the request's time: return the stored payment.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
