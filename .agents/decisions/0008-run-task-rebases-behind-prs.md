# 0008 — /run-task rebases "behind" PRs under a standing force-push authorization

- Date: 2026-11-27
- Trigger: undeclared-protected-path (with a standing force-push authorization)
- Task: none
- Branch: chore/run-task-rebase-behind-prs
- Status: Decided by staff engineer

## Question

`main` requires up-to-date branches (strict status checks), linear history and
rebase merge (ADR 0005), and the Autonomy gate arms auto-merge. Every merge
leaves the other open PRs "behind". `/run-task` step 0.2 then exits "waiting
for PR #n" forever: nothing rebases the PR. A rebase is a push, so the gate's
`synchronize` run removes `staff-approved`. For a task PR merging on
`Verdict: APPROVE`, the gate's SHA rule also fails, because the reviewed
commit is no longer an ancestor of the head. Record 0006 repaired this once,
by hand, for PR #96.

Should branch `chore/run-task-rebase-behind-prs` add a recovery step to
`.claude/skills/run-task/SKILL.md` (and expose `mergeable_state` in
`scripts/gh-rest.sh`)? And should the force-push it needs get a standing
authorization instead of one record per rebase?

## Options

How to detect "behind":

| Option | Pros | Cons | Reversibility |
|---|---|---|---|
| (D1) `mergeable_state` from `gh-rest.sh pr-state`/`list-prs` | GitHub's own view; also shows `dirty` | The list-pulls endpoint does not return `mergeable_state`, so `list-prs` would need one extra call per PR. GitHub computes the value lazily, so `unknown` comes first and needs a retry. `blocked` can hide `behind` while required checks are pending. It edits a second protected script and changes its output contract | High |
| (D2) Local git after `git fetch`: `git merge-base --is-ancestor origin/main origin/<branch>` fails → behind | Deterministic, offline after the fetch, no retry. No change to `gh-rest.sh` or its contract. A conflict shows up as the rebase stopping, which is exactly where the flow has to act | Does not show `dirty` up front (the rebase does) | High |

What carries the approval over to the rebased head:

| Option | Pros | Cons | Reversibility |
|---|---|---|---|
| (A1) Task PR on the evaluator path: a staff `approve-pr` record built on the range-diff, then `staff-approved` | One mechanism for every PR | Turns every task PR into a staff-approved PR. The label then has to be added after the gate's `synchronize` run, which is a race. It skips the policy rule that code commits after the reviewed SHA need a new review round. The tests are not re-run locally on the combined code | High |
| (A2) Task PR on the evaluator path: a new evaluator round on the rebased head (test-runner + evaluator, a new `review-N.json`, an `.agents/`-only bookkeeping commit) | This is what the policy already requires ("anything else needs a new review round"). The evidence stays bound to the new SHA, including a local run on main + patch. The gate passes on its normal path with no label, so there is no race with `synchronize`. Fully stateless: "latest review head is not an ancestor of the PR head" can be computed from git | One evaluator round and one build per rebase | High |
| (A3) PR approved through a staff record: carry the record over when the range-diff is identical, as 0006 did, and re-add `staff-approved` after the gate's `synchronize` run on the new head has completed | Same rule as merge queues: identical patches on a new base, with fresh CI. Precedent 0006 | The label is a separate step after the push, so a run can die between the two | High |
| (A4) Teach the gate to accept patch-id equivalence | No re-review at all | It weakens the gate's SHA rule, which cannot see the old commits after a force-push. More code in the barrier itself | Medium |

Force-push authorization:

| Option | Pros | Cons | Reversibility |
|---|---|---|---|
| (F1) One staff record per rebase (as 0006) | Each rewrite is reviewed | One staff round trip for a mechanical step after every merge. Records pile up | n/a |
| (F2) A standing authorization, bounded by the conditions below | Recovery runs without a judgment call. The conditions are machine-checkable (lease, range-diff, no conflict) | A rule instead of a review per case | High: the old head stays reachable through the lease SHA and the PR timeline |

## How banks and production ledgers do it

Change management in regulated shops (SOX, DORA ICT change control) wants the
change that reaches the main line to be exactly the approved change, and the
trail to be auditable. Merge queues (GitHub merge queue, Bors, Gerrit
rebase-on-submit, Zuul) do the mechanical part on their own: they rebase the
approved change onto the current tip, re-run CI, and keep the approval only
while the patch content is unchanged. A conflict or a content change sends the
change back for review. Most of them serialize, one candidate at a time,
because rebasing every open change after each merge burns CI and keeps
invalidating itself. A standing, rule-bound authorization for that rebase is
the norm. A human signs off on the rule, not on each replay.

## Decision

**Yes to the recovery step in `.claude/skills/run-task/SKILL.md`. No change to
`scripts/gh-rest.sh`. A standing force-push authorization, bounded as below.**
Detection uses D2. Approval carry-over uses A2 for task PRs on the evaluator
path and A3 for PRs approved through a staff record. Force-push uses F2.

This does not touch money or the ledger (goal 1). It is merge-queue practice
(goal 2): same patches, new base, fresh CI, and approval lost on any content
change. On consistency (goal 3), D2 and A2 are deterministic and stateless. A3
fails closed when a run dies halfway. A4 loses because it loosens the gate. D1
loses on determinism (lazy `unknown`, the hidden `behind`) and it edits a
second protected script with no need. A1 loses because the policy already
names a review round as the remedy, and that path has no label race. Serial
rebasing (one PR per run) keeps CI cost and the PR timelines small (goals 4
and 6).

### Standing force-push authorization

Until a later record supersedes it, this record authorizes `/run-task` to
force-push a PR branch, without a new staff record, when all of these hold:

1. The branch is not `main`. It belongs to this repository, its PR is open and
   unmerged, and it is a flow branch (`<type>/…`, never `claude/…`).
2. The PR is otherwise ready: it is not a draft, it has no `needs-human`, the
   checks on its head H are not red, and it has an approval path (a body line
   `Verdict: APPROVE`, or an approval record for itself at H).
3. It is behind: `git merge-base --is-ancestor origin/main H` fails.
4. The only operation is `git rebase origin/main` of exactly H: no
   interactive rebase, no squash, no reword, no edit. If the rebase stops on a
   conflict, or a commit becomes empty or is dropped, run `git rebase --abort`,
   do not push, and ask the staff engineer (trigger `force-push`, reason
   `rebase conflict` with the files).
5. `git range-diff $(git merge-base origin/main H)..H origin/main..HEAD` is
   computed before the push.
6. The push is `git push --force-with-lease=<branch>:H origin <branch>`. Never
   a plain `--force`, never any other ref.
7. At most one rebase per run: the lowest-numbered PR that meets 1–3. The
   other PRs wait for a later run.

A range-diff with `!` and no conflict may still be pushed. The approval does
not carry over, though (Consequences, step 4).

## Consequences

The branch `chore/run-task-rebase-behind-prs` replaces `/run-task` step 0.2
with this procedure, applied to every open flow PR (task PRs and non-task PRs
of this repository):

1. **Red checks on H**: task PR → the existing fix path. Non-task PR → fix it
   on its branch the same way.
2. **Behind** (D2) and conditions 1–7 hold: rebase, compute the range-diff, and
   push with the lease. Then post a PR comment
   (`gh-rest.sh comment`) with one line:
   `Rebase: <H> -> <new head> onto <origin/main sha>; range-diff <n>/<n> identical`
   (or the `!` lines), followed by the range-diff. That comment is the audit
   trail. A `progress.md` line rides on the next commit to that branch, if one
   follows.
3. **Carry-over when the range-diff is all `=`, same commit count:**
   - task PR merging on `Verdict: APPROVE` (no staff record): run a new
     evaluator round on the rebased head, as steps 4–6 say (test-runner, a new
     `review-N.json`, an `.agents/`-only bookkeeping commit), then a normal
     push (no force). Log it as `· review-N · rebase ·`. A rebase round does
     not count toward the 3-round cap. Only a `request_changes` follows the
     usual path;
   - PR approved through a staff record (it carries its own
     `- PR: #n` / `Approved by staff engineer` record): wait in the same run
     until the Autonomy gate check run on the new head has completed
     (`gh-rest.sh checks`), then `gh-rest.sh label <pr> staff-approved`.
     Never re-add it over a red check.
4. **Range-diff with `!` or a different commit set:** no carry-over. A task PR
   on the evaluator path gets a new evaluator round (counted as a normal
   round). A staff-approved PR gets `approve-pr` for the new head, with the
   range-diff in `context`. Then commit, push, wait for the gate run, label.
5. **Stateless recovery (the run died partway):**
   - a task PR whose latest `review-N.json` head is not an ancestor of the PR
     head (a missing object counts as "not an ancestor"): new evaluator round,
     as in 3;
   - a PR with its own approval record, no `staff-approved`, green checks and
     a completed gate run on its head: `gh-rest.sh` cannot read PR comments,
     and after the force-push the old head is gone from a fresh clone, so the
     carry-over cannot be proven. Delegate `approve-pr` for the head (fail
     closed).
6. **Otherwise** (green or pending, up to date, approved or auto-merge armed):
   exit "waiting for PR #n", as today.
7. CI on the new head must be green before the merge. Auto-merge with strict
   checks already enforces that, and no step here bypasses it.
8. Do not add `mergeable_state` to `scripts/gh-rest.sh`; it stays as it is.
   In `AUTONOMY.md`, the bullet "force-pushing a branch other than `main`"
   (section "Approved by the staff engineer") may get one clause pointing at
   this record as the standing authorization for `/run-task` rebases. No
   other policy change.
9. The PR of this branch needs `staff-approved` through an `approve-pr`
   record (protected paths). List this record under "Decisions". No ADR:
   this applies ADR 0005, it does not change it.

## Protected paths

- `.claude/skills/run-task/SKILL.md`
- `AUTONOMY.md`
