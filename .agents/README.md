# `.agents/` — file-based agent state

Shared state between the orchestrator (main session) and its subagents. Agents
communicate through these files, not through chat history.

| Path | Writer | Purpose |
|------|--------|---------|
| `tasks.json` | orchestrator only | Task list ([schema](templates/tasks.schema.json)). Edited field by field, never regenerated. |
| `progress.md` | orchestrator only | Append-only log, one line per event. |
| `templates/` | humans | Contracts: `brief.md`, `tasks.schema.json`, `review.schema.json`. |
| `tasks/<id>/brief.md` | orchestrator (`/plan-feature`) | What the task delivers and how it is accepted. |
| `tasks/<id>/review-<n>.json` | `evaluator` | One file per review round ([schema](templates/review.schema.json)); never overwritten. |
| `tasks/<id>/logs/` | `test-runner` | Maven logs and `test-summary.json`. Gitignored. |

Rules:

- Subagents write only inside `tasks/<id>/` and return a condensed result plus
  file paths; the orchestrator updates `tasks.json` and `progress.md`.
- Task ids (`T-001`, …) are stable and never reused.
- Status flow: `todo → in_progress → in_review → done`, or `blocked` with a
  `blocked_reason`.
- Source of truth: `tasks.json` owns working state, `depends_on` and
  `acceptance_criteria`; GitHub owns `done` (merged PR). Each issue mirrors
  its task: title `T-xxx — <title>`, the phase milestone, open/closed state and
  a `Depends on: …` footer. `scripts/check-task-sync.sh` compares both sides
  and prints JSON; fix drift on the side that does not own the field.
- The test summary format is the contract in the header of
  `scripts/test-summary.sh`.
