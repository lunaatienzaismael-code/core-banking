package com.ledgerflow.payment.application.port.out;

import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentId;
import java.util.Optional;

public interface PaymentRepository {

    /** Inserts a new payment. */
    void save(Payment payment);

    Optional<Payment> findById(PaymentId id);
}
