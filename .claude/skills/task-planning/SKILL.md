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

<!-- Filled in Phase 4 with the UC1 task breakdown. -->
