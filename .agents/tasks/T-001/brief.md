# T-001 — ADR 0008 and openapi-generator with initial contract

- Use case: none (enabling work for UC1) · Phase: 3 · Type: build
- Issue: #5 · Branch: `build/T-001-openapi-generator`
- Depends on: none

## Goal

The OpenAPI-first tooling of ADR 0006 is decided and wired: the ledger contract
lives in the repo, and the build generates Spring interfaces and DTOs from it,
so controllers that diverge from the contract do not compile.

## Acceptance criteria

- **B1** — ADR 0008 records the tooling decision (openapi-generator, generator
  `spring`, `interfaceOnly=true`; options considered: generated interfaces vs
  test-time validation vs both) and is listed in `docs/adr/README.md`.
- **B2** — `ledger-service/src/main/openapi/ledger-api.yaml` (OpenAPI 3.1) exists
  with `info`, server placeholder and a reusable `Problem` schema
  (RFC 9457: `type`, `title`, `status`, `detail`, `instance`) and
  `application/problem+json` error response components.
- **B3** — `./mvnw -B verify` generates sources from the contract into
  `target/generated-sources`, compiles them, and stays green (Spotless, ArchUnit,
  JaCoCo untouched by generated code).
- **B4** — Generated code lives under
  `com.ledgerflow.ledger.adapter.in.web.api` (interfaces) and
  `com.ledgerflow.ledger.adapter.in.web.model` (DTOs), so ArchUnit keeps it in
  the inbound web adapter.

## Scope

- In: ADR 0008, parent POM plugin management + version property, ledger-service
  plugin execution, contract file, README mention of where the contract lives.
- Out: any endpoint (T-003+), Problem Details exception handler (T-003),
  contract linting or breaking-change detection in CI (later, if needed).

## Design notes

- Pin the latest openapi-generator release that supports Spring Boot 4
  (Jakarta, Spring 7, Jackson 3 at runtime); check its `spring` generator
  options for Boot 4 before choosing flags. Disable generation of controllers,
  application class, docs, tests and supporting files; `useTags=true`.
- Exclude generated packages from JaCoCo and Spotless; do not commit generated code.
- Keep generator configuration in the parent POM's `pluginManagement` so future
  services (payment-service) reuse it with only `inputSpec` and packages.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour (B3/B4 evidenced by the build and ArchUnit)
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
