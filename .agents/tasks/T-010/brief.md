# T-010 — ADR 0009: materialised balance with atomic conditional updates

- Use case: UC2 (`docs/spec.md`) · Phase: 5 · Type: docs
- Issue: #24 · Branch: `docs/T-010-adr-materialised-balance`
- Depends on: none

## Goal

The deferred Phase 5 decision is recorded: balances are materialised as
per-account debit/credit counters next to the immutable postings journal,
updated by atomic conditional SQL, and the spec and ADR 0004 reflect it.

## Acceptance criteria

- **B1** — `docs/adr/0009-materialised-balance.md` (status Accepted) records:
  context (overdraft check on every write; cost of summing history; hot
  internal accounts); options (derived; materialised; snapshot + delta) and
  concurrency options (optimistic `@Version` + retry; pessimistic
  `SELECT … FOR UPDATE`; atomic conditional `UPDATE`; `SERIALIZABLE`), each
  with pros/cons; decision; consequences.
- **B2** — Decision content: `accounts.debits_posted` / `credits_posted`
  (`bigint`, minor units, non-negative) updated in the same DB transaction as
  the postings, by `UPDATE … SET x = x + :amount` (no read-modify-write in
  Java); for `NOT_ALLOWED` accounts the `WHERE` clause also requires the
  resulting normal-side balance ≥ 0 (zero rows updated = insufficient funds);
  a table `CHECK` enforces the same as defence in depth; postings stay the
  source of truth and the invariant `counters = Σ postings` is tested (and
  reconciled in UC6); `@Version` remains for account attribute changes only.
- **B3** — ADR 0004 gets a dated "Superseded in part by ADR 0009" note on the
  derived-balance and optimistic-locking-for-balance points (original text
  kept); `docs/adr/README.md` index lists 0009.
- **B4** — `docs/spec.md`: the "Balance is derived" assumption and AC2.5's
  "(optimistic locking / retry)" are reworded to match; the AC2.5 outcome (no
  lost update, final balance = exact sum of accepted transactions) is
  unchanged.

## Scope

- In: `docs/adr/0009-*.md`, `docs/adr/0004-*.md` (note only),
  `docs/adr/README.md`, `docs/spec.md`.
- Out: any code or changeset (T-012 onwards); skill updates (done in the
  task that changes the code they describe).

## Design notes

- Explain why hot internal accounts (settlement/suspense, `ALLOWED`) never
  conflict: their condition is always true, so they only take a brief row lock.
- Record HTTP mapping for UC2 errors: 422 for unbalanced, mixed-currency and
  insufficient-funds rejections; 400 for malformed input (ADR 0006 extension).
- Re-evaluation trigger: measured contention on a single constrained account.

## Definition of done

- [ ] Every criterion above is checkable in the diff
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
