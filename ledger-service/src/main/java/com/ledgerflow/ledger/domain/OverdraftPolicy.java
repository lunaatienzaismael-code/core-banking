package com.ledgerflow.ledger.domain;

/** Whether an account's normal-side balance may go below zero. Enforced when transactions are recorded (UC2). */
public enum OverdraftPolicy {
    ALLOWED,
    NOT_ALLOWED
}
