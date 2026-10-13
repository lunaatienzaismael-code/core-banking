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
| `growth-rules.json` | orchestrator only | Defect types already turned into a rule, and where; read by `scripts/review-defects.sh`. |
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

## Defect types

Each review finding has a `defect_type` (closed list in the review schema). It
names *what went wrong*, independently of the area in `category`, so the growth
rule can count repeats across tasks (`scripts/review-defects.sh`).

| `defect_type` | Meaning |
|---------------|---------|
| `untested-enumerated-case` | A case the criterion names (value, boundary) has no test of its own. |
| `money-edge-wrong-layer` | A money edge (zero, overflow, `Long.MIN_VALUE`, currency) is not tested where the arithmetic happens. |
| `indirect-test` | The test asserts a proxy (generated model, a lower layer) instead of the real boundary or persisted effect. |
| `weak-assertion` | The test runs the code but asserts too little to fail when the behaviour breaks. |
| `missing-invariant-test` | A financial invariant (ADR 0003) touched by the change has no named test. |
| `unmapped-error` | An error can leave the API as something other than Problem Details (e.g. a generic 500). |
| `contract-gap` | The OpenAPI contract is incomplete or inconsistent with the behaviour. |
| `layering-violation` | A hexagonal rule ArchUnit does not catch (placement, mapper, leaking types). |
| `concurrency-gap` | Lost update, race or check-then-act not handled or not tested. |
| `unbacked-doc-claim` | A doc, skill or ADR states something the code or history does not back. |
| `test-data-gap` | Fixtures or test data cannot express a case that is or will be needed. |
| `naming-convention` | Names, test names or traceability (`@DisplayName("ACx.y …")`) off convention. |
| `scope-creep` | Change beyond the brief, or speculative code. |
| `other` | None fits; the description says why. |

The `defect_type` of the 13 findings in T-001..T-009 was added on 2026-10-13
when the field was introduced; nothing else in those files changed.
