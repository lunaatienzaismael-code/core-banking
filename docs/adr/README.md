# Architecture Decision Records

Each ADR is a short, immutable record of one significant decision: its context,
the options considered, the decision, and its consequences. Superseded
decisions are not deleted; a new ADR supersedes them and says so.

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-stack-and-versions.md) | Language, Build System and Core Stack | Accepted |
| [0002](0002-hexagonal-architecture.md) | Hexagonal Architecture and Dependency Rules | Accepted |
| [0003](0003-testing-strategy.md) | Testing Strategy | Accepted |
| [0004](0004-persistence-and-money.md) | Persistence and Money Representation | Accepted, superseded in part by 0009 |
| [0005](0005-git-flow-and-merge-strategy.md) | Git Flow and Merge Strategy | Accepted |
| [0006](0006-api-and-error-model.md) | API Style and Error Model | Accepted |
| [0007](0007-deployment.md) | Deployment and Local Environment | Accepted |
| [0008](0008-openapi-tooling.md) | OpenAPI-First Tooling | Accepted |
| [0009](0009-materialised-balance.md) | Materialised Balance with Atomic Conditional Updates | Accepted |
| [0010](0010-idempotent-payment-initiation.md) | Idempotent Payment Initiation | Accepted |
| [0011](0011-payment-state-machine.md) | Payment State Machine | Accepted |

## Deferred decisions (ADRs to be written later)

- **Event-driven design**: orchestration vs choreography, outbox vs dual-write,
  topics and partition keys — Phase 7.
