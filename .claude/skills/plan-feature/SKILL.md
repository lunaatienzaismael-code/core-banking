---
name: plan-feature
description: Plan a LedgerFlow use case into tasks with briefs, tasks.json entries and GitHub issues.
argument-hint: <use case, e.g. UC1>
disable-model-invocation: true
---

# /plan-feature $ARGUMENTS

Plan use case **$ARGUMENTS** following the `task-planning` skill. Talk to the
user in Spanish; everything written to the repo is in English.

## 1. Understand

- Read the `$ARGUMENTS` section of `docs/spec.md`, the ADRs it depends on,
  `docs/roadmap.md` (which phase it belongs to) and `.agents/tasks.json`
  (existing tasks and the next free id).
- If a decision is missing (e.g. a deferred ADR this use case needs), write
  it as an ADR per `AUTONOMY.md`: reversible ones `Proposed by agent`; one
  from the "stop the run" list stops planning and goes to the owner.

## 2. Propose

Build a compact table: id, type, title, ACs covered, depends on, rough size.
Check every AC of the use case is covered exactly once. The table goes in the
plan PR's body. The owner's merge of that PR approves the plan and the phase
(`AUTONOMY.md`, "End of phase"); there is no OK to wait for in the session.

## 3. Record

For each approved task:

1. Create `.agents/tasks/<id>/brief.md` from `.agents/templates/brief.md`.
2. Append the task to `.agents/tasks.json` (edit in place, do not regenerate the
   file; keep existing entries untouched).

Then append one line to `.agents/progress.md`:
`<date> · phase <n> · planned · $ARGUMENTS → T-xxx..T-yyy`.

Open the plan PR (ADRs, briefs, `tasks.json`, `progress.md`) labelled
`needs-human`, and stop.

## 4. GitHub (after the plan PR is merged)

The first run after the owner merges the plan PR creates the GitHub objects,
through REST:

- Ensure the phase milestone exists (`gh api repos/:owner/:repo/milestones`).
- Create one issue per task: title `T-xxx — <title>`, milestone = the phase,
  body = the brief's goal, the AC ids it covers, and a footer line
  "Brief: <brief path> · Depends on: <ids or none>".
  Do not copy criteria, scope or notes: the brief is the source of truth, and
  a copy drifts silently when the brief changes.
- Write each issue number into the task's `issue` field and the brief header.

Finish with a two-line summary: tasks created and the suggested first `/next-task`.
