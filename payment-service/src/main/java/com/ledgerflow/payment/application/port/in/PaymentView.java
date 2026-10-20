package com.ledgerflow.payment.application.port.in;

import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.Money;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentId;
import com.ledgerflow.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.Optional;

/** A payment as seen by use case callers. */
public record PaymentView(
        PaymentId id,
        PaymentStatus status,
        AccountId debtor,
        AccountId creditor,
        Money amount,
        Instant initiatedAt,
        Optional<String> reason) {

    public static PaymentView of(Payment payment) {
        return new PaymentView(
                payment.id(),
                payment.status(),
                payment.debtor(),
                payment.creditor(),
                payment.amount(),
                payment.initiatedAt(),
                payment.reason());
    }
}
