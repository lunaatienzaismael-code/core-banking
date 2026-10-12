---
name: java-hexagonal
description: Where each class goes and how layers talk in a LedgerFlow service (hexagonal / ports and adapters, ADR 0002, money per ADR 0004). Use when creating or moving any Java production class, port, adapter, mapper or exception in a service module.
---

# Hexagonal layout for LedgerFlow services

Source of truth: ADR 0002 (layout, dependency rules) and ADR 0004 (money). The
dependency rules are enforced by `architecture-rules` (`HexagonalArchitecture`);
this skill covers what ArchUnit cannot see.

## Placement (base package `com.ledgerflow.<service>`)

| Class | Package | Name | Notes |
|-------|---------|------|-------|
| Entity, value object, domain service, domain exception | `domain` | `Account`, `Money`, `InsufficientFundsException` | JDK only. No annotations at all. |
| Use case interface | `application.port.in` | `OpenAccountUseCase` | One use case per interface. |
| Use case input/output | `application.port.in` | `OpenAccountCommand`, `AccountView` | Records; validate in the compact constructor. |
| Outbound port | `application.port.out` | `AccountRepository`, `<Thing>Port` | Speaks domain types only. |
| Use case implementation | `application.service` | `OpenAccountService` | `@Service`, constructor injection, `@Transactional`. |
| REST controller, DTO, web mapper, error handler | `adapter.in.web` | `AccountController`, `OpenAccountRequest`, `AccountWebMapper` | Depends on inbound ports only. |
| JPA entity, Spring Data repo, persistence adapter, mapper | `adapter.out.persistence` | `AccountEntity`, `AccountJpaRepository`, `AccountPersistenceAdapter`, `AccountPersistenceMapper` | Adapter implements the outbound port. |
| Cross-cutting Spring wiring | `config` | `<Topic>Configuration` | Only what belongs to no single adapter. |

## Rules

1. **Domain is plain Java.** No Spring, JPA, Jackson, Lombok or validation
   annotations. Invariants are enforced in constructors/factory methods and
   throw domain exceptions; an invalid domain object cannot exist.
2. **Money** is the `Money` value object: `long` minor units + ISO-4217 code.
   Never `double`/`float`; no `BigDecimal` in state. Operations across
   currencies throw. Use `Math.addExact`/`subtractExact` so overflow fails loudly.
3. **Ledger sign convention** (spec): posting amount is signed, debit `+`,
   credit `−`; a transaction's lines sum to zero per currency. Balance is
   derived from postings and reported from the account's normal balance side.
4. **Use cases are the transaction boundary.** `@Transactional` lives on the
   application service; optimistic-lock retries live there too, never in
   adapters or the domain.
5. **Adapters reach the application only through ports.** Controllers inject
   `*UseCase` interfaces, never `*Service` classes.
6. **Explicit mappers at each boundary.** Domain objects never appear in a
   request/response DTO or as a JPA entity. Mappers are small, stateless and
   live in the adapter that needs them.
7. **Errors:** domain/application throw domain exceptions; only
   `adapter.in.web` turns them into Problem Details (RFC 9457, ADR 0006). No
   HTTP types or status codes below the web adapter.
8. **Identifiers** are typed value objects (`AccountId`) in the domain;
   adapters convert to/from `UUID`/strings.
9. Prefer records and immutability; mutable state only in aggregates that need
   it (and then with `@Version` on the persistence side).
10. No speculative abstractions: create a port when a use case needs it, not
    before.

## Checklist before handing to the evaluator

- [ ] Every new class sits in the package from the table above.
- [ ] No framework import in `domain`; no JPA/web/Jackson in `application`.
- [ ] Mappers exist in both directions where data crosses a boundary.
- [ ] Domain exceptions mapped to Problem Details in the web adapter.
- [ ] `HexagonalArchitectureTest` green.

## Reference examples

<!-- Filled in Phase 4 with paths to real code from the first vertical (UC1). -->

- Valid layout fixture: `architecture-rules/src/test/java/com/ledgerflow/architecture/fixture/valid/`
