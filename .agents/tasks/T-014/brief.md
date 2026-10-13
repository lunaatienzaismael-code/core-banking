# T-014 — Reject overdrafts on constrained accounts

- Use case: UC2 (`docs/spec.md`) · Phase: 5 · Type: feat
- Issue: #28 · Branch: `feat/T-014-no-overdraft`
- Depends on: T-013

## Goal

A transaction that would take a `NOT_ALLOWED` account below zero on its normal
side is rejected atomically by the database, with Problem Details, and nothing
is persisted.

## Acceptance criteria

- **AC2.3** — Given a customer account with a no-overdraft policy and
  insufficient balance, when a transaction would drive its normal-side balance
  below zero, then the transaction is rejected with a Problem Details response
  and no posting is persisted.
- **B1** — The counter `UPDATE` for `NOT_ALLOWED` accounts carries the
  normal-side guard in its `WHERE`; zero rows updated →
  `InsufficientFundsException` → `422` Problem Details; the DB transaction
  rolls back.
- **B2** — New changeset adds `ck_accounts_no_overdraft` (policy `ALLOWED` or
  normal-side counters ≥ 0); a schema test asserts the constraint name.
- **B3** — Named boundary tests: leaving exactly zero is accepted; one minor
  unit short is rejected; an `ALLOWED` account may go negative; debit-normal
  and credit-normal constrained accounts both covered.
- **B4** — `LedgerFixture` can create `NOT_ALLOWED` accounts: an overload
  `account(currency, type, overdraftPolicy)`; the existing
  `account(currency, type)` keeps creating `ALLOWED` accounts so UC1 tests are
  untouched (closes the T-004 review nit, `test-data-gap`).

## Scope

- In: persistence adapter SQL, changeset, `InsufficientFundsException`, web
  handler, ITs, OpenAPI `422` example, `LedgerFixture` (B4).
- Out: concurrency (T-015).

## Design notes

- Only lines that reduce the normal-side balance can fail the guard; lines that
  increase it never do.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
