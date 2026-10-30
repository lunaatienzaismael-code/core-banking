package com.ledgerflow.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The payment saga (UC4) across both services, through their HTTP APIs only: the payment's status from
 * payment-service, the money from ledger-service. Each test opens its own accounts, so they share the stack.
 */
@DisplayName("UC4 end to end: a payment moves the money between ledger accounts")
class PaymentSagaIT {

    private static final String EUR = "EUR";

    private final LedgerFlowApi api = new LedgerFlowApi(LedgerFlowStack.ledgerUrl(), LedgerFlowStack.paymentUrl());

    @Test
    @DisplayName("B2 a funded payment completes: the debtor is debited and the creditor credited by the amount")
    void fundedPaymentCompletesAndMovesTheAmount() {
        var debtor = api.openCustomerAccount(EUR);
        var creditor = api.openCustomerAccount(EUR);
        api.deposit(debtor, 10_000, EUR);

        var payment = api.awaitSettled(api.initiatePayment(debtor, creditor, 2_500, EUR));

        assertThat(payment.read("$.status", String.class)).isEqualTo("COMPLETED");
        assertThat(api.balance(debtor)).isEqualTo(7_500);
        assertThat(api.balance(creditor)).isEqualTo(2_500);
    }

    @Test
    @DisplayName("B3 a payment above the debtor's funds is rejected and no balance changes")
    void paymentWithoutFundsIsRejectedAndMovesNothing() {
        var debtor = api.openCustomerAccount(EUR);
        var creditor = api.openCustomerAccount(EUR);
        api.deposit(debtor, 1_000, EUR);

        var payment = api.awaitSettled(api.initiatePayment(debtor, creditor, 2_500, EUR));

        assertThat(payment.read("$.status", String.class)).isEqualTo("REJECTED");
        assertThat(payment.read("$.reason", String.class)).startsWith("INSUFFICIENT_FUNDS");
        assertThat(api.balance(debtor)).isEqualTo(1_000);
        assertThat(api.balance(creditor)).isZero();
    }

    @Test
    @DisplayName("B4 a payment the creditor's account refuses fails and the debtor's reserved funds come back")
    void paymentRefusedByTheCreditorFailsAndReleasesTheDebtorsFunds() {
        var debtor = api.openCustomerAccount(EUR);
        // The ledger reserves the euros, then refuses to post them to an account in another currency.
        var creditor = api.openCustomerAccount("SEK");
        api.deposit(debtor, 10_000, EUR);

        var payment = api.awaitSettled(api.initiatePayment(debtor, creditor, 2_500, EUR));

        assertThat(payment.read("$.status", String.class)).isEqualTo("FAILED");
        assertThat(payment.read("$.reason", String.class)).contains("CURRENCY_MISMATCH");
        assertThat(api.balance(debtor)).isEqualTo(10_000);
        assertThat(api.balance(creditor)).isZero();
    }
}
