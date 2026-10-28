package com.ledgerflow.payment.adapter.in.messaging;

import com.ledgerflow.messaging.outbox.OutboxHeaders;
import com.ledgerflow.payment.application.port.in.AdvancePaymentSagaUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the saga with the payment's own events and the ledger's replies. The offset is committed after the use case
 * returns, i.e. after its database transaction; a redelivery in between is absorbed by the inbox (ADR 0013).
 */
@Component
class PaymentSagaListener {

    private final AdvancePaymentSagaUseCase saga;

    PaymentSagaListener(AdvancePaymentSagaUseCase saga) {
        this.saga = saga;
    }

    @KafkaListener(topics = {"payments.events", "ledger.replies"})
    void on(ConsumerRecord<String, Object> record) {
        saga.advance(SagaInputMapper.toInput(OutboxHeaders.messageIdOf(record.headers()), record.value()));
    }
}
