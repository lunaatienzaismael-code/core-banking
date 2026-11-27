# 0006 — Rebase PR #96 onto main with a lease-guarded force-push

- Date: 2026-11-27
- Trigger: force-push
- Task: T-036
- Branch: feat/T-036-risk-amount-rule
- PR: #96
- Status: Decided by staff engineer

## Question

Branch protection on `main` requires PRs to be up to date (strict status
checks), linear history and rebase merge (ADR 0005). PR #96 was approved in
record 0004 (on the branch, approved head `9c398d8`; the branch head is now
`b1d5efb`, which adds only that record). `main` then moved with PR #95
(`.agents/`, `.claude/`, `.github/`, `AUTONOMY.md`, `CLAUDE.md`, `scripts/`;
no `risk-service/` and no POM change). Auto-merge is on but the PR is
"behind". How should the branch be brought up to date?

Checked read-only: `git merge-tree --write-tree origin/main
origin/feat/T-036-risk-amount-rule` is clean, and the two sides change
disjoint files (the branch: `risk-service/**`, `.agents/STATE.md`,
`.agents/progress.md`, `.agents/tasks.json`, `.agents/tasks/T-036/**`,
`.agents/decisions/0004-*`; PR #95 touches none of these).

## Options

| Option | Pros | Cons | Reversibility |
|---|---|---|---|
| (a) Local `git rebase origin/main`, `git push --force-with-lease=feat/T-036-risk-amount-rule:b1d5efb`, verify with `git range-diff`, re-add `staff-approved` | Keeps the branch linear, matching the rebase-merge and linear-history rules; the replay is verifiable locally before the push; the lease pinned to `b1d5efb` refuses to overwrite anything pushed since | Rewrites unmerged history (needs this record); the `synchronize` drops the label, so it must be re-applied; CI reruns | High: `b1d5efb` stays reachable (reflog, the PR's timeline) and can be pushed back |
| (b) REST `PUT /pulls/96/update-branch` (merge commit) | No local force-push; one API call | Puts a merge commit into the PR branch: the PR history stops being linear, against ADR 0005's readable-history goal; rebase merge then has to drop or replay around the merge, which GitHub can refuse or produce surprising results with; the reviewed commits are no longer the sole content | Medium: a later rebase removes the merge, but that is a force-push anyway |
| (c) GitHub's "update with rebase" (GraphQL `updatePullRequestBranch`, `updateMethod: REBASE`) | Linear; GitHub does the replay | Still a force-push, just by GitHub; no lease on a SHA the agent verified, and no local range-diff before the head moves; less deterministic for the record | High, same as (a) |
| (d) Wait for the owner / do nothing | No history rewrite | Auto-merge never fires under strict checks; the flow stalls on a mechanical step | n/a |

## How banks and production ledgers do it

Regulated engineering shops (and the SOX/DORA-style change-management they
answer to) want the change that reaches the main line to be exactly the change
that was approved, with a linear, auditable trail: "four eyes on what ships".
Merge-queue setups (GitHub merge queue, Bors, Gerrit's rebase-on-submit)
rebase the approved change onto the current tip and re-run CI, treating the
approval as valid as long as the patch content is unchanged; any content change
on rebase (conflict resolution) invalidates the approval. Option (a) with a
range-diff check is the manual equivalent of that: same patches, new base,
fresh CI.

## Decision

Option (a). It does not touch money logic or the invariants (goal 1): the
patches stay byte-identical, only their parent changes. It matches what
production change control does (goal 2) and keeps the linear-history,
rebase-merge rule of ADR 0005 that option (b) would break. It is the most
verifiable path (goal 4): the lease pins the exact head that was reviewed plus
record 0004, and `git range-diff` proves the replay is a no-op on content
before anyone relies on it. It is fully reversible (goal 5). Option (c) is the
same history result with less verification, so it loses on reviewability.

This is a non-`main` branch with unmerged history, the only case where the
policy allows a force-push, and no forward fix exists: strict checks can only
be satisfied by moving the base.

## Consequences

The caller, in this order:

1. `git fetch origin`; check out `feat/T-036-risk-amount-rule` at exactly
   `b1d5efb`; `git rebase origin/main`. If the rebase stops on any conflict,
   abort (`git rebase --abort`), do not push, and request a new decision:
   this record covers only a conflict-free replay.
2. Before pushing, run
   `git range-diff $(git merge-base origin/main b1d5efb)..b1d5efb origin/main..HEAD`.
   Every one of the six commits must show `=` (identical patch). Record the
   output in `.agents/progress.md` with the new head SHA.
3. Push with `git push --force-with-lease=feat/T-036-risk-amount-rule:b1d5efb origin feat/T-036-risk-amount-rule`.
   No plain `--force`, no other branch.
4. **Approval carry-over:** record 0004 carries over to the rebased head only
   if the range-diff in step 2 is empty of content changes (all `=`). Then
   re-add `staff-approved`; the gate finds record 0004 at the new head (it is
   one of the replayed commits). If any commit shows `!` or the commit set
   differs, do not re-add the label: request a new `approve-pr` for the new
   head.
5. CI must run on the new head and be green before the merge; never re-add
   the label over a red check. Auto-merge (rebase) stays enabled.
6. If `main` moves again before the merge, the same procedure applies under
   this record, with the lease pinned to the then-current rebased head and the
   same range-diff condition.
7. No ADR: this applies ADR 0005, it does not change it.
