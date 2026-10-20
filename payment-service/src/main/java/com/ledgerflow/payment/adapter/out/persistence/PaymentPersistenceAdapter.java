package com.ledgerflow.payment.adapter.out.persistence;

import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentId;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpa;

    PaymentPersistenceAdapter(PaymentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Payment payment) {
        jpa.save(PaymentPersistenceMapper.toEntity(payment));
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return jpa.findById(id.value()).map(PaymentPersistenceMapper::toDomain);
    }
}
