package com.ledgerflow.risk.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * A payment to assess. {@code messageId} identifies the message it came in, so a redelivery is recognised
 * (ADR 0013).
 */
public record AssessRiskCommand(
        UUID messageId, String paymentId, String debtorAccountId, long amountMinorUnits, String currency) {

    public AssessRiskCommand {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(debtorAccountId, "debtorAccountId");
        Objects.requireNonNull(currency, "currency");
    }
}
