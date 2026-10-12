# ADR 0008: OpenAPI-First Tooling

- Status: Accepted
- Date: 2026-10-12
- Deciders: Project owner
- Refines: [ADR 0006](0006-api-and-error-model.md) (whose tooling was deferred to
  the first real endpoint)

## Context

ADR 0006 makes the OpenAPI contract the source of truth for each service's HTTP
API and requires that the implementation cannot silently diverge from it. It
left the tooling open. Phase 3 introduces the first business endpoints, so the
mechanism must be fixed now and be reusable by every later service.

## Options Considered

- **Generate server interfaces from the contract** (openapi-generator, `spring`
  generator, `interfaceOnly=true`). Interfaces and DTOs are generated at build
  time; hand-written controllers implement them. A controller whose paths,
  parameters or payload types differ from the contract does not compile.
  Cost: generated DTOs, and a generator whose options must track Spring Boot.
- **Hand-written controllers validated in tests** (e.g. a request/response
  validator in integration tests). Full freedom over code, but divergence is
  only caught for the paths and cases the tests exercise, at test time.
- **Both.** Strongest guarantee, more moving parts than the project needs now.

## Decision

- **openapi-generator** (Maven plugin, version pinned in the parent POM) with
  the **`spring` generator in `interfaceOnly` mode**, Spring Boot 4 and
  Jackson 3 options, bean validation on, no default method bodies
  (`skipDefaultInterface`), no documentation annotations, no nullable wrapper
  library.
- Contract per service at `<service>/src/main/openapi/<service>-api.yaml`
  (OpenAPI 3.1). Generated code goes to `target/generated-sources` and is
  never committed.
- Generated packages live inside the inbound web adapter:
  `<base>.adapter.in.web.api` (interfaces) and `<base>.adapter.in.web.model`
  (DTOs). These two packages are reserved for generated code; hand-written web
  classes (controllers, mappers, error handler) sit in `adapter.in.web`.
  The hexagonal ArchUnit rules therefore apply to generated code unchanged.
- Generator configuration lives in the parent POM's `pluginManagement`; a
  service module only declares its contract file and packages.
- Error responses are described in the contract with a `Problem` schema
  (RFC 9457, `application/problem+json`); at runtime they are produced by
  Spring's `ProblemDetail` in a single exception handler (ADR 0006).
- Generated code is excluded from formatting (Spotless) and coverage (JaCoCo).

## Consequences

- Contract drift is a compile error, not a review comment.
- Web DTOs come from the contract; mapping to domain types stays explicit and
  hand-written (ADR 0002 rule 7).
- Upgrading Spring Boot may require bumping the generator and revisiting its
  options; this is a known, contained cost.
- Contract linting and breaking-change detection in CI are not part of this
  decision; they can be added later without changing it.
