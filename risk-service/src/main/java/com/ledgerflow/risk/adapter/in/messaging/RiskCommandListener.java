package com.ledgerflow.risk.adapter.in.messaging;

import com.ledgerflow.messaging.AssessRisk;
import com.ledgerflow.messaging.outbox.OutboxHeaders;
import com.ledgerflow.risk.application.port.in.AssessRiskCommand;
import com.ledgerflow.risk.application.port.in.AssessRiskUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Turns {@code AssessRisk} on {@code risk.commands} into a use case call. The offset is committed after the use case
 * returns, i.e. after its database transaction; a redelivery in between is absorbed by the inbox (ADR 0013).
 */
@Component
class RiskCommandListener {

    static final String COMMANDS = "risk.commands";

    private final AssessRiskUseCase assessRisk;

    RiskCommandListener(AssessRiskUseCase assessRisk) {
        this.assessRisk = assessRisk;
    }

    @KafkaListener(topics = COMMANDS)
    void on(ConsumerRecord<String, Object> record) {
        var messageId = OutboxHeaders.messageIdOf(record.headers());
        switch (record.value()) {
            case AssessRisk command ->
                assessRisk.assess(new AssessRiskCommand(
                        messageId,
                        command.getPaymentId(),
                        command.getDebtorAccountId(),
                        command.getAmountMinorUnits(),
                        command.getCurrency()));
            default ->
                throw new IllegalArgumentException(
                        "Unsupported risk command " + record.value().getClass().getName());
        }
    }
}
