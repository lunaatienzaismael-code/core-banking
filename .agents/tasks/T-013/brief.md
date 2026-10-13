# T-013 — Record a transaction end to end

- Use case: UC2 (`docs/spec.md`) · Phase: 5 · Type: feat
- Issue: #27 · Branch: `feat/T-013-record-transaction-endpoint`
- Depends on: T-011, T-012

## Goal

`POST /transactions` records a balanced double-entry transaction: the
transaction, its postings and the accounts' counters are written atomically,
or nothing is.

## Acceptance criteria

- **AC2.1** — Given two accounts in the same currency, when a transaction with
  debit and credit lines is recorded, then it is accepted only if total debits
  equal total credits in that currency; otherwise it is rejected and nothing
  is persisted.
- **AC2.2** — Given any accepted transaction, then the sum of all postings it
  creates is zero per currency.
- **AC2.4** — Given lines referencing different currencies, when the
  transaction is recorded, then it is rejected.
- **B1** — OpenAPI: `POST /transactions` with `lines[]` of `{ accountId,
  amount (Money, positive), direction (DEBIT|CREDIT) }`; `201 Created` with
  the transaction (id, occurredAt, lines) and `Location`; `422` Problem
  Details for unbalanced / mixed-currency / line currency ≠ account currency;
  `404` for an unknown account; `400` for malformed input.
- **B2** — `RecordTransactionUseCase` + command/view; the service is the
  transaction boundary and applies each line with the atomic counter
  `UPDATE` of ADR 0009 (unconditional here; the guard comes in T-014).
- **B3** — "Nothing persisted" is proven at HTTP level: after each rejection,
  no row in `transactions`/`postings` and counters unchanged.

## Scope

- In: OpenAPI contract, `application.port.in/out`, `application.service`,
  `adapter.in.web`, `adapter.out.persistence`, ITs.
- Out: overdraft rejection (T-014); concurrency test (T-015); idempotency
  keys (UC3).

## Design notes

- Lines are converted to signed `PostingLine`s (debit +, credit −) by the web
  mapper; the domain `Transaction` (T-011) validates balance and currency.
- Problem `type` URIs follow the UC1 handler; one handler per new exception.
- If the size passes ~400 lines, split the 422 error paths into a follow-up.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
