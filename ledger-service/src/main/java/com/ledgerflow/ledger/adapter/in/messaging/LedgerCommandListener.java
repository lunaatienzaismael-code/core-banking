package com.ledgerflow.ledger.adapter.in.messaging;

import com.ledgerflow.ledger.application.port.in.ReserveFundsUseCase;
import com.ledgerflow.messaging.ReserveFunds;
import com.ledgerflow.messaging.outbox.OutboxHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Turns commands on {@code ledger.commands} into use case calls. The offset is committed after the use case returns,
 * i.e. after its database transaction; a redelivery in between is absorbed by the inbox (ADR 0013).
 */
@Component
class LedgerCommandListener {

    private final ReserveFundsUseCase reserveFunds;

    LedgerCommandListener(ReserveFundsUseCase reserveFunds) {
        this.reserveFunds = reserveFunds;
    }

    @KafkaListener(topics = LedgerTopics.COMMANDS)
    void on(ConsumerRecord<String, Object> record) {
        var messageId = OutboxHeaders.messageIdOf(record.headers());
        switch (record.value()) {
            case ReserveFunds command -> reserveFunds.reserve(LedgerCommandMapper.toCommand(messageId, command));
            default ->
                throw new IllegalArgumentException("Unsupported ledger command "
                        + record.value().getClass().getName());
        }
    }
}
