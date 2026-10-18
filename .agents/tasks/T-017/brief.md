# T-017 — Get a transaction by id

- Use case: UC2 (`docs/spec.md`) · Phase: 5 · Type: feat
- Issue: #36 · Branch: `feat/T-017-get-transaction`
- Depends on: T-013

## Goal

The `Location` that `POST /transactions` returns resolves: `GET
/transactions/{id}` returns the recorded transaction (evaluator check
"Contract completeness", found in the T-013 review).

## Acceptance criteria

- **B1** — OpenAPI: `GET /transactions/{transactionId}` → `200` with the same
  `Transaction` schema `POST` returns; `404` Problem Details for an unknown id;
  `400` for a malformed id.
- **B2** — `GetTransactionUseCase` + read side (`JdbcClient` over
  `transactions` and `postings`, lines in a deterministic order).
- **B3** — HTTP IT: the `Location` of a recorded transaction, followed with
  `GET`, returns the same id, `occurredAt` and lines as the `201` body;
  unknown and malformed ids give 404 / 400 Problem Details.

## Scope

- In: OpenAPI, `application.port.in/out`, `application.service`,
  `adapter.in.web`, `adapter.out.persistence`, ITs.
- Out: listing or searching transactions.

## Design notes

- Line order: posting id order is deterministic but not the request order;
  either store a line number or document the order. Prefer documenting the
  order unless a consumer needs the request order.
- New `TransactionNotFoundException` (domain) mapped to 404 like accounts.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
