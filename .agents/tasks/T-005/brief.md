# T-005 — Query an account's movements

- Use case: UC1 (`docs/spec.md`) · Phase: 3 · Type: feat
- Issue: #9 · Branch: `feat/T-005-account-movements-endpoint`
- Depends on: T-004

## Goal

`GET /accounts/{id}/movements` returns the account's posting lines in
chronological order, completing the UC1 vertical.

## Acceptance criteria

- **AC1.3 Query movements**
  - Given an account with postings
  - When its movements are queried
  - Then each posting line for that account is returned in chronological order
    with amount, currency, direction (debit/credit) and the owning transaction.
- **B1** — Unknown account returns a Problem Details 404 (AC1.4 for movements).
- **B2** — A newly opened account returns an empty movement list (completes
  AC1.1 from T-003).

## Scope

- In: contract path + schema, `GetAccountMovementsUseCase`, service, outbound
  port + persistence adapter query, controller method, web mapper.
- Out: pagination and filtering (can be added later with optional query
  parameters without breaking the contract).

## Design notes

- Each movement: posting id, transaction id, amount as positive minor units,
  currency, direction `DEBIT`/`CREDIT` (derived from the stored sign, T-002),
  occurred-at timestamp.
- Order by transaction occurred-at, then posting id, so ties are deterministic;
  name a test for the ordering with out-of-order inserts.
- Reuse the test fixture from T-004.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
