# 0004 — Approve PR #96: risk-service gains the shared messaging dependencies

- Date: 2026-11-27
- Trigger: approve-pr
- Task: T-036
- Branch: feat/T-036-risk-amount-rule
- PR: #96
- Approved head: 9c398d8b975d0fdf491707b3b8ebcd99b2f4c109
- Status: Approved by staff engineer

## Question

PR #96 (T-036, risk assessment with the amount rule, ADR 0014) edits the
protected existing POM `risk-service/pom.xml`. The Autonomy gate is red only
for that reason; Build and test, Docker image, End-to-end tests and Mutation
testing are green on the head, and the evaluator's `review-2.json` approves.
Are the new dependencies right, and does anything in the diff weaken a guard?

## Options

| Option | Pros | Cons | Reversibility |
|--------|------|------|---------------|
| Approve the POM change as is | Same messaging stack as ledger- and payment-service (ADR 0013); no new third-party library; versions managed by the parent | One more service depends on Confluent's Avro serializer | High: dependencies can be dropped in a later PR |
| Reject and ask for a different transport or serializer in risk-service | None for this phase | Breaks the single contract format of ADR 0013; risk-service could not read `AssessRisk` or write the replies the saga expects | n/a |
| Reject and ask to split the POM change into its own PR | Smaller protected-path PR | The POM change has no value without the use case; churn with no reviewability gain (the POM delta is 29 lines) | High |

## How banks and production ledgers do it

Production payment platforms keep one integration contract per bus: every
service speaks the same schema format against a schema registry, with
compatibility rules enforced centrally, and every service that consumes
commands and emits replies uses a transactional outbox and an idempotent
inbox. A bank's dependency control (third-party risk, SBOM) asks whether a
change brings a new supplier or a new version line. Reusing the components
already approved for the other services, with versions pinned in the parent,
is the low-risk case. The trade-off is coupling to one vendor's serializer,
which ADR 0013 already accepted for the whole platform.

## Decision

Approve the head `9c398d8b975d0fdf491707b3b8ebcd99b2f4c109`.

- **POM**: the new compile and runtime dependencies (`spring-boot-starter-kafka`,
  `messaging-contracts`, `messaging-support`, `kafka-avro-serializer` at
  runtime) and the test dependencies (`messaging-contracts` test-jar,
  `testcontainers-kafka`) match the ones `ledger-service` and
  `payment-service` already declare on `main`. No version is set in the
  module, no third-party library is new to the build, and the parent POM and
  the `<build>` section (plugins, JaCoCo, mutation and Spotless settings) are
  untouched. That is what ADR 0013 needs for B1 (inbox in, outbox out), so the
  dependencies are within the brief's scope.
- **Money (goal 1)**: amounts stay `long` minor units; `AmountRule` never
  compares across currencies (a payment in a currency other than the
  configured limit currency is an `InvalidAssessmentException`, not a
  decision), and non-positive amounts are refused. Strictly above the limit
  is rejected, at the limit is approved, as AC5.1 states.
- **Consistency (goal 3)**: inbox insert, rule and outbox reply run in one
  `@Transactional` use case; the offset is committed after it; a malformed
  assessment is not retried and goes to the dead-letter topic. A redelivery
  test over real Kafka and PostgreSQL proves exactly one reply.
- **Guards**: no threshold, test, gate, path guard or evaluator file is
  changed. The test-only overrides in `TestcontainersConfiguration` (fast
  relay, short retries, a 100 000 minor-unit limit) change test timing and
  data, not any guard. The commits after the reviewed head `6212c29` touch only
  `.agents/` bookkeeping (reviews, `tasks.json`, `STATE.md`, `progress.md`),
  so the evaluator's verdict still covers all the code.
- The evaluator's remaining minor finding (the malformed path to the
  dead-letter topic is tested only at unit level, not over Kafka) does not
  block: the DLT path comes from the shared `DeadLetters` that the other
  services already test, and the brief's ACs are met.

## Consequences

- The caller commits this record onto `feat/T-036-risk-amount-rule`, then
  re-runs the Autonomy gate. The commit moves the head, so the gate must accept
  a record bound to PR #96 whose only later commit is this record. If the gate
  wants the record's head to be the final one, the caller asks for a new
  approval of the new head rather than editing this record.
- T-038 (saga) must handle a payment whose `AssessRisk` was dead-lettered and
  so got no reply (a timeout or an explicit failure reply). Record it in the
  T-038 brief; it is not needed in T-036.
- No ADR is needed: the change applies ADR 0013 and ADR 0014 as written.

## Protected paths

- `risk-service/pom.xml`
