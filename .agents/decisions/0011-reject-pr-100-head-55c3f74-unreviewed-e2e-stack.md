# 0011 — Reject PR #100 at head 55c3f74: the e2e stack commits were never reviewed

- Date: 2026-11-28
- Trigger: approve-pr
- Task: T-038
- Branch: feat/T-038-saga-risk-step
- PR: #100
- Status: Rejected

## Question

PR #100 (T-038, head `55c3f746b32ff91d072e597b6f6d1f25d445d8a4`) touches the
protected path `.github/workflows/ci.yml`, so it needs `staff-approved`. Record 0010
declared that path for the branch and set the conditions for asking for approval.
Does this head meet them, and can it be approved?

Checks of the head: "Build and test", "Docker image", "Mutation testing" and
"End-to-end tests" are green. "Autonomy gate" is red. It is red only because the PR
touches a protected path and has no `staff-approved` label, which is what this
approval exists to clear. A gate red for that reason alone would not block approval
on its own.

## Options

| Option | Pros | Cons | Reversibility |
|---|---|---|---|
| Approve now | Content matches record 0010; every functional check is green; merges T-038 today | Three non-`.agents/` commits after the reviewed SHA have no evaluator round, which breaks the review-evidence rule in `AUTONOMY.md` and Consequence 3 of 0010; the PR body would carry a verdict for a different SHA | A merge to `main` can only be undone forward |
| Reject: run review-2, finish the bookkeeping 0010 asked for, ask again | The evidence binds to the code that merges; costs one evaluator round on a small, additive diff | One more round before the merge | Full |

## How banks and production ledgers do it

Change control in regulated institutions (four-eyes principle, DORA ICT change
management) ties an approval to the exact artefact that was reviewed. A change made
after sign-off, even a test-infrastructure change, reopens the review. The release
manager's sign-off does not stand in for the peer review. It relies on that review
having happened. Here the evaluator is the peer review and the staff record is the
release sign-off. Approving without review-2 would merge code that was only signed
off, never reviewed.

## Decision

**Reject.** The content itself is sound, but the review evidence does not cover it.

Findings:

1. **Blocker: no evaluator round on the new code.** `review-1.json` approves
   `b1f3ca9`. After it come `61cb0f7` (`risk-service/Dockerfile`), `c3d45ef`
   (`e2e-tests/.../LedgerFlowStack.java`) and `a63d807` (`.github/workflows/ci.yml`,
   `scripts/e2e.sh`), and none of them is under `.agents/`. `AUTONOMY.md`
   ("Review evidence") says anything outside `.agents/` after the reviewed SHA needs
   a new round, and record 0010, Consequence 3, asked for review-2 explicitly. There
   is no `review-2.json` on the branch.
2. **Major: the PR body is stale.** `Verdict: APPROVE` and "Round 1" describe
   `b1f3ca9`, not this head. "Decisions" says "None", but record 0010 shaped the
   PR, and `AUTONOMY.md` requires the PR body to list the records. "How" does not
   mention the e2e stack, the Dockerfile or the CI change.
3. **Minor: Consequence 2 of 0010 is not done.** Neither the T-038 brief nor
   `progress.md` notes that the e2e stack work moved here from T-039.
4. **Minor: bookkeeping order.** `chore(agents)` commits (`e1e7f09`, `30693f7`) sit
   before the code commits. The flow wants one closing `chore(agents)` commit. Put
   the new bookkeeping (review-2, progress lines, `review_rounds`) in a final
   `chore(agents)` commit. Do not rewrite history for this.

What is fine and needs no change:

- The diff matches record 0010 point by point. The Dockerfile follows the
  `payment-service` pattern and runs as a non-root user. Redis runs through
  `GenericContainer` and the risk database through the existing `database()`
  helper. `e2e-tests/pom.xml` is unchanged. The CI change only adds a risk-service
  image build to the Docker image and e2e jobs, with the same cache and tag pattern.
- No guard is weakened. No threshold, test, gate, path guard or evaluator setting
  changes. `ci.yml` is the only protected path touched, and 0010 declares it.
  `risk-service/Dockerfile` and `scripts/e2e.sh` are not protected.
- Money and consistency: the saga change was reviewed in round 1, with ITs for
  "reserve only after `RiskApproved`" (AC5.3) and the inbox/stale-reply guard.
  End-to-end tests are now green against the real risk-service.

## Consequences

The caller now, on `feat/T-038-saga-risk-step`, with no force-push:

1. Append to `progress.md` that the e2e stack work (Dockerfile, stack containers,
   image builds) moved from T-039 to T-038 per record 0010, or add the same note to
   the T-038 brief.
2. Run `test-runner` on a clean tree at the current code head. Then run evaluator
   round 2 on the full diff `origin/main...HEAD`, with `ci_e2e` taken from the
   "End-to-end tests" run of this head (green). Under 0010 this is still fix
   attempt 1, and it is round 2 of 3.
3. Commit `review-2.json`, the progress line and `review_rounds: 2` in one closing
   `chore(agents)` commit, together with records 0010 and 0011 if they are not
   there yet. Push.
4. Update the PR body: the verdict line and summary from review-2, "How" covering the
   e2e stack and CI change, and "Decisions" listing records 0010 and 0011.
5. Once the checks of the new head are green, with "Autonomy gate" red only for the
   missing label, ask me again with `approve-pr` and the new head. If review-2 says
   `CHANGES`, fix it under the normal round limit before asking.
