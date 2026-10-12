# T-004 — Query an account's derived balance

- Use case: UC1 (`docs/spec.md`) · Phase: 3 · Type: feat
- Issue: #8 · Branch: `feat/T-004-account-balance-endpoint`
- Depends on: T-003

## Goal

`GET /accounts/{id}/balance` returns the balance derived from the account's
postings, from its normal side; unknown accounts get a Problem Details 404.

## Acceptance criteria

- **AC1.2 Balance is derived**
  - Given an account with recorded postings
  - When its balance is queried
  - Then the balance equals Σ(debits) − Σ(credits) of that account in its
    currency for a debit-normal account, or Σ(credits) − Σ(debits) for a
    credit-normal account, computed from postings (no independently mutable
    balance field).
- **AC1.4 Unknown account**
  - Given an id that does not exist
  - When balance or movements are queried
  - Then the API returns a Problem Details (RFC 9457) 404 response.
  - *This task covers the balance endpoint and adds the 404 mapping; T-005
    (B1) asserts it for movements.*
- **B1** — Liquibase changesets create `transactions` (uuid id, occurred-at
  timestamp) and `postings` (uuid id, transaction FK, account FK, signed
  `BIGINT` amount with `amount <> 0` check, `char(3)` currency, index on
  account) tables. There is no balance column anywhere.
- **B2** — A malformed id returns `400` Problem Details.

## Scope

- In: contract path + schema, `GetAccountBalanceUseCase`, service, outbound
  port to read the signed net of an account's postings, persistence adapter
  (SQL aggregate), controller method, 404 mapping for an account-not-found
  domain/application exception.
- Out: writing transactions/postings through the domain or API (UC2, Phase 5);
  movements (T-005).

## Design notes

- Postings exist only through test fixtures in this phase (user decision): add
  a test-only helper that inserts a balanced transaction (e.g. customer
  account vs a suspense account) with `JdbcTemplate`. Fictitious data.
- Aggregate in SQL (`SUM(amount)` filtered by account), apply the normal side
  in the domain (T-002). PostgreSQL `SUM(bigint)` returns `numeric`: convert
  with an exact check so an out-of-range total fails loudly.
- While extending `ProblemDetailsHandler`, add a fallback for unexpected
  exceptions: 500 Problem Details without internals (T-003 review-1 nit).
- Named tests: debit-normal and credit-normal accounts with mixed postings,
  account with no postings, postings of other accounts not counted.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
