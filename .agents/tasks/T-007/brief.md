# T-007 — ArchUnit rules extracted from UC1

- Use case: none (Phase 4 conventions) · Phase: 4 · Type: test
- Issue: #16 · Branch: `test/T-007-archunit-conventions`
- Depends on: none

## Goal

Conventions that UC1 follows by discipline become machine-checked rules in the
reusable `HexagonalArchitecture`, so later services get them for free.

## Acceptance criteria

- **B1** — No floating point for money: no field, method parameter or return
  type of `double`, `float`, `Double` or `Float` in `domain` or `application`
  (ADR 0004).
- **B2** — Generated contract DTOs stay in the web adapter: classes in
  `..adapter.in.web.model..` are accessed only from `..adapter.in.web..`
  (ADR 0002 rule 6, ADR 0008).
- **B3** — Use cases are the transaction boundary: `@Transactional` (Spring's
  and Jakarta's) appears only on classes or methods in `application.service`
  (ADR 0002 rule 4).
- **B4** — Domain exceptions share one base: every `Throwable` subclass in
  `domain` is assignable to the service's `domain.DomainException`.
- **B5** — `@Entity` (and `@Embeddable`, `@MappedSuperclass`) classes live only
  in `adapter.out.persistence`.
- **B6** — Each new rule has a violating fixture under
  `architecture-rules/src/test/java/.../fixture/` and a case in the
  parameterised `ruleDetectsItsViolation` test; the compliant and empty
  fixtures still pass; `ledger-service` passes all rules.

## Scope

- In: `architecture-rules` (`HexagonalArchitecture`, fixtures, tests),
  `java-hexagonal` checklist line naming the new rules.
- Out: new Problem Details mapping for `DomainException` (not needed while
  every subtype has its own handler).

## Design notes

- B5 overlaps the existing "domain is JDK-only" and "application free of
  persistence" rules; keep it anyway (user decision) — it also catches an
  entity placed in another adapter or in `config`.
- B4: if a service has no `DomainException`, the rule should fail with a clear
  message rather than pass vacuously.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
