# 0010 — T-038 makes the e2e stack run the real risk-service

- Date: 2026-11-28
- Trigger: ci-red (fix attempt 0)
- Task: T-038
- Branch: feat/T-038-saga-risk-step
- Status: Decided by staff engineer

## Question

PR #100 (head `30693f7`) is green on "Build and test", "Docker image", "Mutation
testing" and "Autonomy gate", but "End-to-end tests" is red: the three tests in
`e2e-tests` `PaymentSagaIT` time out. T-038 makes the saga wait for `RiskApproved` on
`risk.replies` before `ReserveFunds`, and the e2e stack (`LedgerFlowStack`) starts
only ledger-service and payment-service. risk-service exists (T-037, done) but has no
Dockerfile, and neither `scripts/e2e.sh` nor `.github/workflows/ci.yml` builds its
image. How does T-038 get back to green?

The evaluator verdict is real: `review-1.json` says `approve` for `b1f3ca9`, and the
commits after it are `chore(agents)` bookkeeping only. It recorded `ci_e2e: absent`,
so it did not see the e2e failure.

## Options

| Option | Pros | Cons | Reversibility |
|---|---|---|---|
| A. Test-side Kafka responder in `e2e-tests` that answers every `AssessRisk` with `RiskApproved` | Small; no new image | An end-to-end suite with a stubbed service is no longer end-to-end; it is thrown away as soon as T-039 brings the real service; touches a protected POM for test deps; hides contract drift between payment-service and risk-service | Easy to remove, but it is throwaway work |
| B. Add `risk-service/Dockerfile`, Redis and a risk PostgreSQL to the stack; build the image in `scripts/e2e.sh` and `ci.yml` | The e2e suite tests the real saga across the three services; the stack work T-039 needs anyway is done once; main stays green | Larger PR; edits the protected workflow; widens T-038 beyond "payment-service saga" | Fully reversible; additive infrastructure |
| C. Merge with e2e red, fix in T-039 | Smallest now | Overrides a red check, which policy forbids; main carries a broken e2e suite | Not acceptable |
| D. Re-order: move T-039's stack work into T-038's brief | Same result as B, framed as a re-brief | Same as B; it is B | Same as B |

## How banks and production ledgers do it

A payment hub's end-to-end (system integration) tests run every service on the
critical path of the payment for real, and stub only what lies outside the boundary
(the clearing system, the scheme, a third-party sanctions provider). The fraud/risk
screening step is inside the boundary: in SEPA SCT processing it is a mandatory
step before the debit is booked, and its contract with the hub is exactly what an
end-to-end test exists to catch drifting. Contract stubs belong to service-level
tests (as `PaymentSagaIT` and `DeadLetterIT` in payment-service now do), not to the
end-to-end suite. The trade-off is a heavier stack, paid once.

## Decision

**Option B (which is D in substance): T-038 brings risk-service into the e2e stack.**

- Money: no effect on the ledger; it makes the suite prove that funds are reserved
  only after a real `RiskApproved`, which is what AC5.3 protects.
- Bank practice: the risk step is on the critical path; the end-to-end suite must run
  it for real. A stub (A) would make the suite pass while proving nothing about the
  payment-service/risk-service contract.
- Consistency: the real outbox/inbox path of risk-service is exercised end to end.
- Reviewability: additive commits (Dockerfile, stack containers, image build), each
  readable on its own.
- Later phases: T-039 keeps its own value (the over-threshold rejection with no money
  moved, and the approved path asserted explicitly), on a stack that already exists;
  nothing is thrown away. Option A would be built and deleted within one task.
- C is ruled out: a red check is never overridden.

Scope stays tight: the Dockerfile follows the existing `payment-service/Dockerfile`
pattern; the stack adds one Redis container and one PostgreSQL database for risk, wired
through the `RISK_*` environment variables `risk-service/application.yml` already
reads. No new risk rules, no new production behaviour, no T-039 test cases. The
existing three e2e tests must use amounts below the risk threshold; if any does not,
adjust the test data, not the threshold.

## Consequences

The caller now:

1. On `feat/T-038-saga-risk-step`, add atomic commits:
   - `build(risk): add risk-service Dockerfile` (same pattern as payment-service);
   - `test(e2e): run risk-service with Redis and its database in the e2e stack`
     (`LedgerFlowStack`, Redis via `GenericContainer`, so no new dependency is
     expected; if `e2e-tests/pom.xml` does turn out to need one, stop and ask me,
     it is not declared here);
   - `ci: build the risk-service image for the e2e job` (`.github/workflows/ci.yml`,
     both the Docker image job and the e2e job, same cache/tag pattern) and the same
     in `scripts/e2e.sh`.
2. Note in the T-038 brief (or `progress.md`) that the e2e stack work moved here from
   T-039 per this record; T-039 keeps the rejection and approved-path smoke tests.
3. Run `test-runner`, then `scripts/e2e.sh`, then a new evaluator round (review-2):
   the diff changed after review-1.
4. The PR touches a protected path, so it needs `staff-approved`: ask me with
   `approve-pr` once every check of the new head is green.
5. This counts as the approach for fix attempt 1; the "2 fix attempts" limit still
   applies.

## Protected paths

- `.github/workflows/ci.yml`
