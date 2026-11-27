# 0005 — The Stop hook skips the JaCoCo goals

- Date: 2026-11-27
- Trigger: undeclared-protected-path
- Task: none
- Branch: fix/verify-hook-coverage
- Status: Decided by staff engineer

## Question

The Stop hook `scripts/verify.sh` runs `./mvnw <modules> spotless:check verify -DskipITs`.
The root `pom.xml` binds `jacoco:check` (BUNDLE line >= 0.90, branch >= 0.75)
to `verify`. Surefire and Failsafe write one shared exec file, so the hook
measures coverage without the ITs. A module whose new code is mostly adapters
that only ITs cover fails the check in the hook. That is what happens to
`risk-service` on `feat/T-036-risk-amount-rule`, while CI's full build of the
same commit is green. Should `fix/verify-hook-coverage` edit `scripts/verify.sh`,
and how?

## Options

| Option | Pros | Cons | Reversibility |
|--------|------|------|---------------|
| (a) `-Djacoco.skip=true` in the hook's Maven call | The hook checks only what AUTONOMY.md says it checks (Spotless, unit and ArchUnit tests). No false failures. Faster turn ends because no agent or report runs. CI already uses this flag for the mutation job (`ci.yml`). | The hook produces no coverage report at all | High: one flag in one script |
| (b) `-Djacoco.haltOnFailure=false` | Keeps a local report | The report measures unit tests only, so its number is misleading. Logs coverage warnings that look like failures at every turn end. Slower. | High |
| (c) Leave as is | No change | The hook blocks turn ends on false failures. It drives the agent to write filler unit tests for adapters, or to stall the autonomous run. | n/a |

## How banks and production ledgers do it

Delivery pipelines in regulated shops have tiers. A pre-commit or local tier
runs fast, deterministic checks: formatting, unit tests, architecture rules.
The quality gate that is authoritative (coverage thresholds, integration
suites, SonarQube-style gates) runs once, in CI, on the complete test set,
before the merge to the protected branch. The gate is measured only where its
input is complete, because coverage measured on part of the suite is a wrong
number, not a stricter one. The trade-off is later feedback on coverage, and
the merge gate makes up for it.

## Decision

Option (a). Money invariants are not touched (goal 1). Coverage stays
enforced, with the thresholds unchanged, where it is measured correctly: CI's
"Build and test" job runs full `./mvnw -B verify` with ITs and `jacoco:check`,
and that job is a required check on `main`. The `test-runner` also runs the
full build before review. So this weakens no guard: the hook's documented
contract (AUTONOMY.md, "The Stop hook") never included coverage, and its
header comment already gives the full suite to the test-runner and CI.
Option (b) keeps a unit-only coverage number that misleads a reviewer and adds
noise, so it is worse for reviewability (goal 4). Option (c) blocks correct
work on a false signal.

## Consequences

- On `fix/verify-hook-coverage`, change only the Maven call in
  `scripts/verify.sh` to
  `./mvnw "${args[@]}" spotless:check verify -DskipITs -Djacoco.skip=true`.
  Add one comment line saying that coverage is enforced by CI's full
  `verify` and by `test-runner`, because it needs the ITs.
- Do not touch `pom.xml`, the `jacoco.minimum.*` thresholds or `ci.yml`.
- The PR carries `staff-approved` per the path guard. Approving it is a
  separate `approve-pr` decision.
- No ADR needed: this is a tooling fix inside the existing policy.

## Protected paths

- `scripts/verify.sh`
