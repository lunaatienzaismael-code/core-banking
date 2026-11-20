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
do not work in cloud sessions. Never merge, push to `main`, force-push or tag.

## 0. Lock and reconcile

1. `git fetch origin --prune --tags`. The work starts from `origin/main`
   unless step 0.3 resumes a branch.
2. `scripts/gh-rest.sh list-prs open`. If a task PR is open (head
   `<type>/T-xxx-…`):
   - checks on its `head_sha` (`gh-rest.sh checks`) red → switch to its
     branch and fix it (step 3 on). That is fix attempt 1 or 2. After 2
     attempts, stop (step 8);
   - checks green or pending → exit with "waiting for PR #n". Do nothing
     else.
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

## 1. Select

- `$ARGUMENTS` if given; otherwise the first `todo` task of the current
  phase (first unchecked phase in `docs/roadmap.md`) whose `depends_on` are
  all `done`.
- None ready, but `todo` tasks wait on unmerged work → exit "waiting".
- The phase has no planned task for its use case → run `/plan-feature UCn`.
  It ends with a plan PR labelled `needs-human`. Stop.
- Every task of the phase is `done` → end of phase (`AUTONOMY.md`): a
  phase-close PR (roadmap tick, `STATE.md`) and the next phase's plan PR
  (`/plan-feature`), both labelled `needs-human`. Stop.
- Read the task's `brief.md`, including `Protected paths:` if present.

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
  check that the brief declares it. If it does not, stop (step 8).
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
- `CHANGES` → `scripts/gh-rest.sh label <pr> needs-human`.
- Set `pr` in `tasks.json`, add it to the bookkeeping commit (amend it if
  not pushed yet, otherwise a new `.agents/`-only commit), push.
- `scripts/gh-rest.sh unlabel <issue> agent:running`. Report the PR link.

## 8. Stop

On any stop condition in `AUTONOMY.md`:

- `scripts/gh-rest.sh comment <issue> --body-file <file>` with the reason,
  what was done, and the exact question for the owner;
- `scripts/gh-rest.sh label <issue> needs-human`, then
  `scripts/gh-rest.sh unlabel <issue> agent:running`;
- if the branch has commits, push it and open a draft PR so the work is not
  lost;
- regenerate `STATE.md` and commit it on the branch, if there is one.
