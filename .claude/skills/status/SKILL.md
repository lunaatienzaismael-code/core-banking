---
name: status
description: Show a compact LedgerFlow status - phase, tasks by state, current branch, latest review, open PR checks.
disable-model-invocation: true
allowed-tools: Read, Bash(git status:*), Bash(git branch:*), Bash(git log:*), Bash(scripts/gh-rest.sh list-prs:*), Bash(scripts/gh-rest.sh checks:*), Bash(scripts/gh-rest.sh pr-state:*), Bash(scripts/gh-rest.sh list-issues:*), Bash(scripts/check-task-sync.sh), Bash(scripts/review-defects.sh)
---

# /status

Read-only. Change nothing. Answer in Spanish, in at most ~15 lines.

Gather:

1. `docs/roadmap.md` — first unchecked phase = current phase.
2. `.agents/tasks.json` — count by status; list `in_progress`, `in_review`,
   `blocked` (with reason) and the next ready `todo` (dependencies done).
3. `git status --short --branch` and `git log --oneline -3`.
4. For the active task: latest `.agents/tasks/<id>/review-*.json` verdict and
   counts; `logs/test-summary.json` status if present.
5. Open PRs: `scripts/gh-rest.sh list-prs open` and `scripts/gh-rest.sh checks <head_sha>`
   for each (REST; `gh pr` does not work in cloud sessions). Open questions:
   `scripts/gh-rest.sh list-issues open needs-human`.
6. Last 3 lines of `.agents/progress.md`.
7. `scripts/check-task-sync.sh` — tasks.json vs GitHub (title, milestone,
   state, `Depends on`).
8. `scripts/review-defects.sh` — defect types seen in 2+ tasks without a rule.

Output: a short header line (phase, branch), a task table, then one line with
the suggested next action (e.g. "`/run-task`", "merge PR #12" or "answer #76").
If the sync check reports drift, add a "Discrepancias" line per item (task,
field, both values) and say which side owns the field; do not fix it here.
If the defects report has `alerts`, add a "Regla de crecimiento" line per
defect type with its tasks.
