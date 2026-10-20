package com.ledgerflow.payment.application.port.out;

import com.ledgerflow.payment.domain.IdempotencyKey;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentId;
import com.ledgerflow.payment.domain.RequestFingerprint;
import java.util.Optional;

public interface PaymentRepository {

    /**
     * Inserts the payment under the key unless a payment already holds that key, atomically (ADR 0010): a concurrent
     * insert with the same key waits for the first to commit. Returns whichever payment holds the key, with the
     * fingerprint stored with it.
     */
    KeyedPayment insertIfAbsent(Payment payment, IdempotencyKey key, RequestFingerprint fingerprint);

    Optional<Payment> findById(PaymentId id);

    /** A stored payment and the fingerprint of the request that created it. */
    record KeyedPayment(Payment payment, RequestFingerprint fingerprint) {}
}
