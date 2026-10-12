# ADR 0002: Hexagonal Architecture and Dependency Rules

- Status: Accepted
- Date: 2026-10-12
- Deciders: Project owner

## Context

The core value of LedgerFlow is its accounting and payment logic. That logic
must be testable without a framework and protected from infrastructure churn
(web framework, ORM, messaging). We also want a structure that is identical
across every future service module, so the same architectural tests can be
reused.

## Options Considered

- **Layered (controller → service → repository)** — familiar but tends to leak
  persistence and framework concerns into business logic; dependency direction
  is not enforced.
- **Hexagonal (ports and adapters)** — business core depends on nothing;
  infrastructure depends inward via ports. More upfront structure, but the
  dependency rule is explicit and machine-checkable.
- **Clean/Onion** — equivalent goals; hexagonal's port/adapter vocabulary maps
  most directly to the inbound REST / outbound persistence split we need.

## Decision

Adopt **hexagonal architecture** with this package layout per service
(example for `ledger-service`):

```
com.ledgerflow.ledger
  domain/              # entities, value objects, domain services, invariants
  application/
    port/in/           # inbound ports = use case interfaces
    port/out/          # outbound ports = repository/gateway interfaces
    service/           # use case implementations (orchestrate domain + ports)
  adapter/
    in/web/            # REST controllers, request/response DTOs, mappers
    out/persistence/   # JPA entities, repository adapters, mappers
  config/              # Spring wiring that does not belong to one adapter
```

**Dependency rules (enforced by ArchUnit — see ADR 0003):**

1. `domain` depends only on the JDK. No Spring (no dependency injection, no
   annotations), no JPA, no Jackson, no web.
2. `application` depends on `domain` and its own ports. It **may** use Spring's
   core container and transaction support (`@Service`, constructor injection,
   `@Transactional`), since use cases are the transaction boundary.
3. `adapter.in.*` depends on inbound ports; it must not depend on `adapter.out`.
4. `adapter.out.*` depends on outbound ports and `domain`; it must not depend
   on `adapter.in`.
5. Adapters never depend on other adapters.
6. `domain` must not import any framework package. `application` must not
   import JPA, Spring Data, web/HTTP or serialization (Jackson) packages.
7. Mapping between layers is explicit (domain ↔ persistence entity, domain ↔
   web DTO); domain types do not cross the HTTP or DB boundary directly.

## Consequences

- The domain is unit-testable with plain JUnit, no Spring context.
- Application services are still unit-testable without a Spring context
  (constructor injection, outbound ports mocked); Spring annotations there are
  metadata only. Optimistic-locking retries live in the application layer, at
  the use case (transaction) boundary.
- The ArchUnit rule set is written once and reused by every new service module.
- There is a deliberate cost: explicit mappers between layers. This is accepted
  as the price of isolation and is itself a convention a reviewer expects to
  see done consistently.
- Where each class type lives is not a matter of taste; it is encoded in the
  rules and the `java-hexagonal` skill.
