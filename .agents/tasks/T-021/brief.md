# T-021 — Initiate a payment and read it back

- Use case: UC3 (`docs/spec.md`) · Phase: 6 · Type: feat
- Issue: #45 · Branch: `feat/T-021-initiate-payment`
- Depends on: T-020

## Goal

`POST /payments` creates a payment in `INITIATED` and `GET /payments/{id}`
returns it, with validation errors as Problem Details.

## Acceptance criteria

- **B1** — OpenAPI: `POST /payments` (debtor account id, creditor account id,
  amount as `{amount, currency}` with `amount ≥ 1`) → `201` + `Location` +
  `Payment` body (id, status, accounts, amount, initiatedAt); `400` malformed;
  `422` same debtor and creditor. `GET /payments/{paymentId}` → `200` same
  schema, `404` unknown, `400` malformed id.
- **B2** — Liquibase changeset `payments` table with named constraints
  (positive amount, currency format, debtor ≠ creditor, status in the ADR 0011
  set) and a `version` column; JPA entity or `JdbcClient` adapter per the
  persistence skill.
- **B3** — HTTP ITs: created payment is persisted in `INITIATED` and the
  `Location` returns the same body; each validation error leaves no row.
- **B4** — The `Idempotency-Key` header is **not** part of this task's
  contract yet; T-022 adds it as required.

## Scope

- In: contract, `application.port.in/out`, `application.service`, web and
  persistence adapters, changeset, ITs.
- Out: idempotency (T-022); any call to the ledger (Phase 7).

## Design notes

- `initiatedAt` at microsecond precision (lesson from T-017).
- Use case `InitiatePaymentUseCase` is the transaction boundary.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
