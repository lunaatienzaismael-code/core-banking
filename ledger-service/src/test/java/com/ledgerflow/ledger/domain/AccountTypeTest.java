package com.ledgerflow.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccountTypeTest {

    @ParameterizedTest
    @CsvSource({"ASSET, DEBIT", "EXPENSE, DEBIT", "LIABILITY, CREDIT", "EQUITY, CREDIT", "INCOME, CREDIT"})
    void accountTypeFixesTheNormalBalanceSide(AccountType type, Side expected) {
        assertThat(type.normalSide()).isEqualTo(expected);
    }
}
