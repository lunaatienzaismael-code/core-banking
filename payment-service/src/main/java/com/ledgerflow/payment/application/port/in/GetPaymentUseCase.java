package com.ledgerflow.payment.application.port.in;

import com.ledgerflow.payment.domain.PaymentId;

public interface GetPaymentUseCase {

    /** @throws com.ledgerflow.payment.domain.PaymentNotFoundException if no payment has this id */
    PaymentView paymentOf(PaymentId id);
}
