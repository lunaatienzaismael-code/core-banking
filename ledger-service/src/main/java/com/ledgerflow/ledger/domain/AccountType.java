package com.ledgerflow.ledger.domain;

/** Accounting classification of an account; it fixes the account's normal balance side (see spec). */
public enum AccountType {
    ASSET(Side.DEBIT),
    LIABILITY(Side.CREDIT),
    EQUITY(Side.CREDIT),
    INCOME(Side.CREDIT),
    EXPENSE(Side.DEBIT);

    private final Side normalSide;

    AccountType(Side normalSide) {
        this.normalSide = normalSide;
    }

    public Side normalSide() {
        return normalSide;
    }
}
