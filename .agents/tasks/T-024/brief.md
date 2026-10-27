# T-024 — spring-boot-conventions skill

- Use case: none · Phase: 7 · Type: chore
- Issue: #54 · Branch: `chore/T-024-spring-boot-conventions`
- Depends on: none

## Goal

The Spring Boot conventions both services already share are written down as a
project skill, so the Kafka adapters of Phase 7 follow them instead of
inventing new ones. Trigger from `CLAUDE.md` "Growth": two inbound adapters
and stable config/error conventions.

## Acceptance criteria

- **B1** — `.claude/skills/spring-boot-conventions/SKILL.md` with frontmatter
  (name, description saying when to use it) covering only conventions visible
  in the code today: `application.yaml` layout and profiles, `config/` package
  (e.g. `ClockConfiguration`), controllers implementing generated OpenAPI
  interfaces, web mappers, `ProblemDetailsHandler` mapping (domain exception →
  status, the 500 fallback, `ConstraintViolationException`), constructor
  injection, transaction boundary at the use case.
- **B2** — Each rule points to a real file in "Reference examples"
  (ledger and payment); no rule that the code does not already follow.
- **B3** — `CLAUDE.md` lists the skill under Skills and drops it from
  "Growth"; `java-hexagonal` links to it where they meet, without repeating
  content.

## Scope

- In: the new skill, `CLAUDE.md`, a cross-link in `java-hexagonal`.
- Out: code changes; the ledger `ConstraintViolationException` → `400` gap
  (recorded as known divergence, not fixed here).

## Design notes

- Keep it short (like `persistence-postgres-liquibase`); ADRs are linked, not
  repeated.
- Where the two services differ, say so and name which one is the target.

## Definition of done

- [ ] Every criterion above is visible in the skill and `CLAUDE.md`
- [ ] Every referenced path exists
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
