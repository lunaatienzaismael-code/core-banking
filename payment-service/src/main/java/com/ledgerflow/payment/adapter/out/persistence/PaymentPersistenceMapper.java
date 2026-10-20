package com.ledgerflow.payment.adapter.out.persistence;

import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.Money;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentId;
import com.ledgerflow.payment.domain.PaymentStatus;
import java.time.ZoneOffset;

/** Explicit mapping between the domain and the JPA entity (ADR 0002 rule 7). */
final class PaymentPersistenceMapper {

    private PaymentPersistenceMapper() {}

    static PaymentEntity toEntity(Payment payment) {
        return new PaymentEntity(
                payment.id().value(),
                payment.debtor().value(),
                payment.creditor().value(),
                payment.amount().amount(),
                payment.amount().currency().getCurrencyCode(),
                payment.status().name(),
                payment.reason().orElse(null),
                payment.initiatedAt().atOffset(ZoneOffset.UTC));
    }

    static Payment toDomain(PaymentEntity entity) {
        return Payment.restore(
                new PaymentId(entity.getId()),
                new AccountId(entity.getDebtorAccountId()),
                new AccountId(entity.getCreditorAccountId()),
                Money.of(entity.getAmount(), entity.getCurrency()),
                entity.getInitiatedAt().toInstant(),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getReason());
    }
}
