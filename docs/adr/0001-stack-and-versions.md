# ADR 0001: Language, Build System and Core Stack

- Status: Accepted
- Date: 2026-10-12
- Deciders: Project owner

## Context

LedgerFlow is a greenfield, event-driven SEPA-style payments platform with a
double-entry ledger, built as a backend portfolio project targeting European
banking and fintech reviewers. The stack must look **production-credible to a
bank**, not bleeding-edge for its own sake: ecosystem maturity, long-term
support and operational familiarity weigh more than novelty.

We need to fix: the JVM language version, the build tool and the application
framework baseline.

## Options Considered

### Java version
- **Java 21 LTS** — the LTS the banking sector actually runs in production
  today. Ships virtual threads, records, sealed types, pattern matching.
  Broadest vendor/tooling support.
- **Java 25 LTS** — newer LTS, more language features. Signals being "ahead",
  but banks lag LTS adoption by years; some vendor runtimes and tooling are
  still catching up.

### Build tool
- **Maven multi-module** — ubiquitous in enterprise/banking, declarative,
  predictable, trivial for reviewers to read. Parent POM centralises versions.
- **Gradle** — faster, more flexible, but less common in conservative banking
  shops and noisier for a reviewer skimming the build.

### Framework
- **Spring Boot** (current stable GA) — de-facto standard for JVM backend in
  banking; mature support for web, data, testing, observability.

## Decision

- **Java 21 LTS.**
- **Maven multi-module**: a parent POM plus one module per service, starting
  with **`ledger-service`** only. Additional service modules are added per
  roadmap phase.
- **Spring Boot**, latest stable GA release. The exact version is **pinned in
  Phase 1** against the then-current release and recorded in the parent POM;
  it is deliberately not hard-coded in this ADR to avoid documenting a stale
  number.
- Group id `com.ledgerflow`; base package per service, e.g.
  `com.ledgerflow.ledger`.

## Consequences

- The project reads as "I know what banks run", while still using modern Java
  (virtual threads, records) where it adds clarity.
- Maven's parent POM is the single place for dependency and plugin versions,
  reused by every future service module.
- If a later phase needs a Java 25-only feature, that is a new decision with
  its own ADR superseding this one.
- Spring Boot's version baseline constrains the minimum Java version; the
  Phase 1 pin must be compatible with Java 21.
