# T-002 — Account domain model and derived balance

- Use case: UC1 (`docs/spec.md`) · Phase: 3 · Type: feat
- Issue: #6 · Branch: `feat/T-002-account-domain-model`
- Depends on: none

## Goal

The ledger domain has framework-free, invariant-enforcing types for money,
accounts and posting lines, and computes an account's balance from postings,
expressed from the account's normal balance side.

## Acceptance criteria

- **B1** — `Money` = `long` minor units + ISO-4217 currency; rejects null or
  non-ISO currency codes; addition/subtraction across currencies throws;
  overflow throws instead of wrapping.
- **B2** — `AccountType` (`ASSET`, `LIABILITY`, `EQUITY`, `INCOME`, `EXPENSE`)
  fixes the normal balance side: asset/expense debit-normal, the rest
  credit-normal (spec "Normal balance").
- **B3** — `Account` has an `AccountId`, currency, type and `OverdraftPolicy`
  (`ALLOWED` / `NOT_ALLOWED`; enforcement is UC2) and cannot be built invalid.
- **B4** — A posting line carries a signed, non-zero amount (debit `+`,
  credit `−`); its direction (`DEBIT`/`CREDIT`) is derived from the sign.
- **B5** — Given the signed net of an account's postings (Σ debits − Σ credits),
  the account reports its balance from its normal side: the net for
  debit-normal, its negation for credit-normal; zero postings → zero balance;
  a net in another currency is rejected.

## Scope

- In: `com.ledgerflow.ledger.domain` types above and their unit tests.
- Out: ports, services, persistence, web (T-003+); transaction recording and
  the balanced-transaction invariant (UC2, Phase 5); overdraft enforcement.

## Design notes

- `java.util.Currency` may back the currency check (JDK is allowed in the
  domain); expose a small value type if it reads better.
- The balance takes a net `Money` rather than a list of postings so T-004 can
  aggregate in SQL; also offer a pure summing helper over posting lines so the
  calculation has a domain-level reference implementation.
- Named tests for each normal side, the zero case, currency mismatch and
  `Long.MAX_VALUE` overflow.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
