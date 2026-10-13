---
name: task-planning
description: How to split a LedgerFlow use case into tasks (one task = one branch = one PR) and write each task's brief. Use when planning a feature or use case, or when a task turns out too big.
---

# Planning tasks for LedgerFlow

Source of truth: `docs/spec.md` (use cases and acceptance criteria), ADR 0005
(one task = one branch = one PR), `.agents/templates/`.

## Splitting a use case

1. Start from the use case's acceptance criteria. Each AC must end up in
   exactly one task (a task may deliver several).
2. **Slice vertically by behaviour**, not by layer: a task delivers something
   observable and testable (domain rule + its port + adapter + tests), not
   "all the entities" then "all the controllers". The exception is the first
   task of a use case, which may lay down the domain model alone if it is big.
3. **Size:** reviewable in one sitting — aim for ≤ ~400 changed lines
   (excluding generated code), ≤ ~5 commits. Too big → split by AC or by
   happy path / error paths.
4. **Order by dependency**; record it in `depends_on`. Prefer orders that keep
   `main` releasable after every PR.
5. Enabling work (migration, config, contract) is its own task only when it is
   shared by several later tasks; otherwise it goes with the first task that
   needs it.
6. Each task gets brief-local criteria (`B1`, `B2`…) for anything it must do
   that the spec does not state (e.g. a Liquibase changeset, an OpenAPI path).
7. Open questions that need a decision are surfaced to the user before
   planning, not hidden in a brief. A significant decision becomes an ADR task.

## Writing the brief

Copy `.agents/templates/brief.md` to `.agents/tasks/<id>/brief.md` and fill:

- **Goal** in one or two sentences, observable.
- **Acceptance criteria** copied from the spec (Given/When/Then), each
  provable by a test; plus `B*` criteria.
- **Scope In/Out** so the evaluator can flag scope creep.
- **Design notes** only for what skills/ADRs do not cover: port and use case
  names, error cases, edge cases that deserve a named test.

## Recording the task

Append to `.agents/tasks.json` (schema `.agents/templates/tasks.schema.json`):
next free id `T-NNN`, `status: "todo"`, `issue/branch/pr: null`. Branch name
`<type>/<id>-<slug>`, slug ≤ 5 words, kebab-case.

## Reference examples

UC1 (Phase 3), five tasks, each approved in one review round:

| Task | Type | Delivers | ACs | Depends on | PR | Lines: prod / test / contract+db / docs |
|------|------|----------|-----|------------|----|------------------------------------------|
| T-001 | build | ADR 0008, openapi-generator, initial contract | — | — | #10 | 0 / 44 / 114 / 405 |
| T-002 | feat | Domain model alone (`Money`, `Account`, `PostingLine`, normal side) | — | — | #11 | 276 / 274 / 0 / 30 |
| T-003 | feat | Open an account end to end (first vertical slice) | AC1.1 | T-001, T-002 | #12 | 323 / 325 / 171 / 36 |
| T-004 | feat | Query the derived balance | AC1.2, AC1.4 | T-003 | #13 | 174 / 419 / 124 / 33 |
| T-005 | feat | Query movements | AC1.3 | T-004 | #14 | 168 / 340 / 59 / 38 |

What it shows:

- Rules 2 and 5: tooling shared by every later task (T-001, rule 5) and a big
  domain model (T-002, rule 2's exception) are their own tasks; everything
  else is a vertical slice.
- Rule 1, with one wrinkle: AC1.4 (unknown account) is recorded on T-004,
  the first endpoint that could return it, but applies to every account
  endpoint, so T-005 had to repeat it for movements as brief-local B1. An AC
  that spans several endpoints is better split into per-endpoint B criteria
  when planning.
- Rule 3 in practice: production code stayed within ~330 lines per PR, but
  with tests, contract and bookkeeping the PRs were 563–855 lines; tests were
  roughly as large as the code they prove.
- Briefs: `.agents/tasks/T-001/brief.md` … `.agents/tasks/T-005/brief.md`;
  UC2's plan (T-010..T-016) follows the same shape.
