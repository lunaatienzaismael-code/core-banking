# 0007 — Approve PR #97: the Stop hook skips the JaCoCo goals

- Date: 2026-11-27
- Trigger: approve-pr
- Task: none
- Branch: fix/verify-hook-coverage
- PR: #97
- Approved head: 7291fde65996e760cad9e21daaf4fa66da2e1705
- Status: Approved by staff engineer

## Question

PR #97 carries out record 0005. It adds `-Djacoco.skip=true` to the Maven call
of the Stop hook `scripts/verify.sh`, and it adds record 0005 itself. Does the
diff match the record, does it weaken a guard, and are the head's checks
acceptable?

## Options

| Option | Pros | Cons | Reversibility |
|--------|------|------|---------------|
| Approve | Matches record 0005 line for line. Ends false hook failures that block T-036. Coverage stays enforced in CI. | The hook gives no local coverage signal (accepted in 0005) | High: one flag |
| Reject | None found | Keeps blocking correct work on a coverage number taken from part of the suite | n/a |

## How banks and production ledgers do it

Local checks are fast and deterministic: formatting, unit tests, architecture
rules. The authoritative quality gate (coverage, integration suites) runs once,
in CI, on the full suite, before the merge to the protected branch (see 0005).

## Decision

Approve.

- **Diff vs record 0005**: the PR has one commit, `7291fde`. It changes only
  `scripts/verify.sh`: the Maven call becomes
  `spotless:check verify -DskipITs -Djacoco.skip=true`, as 0005 prescribes, and
  the header and inline comments say that CI's full `verify` and the
  test-runner enforce coverage. The PR also adds record 0005. `pom.xml`, the
  `jacoco.minimum.*` thresholds and `ci.yml` are untouched, and
  `scripts/verify.sh` is declared in 0005's `## Protected paths`.
- **Guards**: no threshold, test, gate or evaluator changes. The hook's
  contract in `AUTONOMY.md` ("keeps a turn from ending on code that fails
  `spotless:check` or the unit and ArchUnit tests") still holds in full.
  `jacoco:check` still runs in the required "Build and test" job on the full
  suite with ITs. The same flag is already used by the CI mutation job.
- **Checks on the head**: "Build and test", "End-to-end tests" and "Mutation
  testing" are pending. "Autonomy gate" is red only because the PR touches a
  protected path and does not have `staff-approved` yet. This is the gate
  waiting for this approval, as in record 0004, not a failing build. The
  caller's local run of the hook command on the T-036 code exits 0.

## Consequences

- Add `staff-approved` to PR #97 and re-run the Autonomy gate.
- Merge only after "Build and test", "End-to-end tests" and "Mutation testing"
  are green. If one of them turns red, this approval does not cover it: the
  caller fixes it forward and asks for a new `approve-pr` on the new head.
- If the head moves after this record (for example, a bookkeeping commit), the
  caller asks for a new approval of the new head and does not edit this record.
- No ADR needed.
