---
name: run-task
description: Run the next LedgerFlow task end to end without stopping - reconcile, pick, branch, implement, test-runner, evaluator, bookkeeping, PR through REST - under AUTONOMY.md. Use when a routine or the user asks to run, continue or resume the task flow.
argument-hint: "[task id, e.g. T-034]"
---

# /run-task $ARGUMENTS

One run = at most one task. You are the orchestrator: the only writer of
`.agents/tasks.json` and `.agents/progress.md`. `AUTONOMY.md` is the policy;
this skill is the procedure. Talk to the user in Spanish when one is present;
everything written to the repo or to GitHub is in English.

GitHub only through `scripts/gh-rest.sh` (REST). `gh pr …` and `gh issue …`
do not work in cloud sessions. Never merge or push to `main`; tags and
force-pushes only with a staff-engineer record (`AUTONOMY.md`).

## 0. Lock and reconcile

1. `git fetch origin --prune --tags`. The work starts from `origin/main`
   unless step 0.3 resumes a branch.
2. `scripts/gh-rest.sh list-prs open`. For each open flow PR (head
   `<type>/…` of this repository, never `claude/…`; task or non-task), with
   `H` its `head_sha`, take the first case that applies
   ([record 0008](../../../.agents/decisions/0008-run-task-rebases-behind-prs.md)):
   - **Red** checks on `H` (`gh-rest.sh checks`) → switch to its branch and
     fix it (step 3 on). That is fix attempt 1 or 2. After 2 attempts, ask
     the staff engineer (trigger `ci-red`, step 3).
   - **Behind**: `git merge-base --is-ancestor origin/main H` fails, the PR
     is not a draft, has no `needs-human`, and has an approval path (a body
     line `Verdict: APPROVE`, or its own approval record at `H`). At most
     one rebase per run: the lowest-numbered such PR; the others wait.
     1. `git switch -C <branch> H`, `git rebase origin/main`. Nothing else:
        no interactive rebase, squash, reword or edit. A conflict, or a
        commit that becomes empty or is dropped → `git rebase --abort`, do
        not push, ask the staff engineer (trigger `force-push`, reason
        `rebase conflict` with the files).
     2. `git range-diff $(git merge-base origin/main H)..H origin/main..HEAD`.
     3. `git push --force-with-lease=<branch>:H origin <branch>`. Never a
        plain `--force`, never another ref.
     4. `gh-rest.sh comment <pr>`: one line `Rebase: <H> -> <new head> onto
        <origin/main sha>; range-diff <n>/<n> identical` (or the `!` lines),
        then the range-diff. That comment is the audit trail.
     5. Approval:
        - all `=`, same commit count, task PR on `Verdict: APPROVE` → a new
          evaluator round on the new head (steps 4–6: test-runner, a new
          `review-N.json`, an `.agents/`-only bookkeeping commit, a normal
          push). Log it as `· review-N · rebase ·`; it does not count toward
          the 3-round cap;
        - all `=`, same commit count, PR with its own staff approval record
          → wait in this run until the Autonomy gate check run on the new
          head has completed, then `gh-rest.sh label <pr> staff-approved`.
          Never over a red check;
        - any `!` or a different commit set → no carry-over: a task PR gets a
          normal evaluator round; a staff-approved PR gets `approve-pr` for
          the new head with the range-diff in `context`, then commit, push,
          wait for the gate run, label.
   - **Recovery** (a run died partway):
     - a task PR whose latest `review-N.json` head is not an ancestor of `H`
       (a missing object counts as not an ancestor) → a rebase evaluator
       round, as above;
     - a PR with its own approval record, no `staff-approved`, green checks
       and a completed gate run on `H` → `approve-pr` for `H` (the
       carry-over cannot be proven any more: fail closed).
   - **Otherwise** (green or pending, up to date, approved or auto-merge
     armed) → exit with "waiting for PR #n". Do nothing else.

   CI on the new head must be green before the merge; auto-merge with strict
   checks enforces it and no step here bypasses it.
3. `scripts/gh-rest.sh list-issues open agent:running`. An issue with that
   label and no open PR is a run that died. If its task branch exists on
   `origin`, switch to it and resume at the first step not done yet (commits
   on the branch, `logs/`, `review-*.json`). Otherwise remove the label and go
   on.
4. Mark merged tasks done: for every `in_review` task, `gh-rest.sh pr-state
   <pr>`. When it is `MERGED`, set `status: "done"` and append
   `<date> · <id> · done · PR #<pr> merged`. These edits ride on this run's
   branch (step 6), never on `main`.
5. Tasks with `issue: null` in a phase whose plan PR is merged: create the
   milestone and the issues as `/plan-feature` step 4 says, through
   `gh-rest.sh create-milestone` / `create-issue`, and write the numbers
   into `tasks.json` and the briefs. These edits ride on this run's branch
   too.
6. A merged phase-close PR whose milestone is still open: delegate to
   **staff-engineer** with `trigger: release`. On approve, commit its record
   on this run's branch, then `scripts/gh-rest.sh close-milestone <n>`,
   `git tag -a v0.X.0 -m "Phase X" origin/main` and
   `git push origin v0.X.0`.

## 1. Select

- `$ARGUMENTS` if given; otherwise the first `todo` task of the current
  phase (first unchecked phase in `docs/roadmap.md`) whose `depends_on` are
  all `done`.
- None ready, but `todo` tasks wait on unmerged work → exit "waiting".
- The phase has no task with its `use_case` set (enabling tasks such as
  T-033 or T-034 have `use_case: null` and do not count) → run
  `/plan-feature UCn`. It ends with a plan PR the staff engineer approves
  (step 7). Stop.
  Check this before the next bullet.
- Every task of the phase is `done`, its use case included → end of phase (`AUTONOMY.md`): a
  phase-close PR (roadmap tick, `STATE.md`) and the next phase's plan PR
  (`/plan-feature`), both approved by the staff engineer (step 7). Stop.
- Read the task's `brief.md`, including its `## Protected paths` section if
  present.

## 2. Start

- `scripts/gh-rest.sh label <issue> agent:running`.
- `git switch -c <type>/<id>-<slug> origin/main`. This is the only branch
  you push. Never a `claude/…` branch.
- In `tasks.json`, set `branch`, `status: "in_progress"`. Append `· started ·`
  to `progress.md`.

## 3. Implement

- Apply the skills the change touches (`java-hexagonal`, `java-testing`,
  `persistence-postgres-liquibase`, `spring-boot-conventions`,
  `kafka-events`). Write the test for each criterion first where practical.
- Stay inside the brief. Before editing a protected path (`AUTONOMY.md`),
  check that the brief declares it. If it does not, ask the staff engineer
  (below).
- **Judgment calls** (`AUTONOMY.md`, "Owner-level decisions"): an
  owner-level decision, a wrong brief, a task too big, an undeclared
  protected path, `review-exhausted`, `ci-red`. Delegate to **staff-engineer** with `question`, `trigger`,
  `options` and `context`. Apply its decision: edit the brief, write the
  ADR (`Decided by staff engineer`) or split the task as its record says,
  and commit the record with that change. `escalate` → stop (step 8).
  Every record of this run goes in the PR body under "Decisions".
- Commit as you go: atomic Conventional Commits, each one compiling and
  passing its tests. Review evidence is bound to commits, not to the work
  tree.

## 4. Test

- Delegate to **test-runner** with the task id, on a clean tree (everything
  outside `.agents/` committed). Fix until it returns `"status":"pass"`,
  committing each fix. Read the log or `verbatim` only when the failures
  list is not enough.

## 5. Review

- Delegate to **evaluator** with the task id and round `review_rounds + 1`;
  then increment `review_rounds`.
- On `request_changes`: fix blockers and majors (minors when cheap), commit,
  re-run step 4, review again. Max 3 rounds.
- After each review run `scripts/review-defects.sh`. For each entry in
  `alerts`, add the rule it calls for (skill rule, ArchUnit test or evaluator
  check) when that path is not protected, and record it in
  `.agents/growth-rules.json`. If the rule needs a protected path, name it in
  the PR body instead.

## 6. Bookkeeping commit

One last `chore(agents)` commit, touching `.agents/` only: `tasks.json`
(`pr` is set in step 7, `status: "in_review"`), `progress.md`
(`· review-N · …` and `· in_review ·` lines), the `review-N.json` files, and
`STATE.md` regenerated with `scripts/state.sh`. Commits after the reviewed
SHA may touch only `.agents/`.

## 7. Publish

- `git push -u origin <branch>`.
- Write the PR body from `.github/pull_request_template.md`: `Closes #<issue>`,
  the verdict and review path, and one line exactly `Verdict: APPROVE`, or
  `Verdict: CHANGES` after 3 rounds without approval.
- `scripts/gh-rest.sh open-pr --head <branch> --title "<conventional title>"
  --body-file <file> --milestone <n>`, with `--draft` when the verdict is
  `CHANGES`.
- `scripts/gh-rest.sh comment <pr> --body-file <file>`: the evaluator verdict
  (counts by severity) and the latest `review-N.json` path.
- `CHANGES` → ask the staff engineer (trigger `review-exhausted`, step 3).
- **Approval** — a PR that touches a protected path, a plan or phase-close
  PR, or any non-task PR: delegate to **staff-engineer** with
  `trigger: approve-pr`, the PR number and its head SHA. On `approve`, commit
  its record (`.agents/` only), push, and only then
  `scripts/gh-rest.sh label <pr> staff-approved`: every push removes the
  label. The gate merges. On `reject`, fix and ask again.
- Set `pr` in `tasks.json`, add it to the bookkeeping commit (amend it if
  not pushed yet, otherwise a new `.agents/`-only commit), push.
- `scripts/gh-rest.sh unlabel <issue> agent:running`. Report the PR link.

## 8. Stop

On any stop condition in `AUTONOMY.md` (a judgment call is not one: step 3):

- `scripts/gh-rest.sh comment <issue> --body-file <file>` with the reason,
  what was done, and the exact question for the owner;
- `scripts/gh-rest.sh label <issue> needs-human`, then
  `scripts/gh-rest.sh unlabel <issue> agent:running`;
- if the branch has commits, push it and open a draft PR so the work is not
  lost;
- regenerate `STATE.md` and commit it on the branch, if there is one.
