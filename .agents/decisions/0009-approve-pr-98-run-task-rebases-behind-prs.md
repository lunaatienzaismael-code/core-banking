# 0009 — Approve PR #98: /run-task rebases PRs that fall behind main

- Date: 2026-11-27
- Trigger: approve-pr
- Task: none
- Branch: chore/run-task-rebase-behind-prs
- PR: #98
- Approved head: a12ca7b57784d68da6d643d7bf024fc671d89746
- Status: Approved by staff engineer

## Question

PR #98 carries out record 0008. It has one commit, `a12ca7b`, which changes
three files. It replaces step 0.2 of `.claude/skills/run-task/SKILL.md`, adds
one clause to `AUTONOMY.md` and adds record 0008. The questions are: does the
skill text match 0008's Consequences and its Standing authorization, does any
of it weaken a guard, and are the head's checks acceptable?

## Options

| Option | Pros | Cons | Reversibility |
|---|---|---|---|
| Approve | Matches 0008 point by point. Ends the "waiting for PR #n" deadlock after every merge. Every rewrite is lease-guarded, checked with a range-diff and logged in a PR comment | The skill leaves out two minor lines of 0008 (below), which 0008 still covers | High: text only, and 0008 can be superseded |
| Reject and ask for the two lines | The text would be complete | Another round for wording that the skill already links to (0008) and that 0008 states as binding | n/a |

## How banks and production ledgers do it

Merge queues (GitHub merge queue, Bors, Gerrit rebase-on-submit, Zuul) rebase
an approved change onto the current tip and run CI again. The approval holds
only while the patch content stays the same. A conflict or a changed patch
sends the change back for review. Regulated change control (SOX, DORA ICT
change management) signs off on that rule once and audits each replay through
its trail, here the PR comment that carries the range-diff. The four-eyes
control stays on the content: the gate, the evaluator and the staff record.

## Decision

Approve.

- **Standing authorization, conditions 1–7, against the skill.** Condition 1
  is the flow branch of this repository, never `claude/…`. The skill only
  works on open PRs from `list-prs open`, and `main` is never a PR head.
  Condition 2: the case order means a PR with red checks never reaches the
  rebase case, and the skill checks for draft, `needs-human` and an approval
  path. Condition 3 is the `merge-base --is-ancestor` test. Condition 4 is a
  plain `git rebase origin/main` of exactly `H` (`switch -C <branch> H`),
  with an abort and a `force-push` escalation on a conflict or on an empty or
  dropped commit. Condition 5 is the range-diff before the push. Condition 6
  is `--force-with-lease=<branch>:H`, never a plain `--force` and never
  another ref. Condition 7 allows one rebase per run, on the lowest-numbered
  PR. All seven are present.
- **Consequences 1–8.** The skill covers the red path for task and non-task
  PRs, the PR comment that serves as the audit trail, both carry-over
  branches (an all-`=` range-diff gives a task PR a rebase evaluator round
  outside the 3-round cap, and gives a staff-record PR the label only after
  the gate run on the new head has completed and never over a red check),
  the no-carry-over path for any `!`, both stateless recoveries (both fail
  closed), the "otherwise wait" exit, and the closing rule that CI must be
  green before the merge. `scripts/gh-rest.sh` is untouched (8). The
  `AUTONOMY.md` change is the single clause that 0008 allows, and it sits
  under the bullet it names. The PR body lists 0008 under "Decisions" (9).
- **Two lines missing, neither of them a guard.** The skill does not repeat
  "a `progress.md` line rides on the next commit to that branch" (Consequence
  2), and it does not repeat "only a `request_changes` follows the usual
  path" (Consequence 3). The step links to 0008, 0008 is binding as written,
  and the second line is already the default evaluator flow. Not blocking.
- **Guards.** No threshold, test, gate script, path guard, evaluator or
  `gh-rest.sh` contract changes. The only loosening is that force-pushes need
  no record of their own each time. 0008 decided this with its reasons, and
  the conditions above limit it: lease, range-diff, no conflict, flow
  branches only, one per run. Carrying `staff-approved` over still needs the
  gate to find a committed record with `- PR: #n` at the new head, and the
  required checks still run on the new head.
- **Checks on the head.** "Build and test", "Mutation testing" and
  "End-to-end tests" are pending. "Autonomy gate" is red only because the PR
  touches protected paths and does not have `staff-approved` yet. This is the
  gate waiting for this approval, as in records 0004 and 0007, not a failing
  check on the content.

## Consequences

- Commit this record to `chore/run-task-rebase-behind-prs` in one
  `.agents/`-only commit (`chore(agents): record the staff engineer's
  approval of PR #98`), push it, add `staff-approved` to PR #98, and let the
  Autonomy gate run again. That commit is the only change after `a12ca7b`
  that this approval covers. Any other change to the branch needs a new
  `approve-pr`.
- Merge only after "Build and test", "Mutation testing" and "End-to-end
  tests" are green. If one turns red, this approval does not cover it: fix it
  forward and ask for `approve-pr` on the new head.
- If PR #98 falls behind `main` before it merges, rebase it by the procedure
  it adds (record 0008). It is a PR with its own staff record.
- Optional follow-up, not required: add the two missing lines to the skill in
  a later PR that touches it anyway.
- No ADR needed: this applies ADR 0005 and does not change it.
