package com.ledgerflow.payment.adapter.out.persistence;

import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.domain.IdempotencyKey;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentId;
import com.ledgerflow.payment.domain.PaymentNotFoundException;
import com.ledgerflow.payment.domain.RequestFingerprint;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Inserts payments with plain SQL, because {@code ON CONFLICT DO NOTHING} is what makes initiation idempotent under
 * concurrency (ADR 0010); reads them back through JPA.
 */
@Component
class PaymentPersistenceAdapter implements PaymentRepository {

    private static final String INSERT_IF_ABSENT_SQL = """
            insert into payments (id, debtor_account_id, creditor_account_id, amount, currency, status, reason,
                                  initiated_at, idempotency_key, request_hash, version)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            on conflict (idempotency_key) do nothing""";

    private final PaymentJpaRepository jpa;
    private final JdbcClient jdbc;

    PaymentPersistenceAdapter(PaymentJpaRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public KeyedPayment insertIfAbsent(Payment payment, IdempotencyKey key, RequestFingerprint fingerprint) {
        var row = PaymentPersistenceMapper.toEntity(payment);
        // A second insert with the same key waits until the first commits or rolls back, then does nothing or inserts.
        jdbc.sql(INSERT_IF_ABSENT_SQL)
                .params(
                        row.getId(),
                        row.getDebtorAccountId(),
                        row.getCreditorAccountId(),
                        row.getAmount(),
                        row.getCurrency(),
                        row.getStatus(),
                        row.getReason(),
                        row.getInitiatedAt(),
                        key.value(),
                        fingerprint.hex())
                .update();
        // Read committed: this statement sees whichever payment now holds the key, ours or the one that won.
        var stored = jpa.findByIdempotencyKey(key.value()).orElseThrow();
        return new KeyedPayment(
                PaymentPersistenceMapper.toDomain(stored), new RequestFingerprint(stored.getRequestHash()));
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return jpa.findById(id.value()).map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    public void update(Payment payment) {
        // The entity read earlier in this transaction, with the version it had then; JPA checks it on flush.
        var row = jpa.findById(payment.id().value()).orElseThrow(() -> new PaymentNotFoundException(payment.id()));
        row.changeStatus(payment.status().name(), payment.reason().orElse(null));
    }
}
