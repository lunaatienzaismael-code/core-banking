# T-003 — Open an account end to end

- Use case: UC1 (`docs/spec.md`) · Phase: 3 · Type: feat
- Issue: #7 · Branch: `feat/T-003-open-account-endpoint`
- Depends on: T-001, T-002

## Goal

An API consumer can open an account with `POST /accounts`, and it is persisted
in PostgreSQL through every hexagonal layer.

## Acceptance criteria

- **AC1.1 Open account**
  - Given a request to open an account with a currency, an account type
    (which fixes its normal balance side) and an overdraft policy
  - When the account is created
  - Then it has a unique id, the given currency, a zero balance, and an empty
    movement list.
  - *This task evidences id, currency, type/normal side and zero balance via
    the `201` response and the persisted row; "empty movement list" is
    verified in T-005 (B2) once the movements endpoint exists.*
- **B1** — `201 Created` with a `Location: /accounts/{id}` header.
- **B2** — Invalid input (missing field, unknown currency code, unknown type
  or policy) returns `400` as Problem Details (`application/problem+json`).
- **B3** — Liquibase changeset creates the `accounts` table (uuid id, `char(3)`
  currency, type, overdraft policy, `version` for optimistic locking, created
  timestamp); the changelog IT still passes.

## Scope

- In: contract path + schemas for opening an account, `OpenAccountUseCase` +
  command/view, `OpenAccountService`, `AccountRepository` port, JPA persistence
  adapter + entity + mapper, controller implementing the generated interface,
  web mapper, global Problem Details handler in `adapter.in.web`.
- Out: balance and movements endpoints (T-004, T-005), `GET /accounts/{id}`.

## Design notes

- Add `spring-boot-starter-data-jpa`; Hibernate `ddl-auto` stays `validate`/none
  — Liquibase owns the schema.
- The id is generated server-side (UUID); response amounts follow ADR 0004
  (`{"amount": 0, "currency": "EUR"}`, minor units).
- The Problem Details handler is the single place mapping exceptions to HTTP
  (ADR 0006); T-004 extends it with 404.
- Tests: service unit test (port mocked), `@WebMvcTest` for 201/400 mapping,
  persistence adapter IT, one end-to-end IT over HTTP.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
