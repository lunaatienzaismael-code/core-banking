# T-034 — Make InitiatePaymentIT independent of saga timing

- Use case: none (test reliability; UC3 initiation tests) · Phase: 8 · Type: test
- Issue: #81 · Branch: `test/T-034-initiate-payment-it-saga-timing`
- Depends on: none

## Goal

`InitiatePaymentIT` gives the same result on every run, whatever the saga
does in the background. It is the first task of the autonomous flow: an
intermittent test turns CI red at random and costs fix rounds and runs.

Observed: CI run 44829278518 (PR #77) failed at
`InitiatePaymentIT.java:81`, where the GET of the `Location` returned
`RISK_APPROVED` and the test expected the POST body (`INITIATED`). A rerun
passed. Cause: the test runs with the outbox relay and the saga listener
live (the same CI log shows `AdvancePaymentSagaService` handling events). `PaymentInitiated` reaches the saga, which still approves risk
automatically (`AdvancePaymentSagaService`, "until the risk check exists"),
so the status can change between the POST and the test's DB read or GET.

## Acceptance criteria

- **B1** — Given the outbox relay and the saga are running, when
  `initiatedPaymentIsStoredAndReadableAtItsLocation` runs, then none of its
  assertions depends on whether the saga has advanced the payment yet. It
  still proves what its `@DisplayName` claims: the payment is created as
  `INITIATED` (from the synchronous POST response) and the `Location`
  resolves to the same payment (id, parties, amount, `initiatedAt`).
- **B2** — Every other test in `payment-service` that reads a payment's
  status after an initiation over HTTP or the outbox is checked for the same
  race. Each one found is fixed the same way, or listed in the PR body as
  checked and safe, with the reason.
- **B3** — Evidence of stability: the class passes 20 runs in a row in the
  task's environment, with the command and result recorded in the PR body.
  The repetition is not committed.

## Scope

- In: `payment-service/src/test/java/**` ITs that initiate payments.
- Out: production code; the saga's auto-approval (removed by the risk check
  later in Phase 8); test infrastructure shared with ledger-service.

## Design notes

- Prefer assertions that hold in every state the saga can reach over
  slowing or switching off the saga. The test is about initiation, and the
  shared test context (one per service since T-030) should not fork for one
  class. If a status must be asserted after the response, assert it against
  the states the state machine allows from `INITIATED` (ADR 0011), not one
  exact value.
- Do not add sleeps or retries around assertions to hide the race.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] CI `End-to-end tests` green on the PR head
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
