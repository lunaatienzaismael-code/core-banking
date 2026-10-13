---
name: java-testing
description: What to test at each layer of a LedgerFlow service and how (ADR 0003) - JUnit 5, AssertJ, Mockito only for outbound ports, Testcontainers PostgreSQL, named tests for financial invariants. Use when writing or reviewing any test.
---

# Testing LedgerFlow services

Source of truth: ADR 0003. Deterministic first: if a rule can be a test or an
ArchUnit rule, it is one.

## Which test, where

| Layer under test | Kind | Name | Tools | Spring context |
|------------------|------|------|-------|----------------|
| `domain` | unit | `<Class>Test` | JUnit 5 + AssertJ | none |
| `application.service` | unit | `<UseCase>ServiceTest` | + Mockito for **outbound ports only** | none |
| `adapter.in.web` | slice | `<Controller>Test` | `@WebMvcTest`, use case mocked | web slice |
| `adapter.out.persistence` | integration | `<Adapter>IT` | Testcontainers PostgreSQL | full or `@DataJpaTest` + real DB |
| Full vertical | integration | `<Feature>IT` | `@SpringBootTest(RANDOM_PORT)` + `RestTestClient` | full |

- `*Test` runs in Surefire (`test`), `*IT` in Failsafe (`verify`).
- Integration tests `@Import(TestcontainersConfiguration.class)`; never H2,
  never a hand-started container.

## Rules

1. **Never mock the domain.** Build real domain objects. Mock only outbound
   ports, and only in application service tests.
2. **AssertJ only** (`assertThat`, `assertThatThrownBy`); no JUnit assertions.
3. **Name tests after behaviour**, camelCase sentence:
   `rejectsTransactionWhoseLinesDoNotSumToZero`. When a test is the evidence
   for a spec criterion, add `@DisplayName("AC2.1 …")` so it is traceable.
4. **Financial invariants get explicit, named tests** (ADR 0003): debits equal
   credits, no money created or destroyed, currency consistency, no overdraft,
   idempotency, safe concurrency.
5. **Edge cases for money:** zero, negative, `Long.MAX_VALUE` overflow,
   mismatched currencies, invalid ISO-4217 codes.
6. **Arrange / act / assert** with blank lines between; one behaviour per test.
   Parameterised tests (`@ParameterizedTest`) for tables of cases.
7. **Integration tests own their data:** create what they need through the
   API or adapter; do not depend on test order or on data from other tests.
8. **Concurrency tests** use a real database and real threads
   (`ExecutorService` + `CountDownLatch`), assert on the final persisted state,
   and are deterministic in outcome even if not in interleaving.
9. **Fictitious data only** (IBANs, names).
10. A new behaviour without a test that fails when the behaviour is removed is
    not done.

## Running

Through the `test-runner` subagent (it wraps `scripts/test-summary.sh`). Locally:
`./mvnw -B verify`; one module: `./mvnw -B verify -pl ledger-service -am`.
Mutation testing on demand: see README.

## Reference examples

Real UC1 tests (`ledger-service`), one per test kind:

- Domain unit: `ledger-service/src/test/java/com/ledgerflow/ledger/domain/MoneyTest.java` — money edges and overflow; `ledger-service/src/test/java/com/ledgerflow/ledger/domain/AccountTest.java` for balance per normal side.
- Service with mocked ports: `ledger-service/src/test/java/com/ledgerflow/ledger/application/service/OpenAccountServiceTest.java` — Mockito on the outbound port only.
- `@WebMvcTest` slice: `ledger-service/src/test/java/com/ledgerflow/ledger/adapter/in/web/AccountControllerTest.java` — use case mocked, Problem Details asserted.
- Adapter IT: `ledger-service/src/test/java/com/ledgerflow/ledger/adapter/out/persistence/PostingPersistenceAdapterIT.java` — real PostgreSQL, owns its data.
- HTTP end-to-end IT with fixture: `ledger-service/src/test/java/com/ledgerflow/ledger/AccountBalanceIT.java` — `RANDOM_PORT`, `@DisplayName("AC1.2 …")` traceability.
- Fixture: `ledger-service/src/test/java/com/ledgerflow/ledger/LedgerFixture.java` — inserts balanced postings until UC2 provides the write side.
- ArchUnit: `ledger-service/src/test/java/com/ledgerflow/ledger/HexagonalArchitectureTest.java`; Testcontainers config: `ledger-service/src/test/java/com/ledgerflow/ledger/TestcontainersConfiguration.java`.
