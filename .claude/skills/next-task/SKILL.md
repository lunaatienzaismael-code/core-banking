---
name: next-task
description: Pick up the next LedgerFlow task and run it through branch, implementation, test-runner, evaluator and PR.
argument-hint: "[task id, e.g. T-003]"
disable-model-invocation: true
---

# /next-task $ARGUMENTS

Run one task end to end. You are the orchestrator: the only writer of
`.agents/tasks.json` and `.agents/progress.md`. Talk to the user in Spanish.

## 0. Reconcile

- For every task in `in_review`, check its PR (`gh pr view <pr> --json state`).
  If merged: set `status: "done"`, append `<date> · <id> · done · PR #<pr> merged`.
- Working tree must be clean; `git switch main && git pull --ff-only`.

## 1. Select

- `$ARGUMENTS` if given; otherwise the first `todo` task whose `depends_on` are
  all `done`. If an `in_progress` task exists, resume it instead and say so.
- Read its `brief.md`. Summarise goal and criteria to the user in 3–5 lines.

## 2. Start

- Branch `<type>/<id>-<slug>` from `main`; set `branch` and
  `status: "in_progress"` in `tasks.json`; append `· started ·` to progress.

## 3. Implement

- Apply `java-hexagonal` and `java-testing`. Write the test for each criterion
  first where practical. Stay inside the brief's scope; if the brief is wrong
  or too big, stop and tell the user.
- After each meaningful step, delegate to the **test-runner** subagent with the
  task id. Fix until it returns `"status":"pass"`. Read the log path it returns
  only when the failures list is not enough.

## 4. Review

- Delegate to the **evaluator** subagent with the task id and the next round
  number (`review_rounds + 1`); then increment `review_rounds`.
- On `request_changes`: read the review file, fix blockers and majors (minors
  when cheap), re-run test-runner, review again. After round 3 without
  approval, stop and ask the user.
- If the same defect type was already flagged in another task, propose a skill
  rule or an ArchUnit test (growth rule in `CLAUDE.md`).

## 5. Propose and publish (only with explicit OK)

- Propose atomic Conventional Commits (message + files each); every commit must
  compile and pass tests. Wait for the user's OK before committing.
- On OK to publish: push, open a PR with `.github/pull_request_template.md`,
  `Closes #<issue>`, milestone of the phase, and the evaluator verdict + review
  path. Right after opening it, post the evaluator verdict (counts by
  severity) and the latest `review-N.json` path as a PR comment with
  `gh pr comment <pr> --body …`. Set `pr` and `status: "in_review"`; append
  `· in_review · PR #<pr>`.
- Never merge; the user merges. Step 0 marks it done next time.
