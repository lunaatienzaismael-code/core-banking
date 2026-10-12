# ADR 0006: API Style and Error Model

- Status: Accepted
- Date: 2026-10-12
- Deciders: Project owner

## Context

The platform is exposed to API consumers (a trusted channel/BFF, not end users
directly — see `spec.md`). The contract and the error format are part of what a
reviewer evaluates: predictable integration and a modern error standard signal
maturity.

## Options Considered

### Contract ownership
- **Code-first** (annotations generate the OpenAPI doc) — fast, but the
  contract drifts as an afterthought of the code.
- **OpenAPI-first** (the contract is authored and is the source of truth; the
  server is generated from or validated against it in CI) — more discipline,
  but the contract cannot silently diverge from the implementation.

### Error representation
- **Ad-hoc JSON error bodies** — inconsistent, undocumented.
- **Problem Details (RFC 9457)** — standard `application/problem+json` with
  `type`, `title`, `status`, `detail`, `instance` and extensions.

## Decision

- **OpenAPI-first.** The OpenAPI contract lives in the repository and is the
  source of truth for each service's HTTP API. CI validates that the
  implementation conforms to the contract (exact tooling pinned in Phase 1).
- **Errors use Problem Details (RFC 9457)**, `application/problem+json`. A
  shared exception-to-ProblemDetail mapping lives in the inbound web adapter;
  domain/application never emit HTTP types.
- Amounts in API payloads follow ADR 0004 (minor units + ISO-4217 currency).
- Example data (IBANs, names) is always fictitious.

## Consequences

- Consumers get a stable, documented contract; breaking changes are visible as
  contract changes in review.
- A consistent error shape across all endpoints and services.
- The web adapter owns all HTTP concerns, keeping ADR 0002's dependency rules
  intact.
- Once a second inbound adapter exists and config/error conventions stabilise,
  a `spring-boot-conventions` skill will capture them (per roadmap).
