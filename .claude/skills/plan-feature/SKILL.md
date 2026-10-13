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
- If a decision is missing (e.g. a deferred ADR this use case needs), stop and
  ask the user before planning.

## 2. Propose (no files written yet)

Show the user a compact table: id, type, title, ACs covered, depends on, rough
size. Note every AC of the use case is covered exactly once. Wait for approval
or changes.

## 3. Record (after the user's OK)

For each approved task:

1. Create `.agents/tasks/<id>/brief.md` from `.agents/templates/brief.md`.
2. Append the task to `.agents/tasks.json` (edit in place, do not regenerate the
   file; keep existing entries untouched).

Then append one line to `.agents/progress.md`:
`<date> · phase <n> · planned · $ARGUMENTS → T-xxx..T-yyy`.

## 4. GitHub (ask first)

Ask the user before creating anything on GitHub. On OK:

- Ensure the phase milestone exists (`gh api repos/:owner/:repo/milestones`).
- Create one issue per task: title `T-xxx — <title>`, milestone = the phase,
  body = the brief's goal, the AC ids it covers, and a footer line
  "Brief: <brief path> · Depends on: <ids or none>".
  Do not copy criteria, scope or notes: the brief is the source of truth, and
  a copy drifts silently when the brief changes.
- Write each issue number into the task's `issue` field and the brief header.

Finish with a two-line summary: tasks created and the suggested first `/next-task`.
