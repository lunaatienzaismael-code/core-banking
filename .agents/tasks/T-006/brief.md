# T-006 — Skills reference UC1 code; evaluator verdict on the PR

- Use case: none (Phase 4 conventions) · Phase: 4 · Type: chore
- Issue: #15 · Branch: `chore/T-006-skills-reference-examples`
- Depends on: none

## Goal

The seed skills point at real UC1 code instead of empty placeholders, the
testing skill gains the rule the evaluator kept asking for, and every task PR
shows the evaluator verdict as a comment.

## Acceptance criteria

- **B1** — `java-hexagonal` "Reference examples" lists one real path per row of
  its placement table (domain record, use case + command/view, service, web
  controller + mapper + Problem Details handler, JPA entity + adapter + mapper,
  SQL read adapter), each with a one-line "why look here".
- **B2** — `java-testing` "Reference examples" lists one real test per test
  kind (domain unit, service with mocked ports, `@WebMvcTest`, adapter IT,
  HTTP end-to-end IT with fixture, ArchUnit), plus `LedgerFixture`.
- **B3** — `java-testing` gains a rule from the growth trigger (evaluator
  "testing" minors in T-001..T-004): every case a criterion enumerates (each
  named invalid value, each boundary) has its own test case, and every money
  edge (zero, overflow, `Long.MIN_VALUE` negation, currency mismatch) is
  tested at the layer that does the arithmetic.
- **B4** — `/next-task` step 5 posts the evaluator verdict and review path as a
  PR comment (`gh pr comment`) right after opening the PR; `CLAUDE.md`
  per-task flow mentions it.
- **B5** — Every path listed in the skills exists (checked with a one-off
  script before review; not a committed test).

## Scope

- In: `.claude/skills/java-hexagonal/SKILL.md`,
  `.claude/skills/java-testing/SKILL.md`, `.claude/skills/next-task/SKILL.md`,
  `CLAUDE.md`.
- Out: new skills (T-008), ArchUnit rules (T-007).

## Design notes

- Skills stay under ~100 lines; paths, not code excerpts.
- Carry the Phase 3 bookkeeping still uncommitted on `main` (tasks.json,
  progress.md, roadmap Phase 3 ticked) in this branch's chore commit.

## Definition of done

- [ ] Every criterion above has a check (B5 path script; build still green)
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
