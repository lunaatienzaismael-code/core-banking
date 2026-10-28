# T-032 — End-to-end saga smoke test across both services

- Use case: UC4 (`docs/spec.md`) · Phase: 7 · Type: test
- Issue: #69 · Branch: `test/T-032-e2e-saga-smoke`
- Depends on: T-030, T-031

## Goal

One test runs ledger-service and payment-service together, as their Docker
images, and proves through their HTTP APIs that a payment moves the money: the
cross-service proof T-029's review deferred (its "indirect-test" minor).

## Acceptance criteria

- **B1** — Module `e2e-tests` (no production code) starts with Testcontainers,
  on one network: both service images built from their Dockerfiles, a
  PostgreSQL per service, Kafka and Schema Registry (the `messaging-contracts`
  fixture's images).
- **B2** — Happy path: open a funded debtor and a creditor through the ledger
  API, `POST /payments`; the payment reaches `COMPLETED` and the ledger
  balances show the debtor debited and the creditor credited by the amount.
- **B3** — Rejection: a debtor without enough funds; the payment reaches
  `REJECTED` and no balance changed.
- **B4** — Compensation (T-030): a creditor whose posting the ledger refuses;
  the payment reaches `FAILED`, the debtor's balance is back where it was.
- **B5** — Runs behind a Maven profile (`-Pe2e`) and in its own CI job, so the
  "Build and test" check keeps its time; the README says how to run it.

## Scope

- In: `e2e-tests` module, profile, CI job, README line.
- Out: load/performance; observability (Phase 9).

## Design notes

- Funding the debtor needs a ledger write path the API exposes (`POST
  /transactions` from an asset account); no direct SQL into a service's
  database.
- Image builds are slow; reuse Docker layer caching and build each image once
  per run.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `-Pe2e` green locally and in its CI job
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
