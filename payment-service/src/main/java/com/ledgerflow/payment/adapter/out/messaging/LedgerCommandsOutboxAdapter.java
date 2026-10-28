package com.ledgerflow.payment.adapter.out.messaging;

import com.ledgerflow.messaging.PostTransfer;
import com.ledgerflow.messaging.ReserveFunds;
import com.ledgerflow.messaging.outbox.OutboxWriter;
import com.ledgerflow.payment.application.port.out.LedgerCommands;
import com.ledgerflow.payment.domain.Payment;
import org.springframework.stereotype.Component;

/** Writes ledger commands to {@code ledger.commands} through the outbox, keyed by payment (ADR 0013). */
@Component
class LedgerCommandsOutboxAdapter implements LedgerCommands {

    static final String LEDGER_COMMANDS = "ledger.commands";

    private final OutboxWriter outbox;

    LedgerCommandsOutboxAdapter(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void reserveFunds(Payment payment) {
        var paymentId = payment.id().value().toString();
        outbox.write(
                LEDGER_COMMANDS,
                paymentId,
                ReserveFunds.newBuilder()
                        .setPaymentId(paymentId)
                        .setDebtorAccountId(payment.debtor().value().toString())
                        .setAmountMinorUnits(payment.amount().amount())
                        .setCurrency(payment.amount().currency().getCurrencyCode())
                        .build());
    }

    @Override
    public void postTransfer(Payment payment) {
        var paymentId = payment.id().value().toString();
        outbox.write(
                LEDGER_COMMANDS,
                paymentId,
                PostTransfer.newBuilder()
                        .setPaymentId(paymentId)
                        .setCreditorAccountId(payment.creditor().value().toString())
                        .setAmountMinorUnits(payment.amount().amount())
                        .setCurrency(payment.amount().currency().getCurrencyCode())
                        .build());
    }
}
