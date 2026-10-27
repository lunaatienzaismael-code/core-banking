---
name: spring-boot-conventions
description: Spring Boot conventions shared by LedgerFlow services - application.yml layout, config/ beans, controllers on generated OpenAPI interfaces, web mappers, the Problem Details handler, constructor injection and the transaction boundary. Use when adding or changing configuration, a controller, an exception mapping, a Spring bean or a new inbound adapter (e.g. a Kafka listener) in a service module.
---

# Spring Boot conventions for LedgerFlow services

Source of truth: ADR 0002 (layers, Spring allowed in `application`), ADR 0006
(API and errors), ADR 0008 (OpenAPI-first). Where each class goes is
`java-hexagonal`; database configuration and persistence are
`persistence-postgres-liquibase`; test wiring is `java-testing`. This skill
covers how the Spring parts are written once they are in place.

## Configuration

1. **One `src/main/resources/application.yml` per service**, no profiles. Every
   value that changes between environments is an environment variable with a
   local default matching `infra/docker-compose.yml`:
   `${PAYMENT_DB_URL:jdbc:postgresql://localhost:5433/payment}`. Variables are
   prefixed with the service (`LEDGER_`, `PAYMENT_`).
2. **Fixed settings in every service:** `spring.application.name`,
   `spring.jpa.open-in-view: false`, `spring.jpa.hibernate.ddl-auto: validate`
   (Liquibase owns the schema), the Liquibase master changelog path,
   `spring.mvc.problemdetails.enabled: true`, and actuator exposing only
   `health,info` with probes enabled.
3. **Comment the why** of any non-obvious setting on the line above it (the
   `ddl-auto` and `server.port` comments are the model).
4. **One port per service** so services run side by side locally: the ledger
   keeps Spring's default `8080`; every later service sets `server.port` from
   `<SERVICE>_PORT` (payment: `${PAYMENT_PORT:8081}`).

## Beans and injection

5. **Constructor injection only**, `private final` fields, no `@Autowired`.
6. **Package-private by default.** Use case implementations, controllers, the
   Problem Details handler, adapters, entities, mappers and `@Configuration`
   classes are not `public`; only ports, commands/views and domain types are.
7. **`config/` holds beans that belong to no adapter**, one
   `@Configuration(proxyBeanMethods = false)` class per topic named
   `<Topic>Configuration`. Today: the `Clock` bean — use cases take a `Clock`
   and never call `Instant.now()`.
8. **`@Transactional` only on use case methods** in `application.service`
   (`java-hexagonal` rule 4; ArchUnit checks it). An inbound adapter, web or
   messaging, calls one use case and never opens a transaction itself.

## Web adapter

9. **Controllers implement the generated `*Api` interface** (ADR 0008:
   `interfaceOnly`, `skipDefaultInterface`, `Dto` suffix, bean validation on).
   Routes, payloads and request validation come from the contract, not from
   annotations in the controller.
10. **A controller only translates:** DTO → command through a stateless
    `final` `*WebMapper` with static methods, call the `*UseCase`, view → DTO.
    No logic, no `try/catch`.
11. **`201` responses** carry `Location` built with
    `ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")`.
12. **Optional response fields are omitted, not `null`** — payment sets
    `spring.jackson.default-property-inclusion: non_null`; the ledger has no
    optional response field yet and should do the same when it gets one.

## Errors (Problem Details, RFC 9457)

13. **One `ProblemDetailsHandler` per service** in `adapter.in.web`:
    `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`, so
    Spring MVC's own errors (malformed JSON, failed body validation, type
    mismatch, unsupported media type) are already Problem Details.
14. **One `@ExceptionHandler` per domain exception**, returning a
    `ProblemDetail` with status, a fixed human `title` and the exception
    message as `detail`. Status by meaning (ADR 0006): malformed input `400`;
    unknown resource `404`; well-formed request that breaks a business rule
    `422`.
15. **`ConstraintViolationException` → `400`.** Generated interfaces are
    `@Validated`, so header and path constraints fail through method
    validation before the controller runs. Payment maps it; the ledger does
    not yet (known gap, it falls to the `500` fallback) — payment is the
    target.
16. **The `500` fallback** `@ExceptionHandler(Exception.class)` logs the cause
    and answers a fixed message, never exception text, types or stack traces.
    ArchUnit requires it in every web adapter.

Messaging adapters (Phase 7) get their own rules in the `kafka-events` skill
when the first listener exists; rules 5–8 already apply to them.

## Reference examples

- Configuration: `payment-service/src/main/resources/application.yml`,
  `ledger-service/src/main/resources/application.yml`
- `config/` bean: `payment-service/src/main/java/com/ledgerflow/payment/config/ClockConfiguration.java`
- Use case with `Clock` and `@Transactional`: `payment-service/src/main/java/com/ledgerflow/payment/application/service/InitiatePaymentService.java`
- Controller on a generated interface, `201` + `Location`: `payment-service/src/main/java/com/ledgerflow/payment/adapter/in/web/PaymentController.java`,
  `ledger-service/src/main/java/com/ledgerflow/ledger/adapter/in/web/AccountController.java`
- Web mapper: `payment-service/src/main/java/com/ledgerflow/payment/adapter/in/web/PaymentWebMapper.java`
- Problem Details handler (target, with `ConstraintViolationException`):
  `payment-service/src/main/java/com/ledgerflow/payment/adapter/in/web/ProblemDetailsHandler.java`;
  ledger: `ledger-service/src/main/java/com/ledgerflow/ledger/adapter/in/web/ProblemDetailsHandler.java`
- Generator configuration: parent `pom.xml`, `openapi-generator-maven-plugin`
