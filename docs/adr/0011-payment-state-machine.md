# ADR 0011: Payment State Machine

- Status: Accepted
- Date: 2026-10-20
- Deciders: Project owner
- Related: [ADR 0010](0010-idempotent-payment-initiation.md) (initiation and
  the `payment-service` boundary)

## Context

A payment lives longer than the request that creates it. After initiation
(UC3), an asynchronous saga reserves funds in the ledger and completes or
compensates the transfer (UC4, Phase 7); a risk check must approve it before
any reservation (UC5, Phase 8). Each of these steps changes the payment's
status, possibly from different threads or redelivered messages.

- **AC3.3** — a payment only transitions along allowed states; illegal
  transitions are rejected.

The states must be fixed now, although Phase 6 only creates payments, so that
the saga and the risk check plug into an existing model instead of reshaping
it. UC6 (AC6.2) will report payments stuck in a non-terminal state, so which
states are terminal matters too.

## Options Considered

- **Status column without rules** — any code may set any status.
  - Pros: nothing to build.
  - Cons: AC3.3 fails by construction; illegal moves (e.g. completing a
    rejected payment) are caught, if ever, by reconciliation.
- **Enum + transition table in the domain** — the aggregate owns its status;
  each business event is a method that checks the current status against a
  table of allowed moves and throws otherwise.
  - Pros: the whole machine is one readable table, testable exhaustively
    (every from/to pair); no framework in the domain (ADR 0002).
  - Cons: guards and actions live in plain code; a large machine would need
    more structure.
- **State pattern (one class per state)** — each state class implements the
  events it accepts.
  - Pros: behaviour that differs by state sits with the state.
  - Cons: six classes for what is, today, a table of allowed moves with no
    per-state behaviour.
- **Spring State Machine** — a framework-managed machine with persisted
  context.
  - Pros: guards, actions, hierarchical states, listeners.
  - Cons: Spring in the domain (forbidden by ADR 0002); its persistence and
    concurrency model would compete with the aggregate and the database row;
    heavy for six states.

For comparison, ISO 20022 payment status reports carry external status codes
such as `RJCT` (Rejected) and `ACSC` (AcceptedSettlementCompleted) [1]; the
states below are the service's internal lifecycle, which a later interface to
a scheme could map onto those codes.

## Decision

**Enum + transition table in the domain**, with optimistic locking on the
payment row.

### States

| State | Meaning | Terminal |
|-------|---------|----------|
| `INITIATED` | Accepted by the API; nothing reserved | no |
| `RISK_APPROVED` | Risk check passed (UC5) | no |
| `FUNDS_RESERVED` | Debtor's funds held in the ledger (UC4) | no |
| `COMPLETED` | Transfer posted; funds moved to the creditor | yes |
| `REJECTED` | Refused before any reservation (risk, unknown account, currency, insufficient funds) | yes |
| `FAILED` | Failed after a reservation, which was released by compensation | yes |

### Allowed transitions

| From | To | Event (method) | Driven by |
|------|----|----------------|-----------|
| `INITIATED` | `RISK_APPROVED` | `approveRisk` | risk step (Phase 8; until then the saga approves every payment) |
| `INITIATED` | `REJECTED` | `reject` | risk step or saga validation |
| `RISK_APPROVED` | `FUNDS_RESERVED` | `reserveFunds` | saga, after the ledger reserved the funds |
| `RISK_APPROVED` | `REJECTED` | `reject` | saga, when the ledger refuses the reservation |
| `FUNDS_RESERVED` | `COMPLETED` | `complete` | saga, after the transfer is posted |
| `FUNDS_RESERVED` | `FAILED` | `fail` | saga, after compensation released the funds |

Every other pair is illegal, including any move out of a terminal state and
any move to the same state. An illegal move throws a `DomainException`
subtype naming both states; nothing changes.

- **Business-event methods, not a generic setter.** The aggregate exposes
  `approveRisk()`, `reject(reason)`, `reserveFunds()`, `complete()`, `fail(reason)`;
  `reject` is allowed from two states. The transition table is the single
  source of truth the methods consult.
- **`REJECTED` vs `FAILED`.** The split follows money: `REJECTED` means no
  funds were ever held; `FAILED` means funds were held and released. UC6 and
  support can tell from the status alone whether a compensation happened.
- **Optimistic locking.** The payment row has a `version` column (`@Version`
  or an equivalent `WHERE version = ?`). Two concurrent events on one payment
  (e.g. a redelivered message) cannot both apply: the loser gets a conflict and
  re-reads, and then the transition table rejects the now-illegal move. Phase
  7's idempotent consumers decide whether that is an error or a duplicate.
- **Phase 6 scope.** Only `INITIATED` is reachable through the API. The
  transitions are implemented and tested in the domain (AC3.3) and exposed
  through the status field of `GET /payments/{id}`; no endpoint triggers them.

## Consequences

- AC3.3 is provable exhaustively with unit tests over every from/to pair
  (java-testing rule 11), without Spring or a database.
- The saga (Phase 7) and risk check (Phase 8) only call event methods; they
  cannot invent states.
- UC6's stuck-payment report (AC6.2) looks for `INITIATED`, `RISK_APPROVED`
  and `FUNDS_RESERVED` older than a threshold.
- `FAILED` is recorded only once compensation has finished. If Phase 7 needs
  to show "compensation in progress", a `RELEASING` state between
  `FUNDS_RESERVED` and `FAILED` is the expected extension. **Re-evaluation
  trigger:** the saga design in Phase 7, or per-state behaviour that would make
  the state pattern pay off.

## References

1. ISO 20022, *External Code Sets* (ExternalPaymentTransactionStatus1Code) —
   <https://www.iso20022.org/catalogue-messages/additional-content-messages/external-code-sets>
