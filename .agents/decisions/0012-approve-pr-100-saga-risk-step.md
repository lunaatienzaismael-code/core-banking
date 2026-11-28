# 0012 — Approve PR #100 at head 07780bb: saga asks risk-service, e2e stack runs it

- Date: 2026-11-28
- Trigger: approve-pr
- Task: T-038
- Branch: feat/T-038-saga-risk-step
- PR: #100
- Approved head: 07780bb06c7c9b408d53ed0fc6452b335b12523d
- Status: Approved by staff engineer

## Question

Record 0011 rejected PR #100 at head `55c3f74` for three reasons. The e2e stack
commits had no evaluator round, the PR body was stale, and the progress note that
0010 asked for was missing. Does head `07780bb` clear those findings, and can the PR
merge with the protected path `.github/workflows/ci.yml`, which record 0010 declared?

## Options

| Option | Pros | Cons | Reversibility |
|---|---|---|---|
| Approve `07780bb` | Every finding of 0011 is closed. The review evidence covers all code that merges. Functional checks are green | None found | A merge to `main` can only be undone forward |
| Reject again | None: no finding is left open | Delays T-038 and T-039 for no reason | Full |

## How banks and production ledgers do it

Under four-eyes change control (DORA ICT change management), the sign-off is bound
to the artefact that was peer-reviewed. Here the evaluator's `review-2.json` covers
the full diff `origin/main...a970592`. The only commit after it, `07780bb`, changes
`.agents/` bookkeeping alone: the review file, a progress line, `review_rounds` and
`STATE.md`. Under `AUTONOMY.md` ("Review evidence") that does not reopen the review.
The release sign-off can therefore bind to `07780bb`.

## Decision

**Approve head `07780bb06c7c9b408d53ed0fc6452b335b12523d`.**

Findings of 0011, re-checked:

1. **No evaluator round on the e2e commits: closed.** `review-2.json` has head
   `a970592`, base `17affe2`, verdict `approve` with no blocker, major or minor
   finding, a clean-tree test run at that head and `ci_e2e: success`. The diff
   `a970592..07780bb` touches only `.agents/`.
2. **Stale PR body: closed.** The verdict section lists rounds 1 and 2. "How" covers
   the Dockerfile, the stack containers and the image builds. "Decisions" lists 0010
   and 0011.
3. **Missing progress note: closed.** `progress.md` records that the e2e stack work
   moved from T-039 per 0010, and that T-039 keeps the smoke tests.
4. **Bookkeeping order: as asked.** The new bookkeeping sits in closing
   `chore(agents)` commits. History was not rewritten.

Guards and protected path:

- The `ci.yml` change only adds a risk-service image build to the Docker image and
  e2e jobs, with the existing cache and tag pattern. No threshold, test, gate, path
  guard or evaluator setting changes. `ci.yml` is the only protected path in the
  diff, and record 0010 declares it.
- Checks of `07780bb`: "Build and test", "Mutation testing" and "End-to-end tests"
  are green. "Docker image" is pending. It was green on `55c3f74`, and only `.agents/`
  changed since then. "Autonomy gate" is red only because the `staff-approved` label
  is missing, which this record clears (same reading as 0011).
- Money and consistency: the saga reserves funds only after `RiskApproved` (AC5.3).
  `RiskRejected` sends no ledger commands. Redelivered or stale replies are absorbed
  by the inbox and the status guard (ADR 0012). All of this is covered by named ITs.

## Consequences

The caller now:

1. Commit this record. It is under `.agents/`, so the review evidence stays valid.
   Push, then add the `staff-approved` label to PR #100.
2. Let the gate auto-merge (rebase) once "Docker image" and the re-run "Autonomy gate"
   on the new head are green. If any functional check turns red, this approval
   lapses: fix forward and ask again.
3. After the merge, mark T-038 `done` in the next task's reconciliation, and start
   T-039 with the smoke tests only. The stack work is already on `main`.

## Protected paths

- `.github/workflows/ci.yml`
