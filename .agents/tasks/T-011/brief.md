# T-011 — Transaction domain model

- Use case: UC2 (`docs/spec.md`) · Phase: 5 · Type: feat
- Issue: #25 · Branch: `feat/T-011-transaction-domain-model`
- Depends on: T-016 (mutation gate active)

## Goal

A framework-free `Transaction` aggregate that can only exist if it is a valid
double-entry transaction, so T-013 can rely on it.

## Acceptance criteria

- **B1** — `Transaction` (id, occurred-at, lines) is built through a factory
  that rejects: fewer than two lines; lines whose signed amounts do not sum to
  zero; lines in more than one currency; two lines on the same account. Each
  rejection is a `DomainException` subtype with a message naming the cause.
- **B2** — Overflow while summing lines fails loudly (`Math.addExact`), not
  wraps (named test with `Long.MAX_VALUE` lines).
- **B3** — `Transaction` exposes its lines as an unmodifiable list and the
  per-account signed amounts T-013 needs to apply to counters.
- **B4** — Unit tests: one per rejection case and boundary above (java-testing
  rule 11), plus a valid two-line and a valid multi-line transaction.

## Scope

- In: `domain` (`Transaction`, new exceptions, reuse `PostingLine`,
  `CurrencyMismatchException`, `InvalidPostingException` where they fit).
- Out: persistence, use case, HTTP (T-013); overdraft (T-014).

## Design notes

- Reuse `PostingLine` (already rejects zero amounts) rather than a new line type.
- Exception naming suggestion: `UnbalancedTransactionException`; reuse
  `CurrencyMismatchException` for mixed currencies.
- Whether the account's own currency matches the line is checked in T-013
  (needs loaded accounts), not here.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally with no surviving mutant in `Transaction`
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
