package com.ledgerflow.payment.adapter.out.messaging;

import com.ledgerflow.messaging.AssessRisk;
import com.ledgerflow.messaging.outbox.OutboxWriter;
import com.ledgerflow.payment.application.port.out.RiskCommands;
import com.ledgerflow.payment.domain.Payment;
import org.springframework.stereotype.Component;

/** Writes risk commands to {@code risk.commands} through the outbox, keyed by payment (ADR 0013, ADR 0014). */
@Component
class RiskCommandsOutboxAdapter implements RiskCommands {

    static final String RISK_COMMANDS = "risk.commands";

    private final OutboxWriter outbox;

    RiskCommandsOutboxAdapter(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void assess(Payment payment) {
        var paymentId = payment.id().value().toString();
        outbox.write(
                RISK_COMMANDS,
                paymentId,
                AssessRisk.newBuilder()
                        .setPaymentId(paymentId)
                        .setDebtorAccountId(payment.debtor().value().toString())
                        .setAmountMinorUnits(payment.amount().amount())
                        .setCurrency(payment.amount().currency().getCurrencyCode())
                        .build());
    }
}
