# T-012 — Materialise account balances

- Use case: UC2 (`docs/spec.md`) · Phase: 5 · Type: feat
- Issue: #26 · Branch: `feat/T-012-materialise-balance`
- Depends on: T-010

## Goal

Accounts carry `debits_posted` / `credits_posted` counters (ADR 0009) and
`GET /accounts/{id}/balance` reads them, with UC1 behaviour unchanged.

## Acceptance criteria

- **B1** — New changeset adds `debits_posted` and `credits_posted`
  (`bigint not null default 0`, `ck_accounts_debits_posted_non_negative`,
  `ck_accounts_credits_posted_non_negative`) and backfills them from existing
  postings; applied changesets untouched.
- **B2** — `PostingRepository.signedNetOf` is replaced by a port that reads the
  counters; `GetAccountBalanceService` keeps its contract; UC1 ITs (AC1.2,
  AC1.4) pass unchanged.
- **B3** — `LedgerFixture.transfer` updates both accounts' counters in the
  same statement batch as the postings it inserts.
- **B4** — `AccountBalanceIT.noBalanceColumnExists` is replaced by a named
  invariant test: after fixture transfers, each account's counters equal the
  sums of its debit and credit postings.
- **B5** — `AccountEntity` maps the new columns read-only (or not at all) so
  JPA saves never overwrite counters.

## Scope

- In: changeset, `adapter.out.persistence`, `application.port.out`,
  `GetAccountBalanceService`, `LedgerFixture`, `AccountBalanceIT`,
  `persistence-postgres-liquibase` skill (Liquibase rule 5 and examples now
  describe counters instead of "no stored balances").
- Out: writing counters from a use case (T-013); overdraft guard and its
  `CHECK` (T-014).

## Design notes

- Normal-side balance from counters: debit-normal = debits − credits;
  credit-normal = credits − debits (`Math.subtractExact`), via the existing
  `Account.balanceFromNet`.
- Counter overflow: `bigint` addition overflow raises a PostgreSQL error; note
  it, no new handling.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
