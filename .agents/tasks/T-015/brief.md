# T-015 — Safe concurrency on shared accounts

- Use case: UC2 (`docs/spec.md`) · Phase: 5 · Type: test
- Issue: #29 · Branch: `test/T-015-concurrent-transactions`
- Depends on: T-014

## Goal

Concurrent transactions on the same accounts lose no update, never overdraw a
constrained account, and leave counters equal to the postings.

## Acceptance criteria

- **AC2.5** — Given N concurrent transactions touching the same account(s),
  when they are processed, then no update is lost and the final balance equals
  the exact sum of all accepted transactions (wording as updated by T-010).
- **B1** — Testcontainers IT with real threads (`ExecutorService` +
  `CountDownLatch` start gate, java-testing rule 8): N transfers from one
  `NOT_ALLOWED` customer account whose funds cover only K of them, all through
  one hot internal `ALLOWED` account. Asserts: exactly K accepted, N−K
  rejected with insufficient funds, customer balance = initial − K·amount ≥ 0,
  internal account counters exact.
- **B2** — After the run, for every account, counters equal the sums of its
  postings, and all postings sum to zero per currency.
- **B3** — Test is deterministic in outcome across repeated runs (run it
  several times locally before review; note the count in the review request).

## Scope

- In: ITs; production fixes only if the test exposes a defect.
- Out: retries, performance tuning.

## Design notes

- Make N large enough to contend (e.g. 50) while keeping the IT under a few
  seconds.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
