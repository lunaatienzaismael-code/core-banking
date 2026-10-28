package com.ledgerflow.payment.adapter.in.messaging;

import com.ledgerflow.messaging.FundsReserved;
import com.ledgerflow.messaging.PaymentInitiated;
import com.ledgerflow.messaging.ReservationRefused;
import com.ledgerflow.messaging.TransferPosted;
import com.ledgerflow.payment.application.port.in.SagaInput;
import com.ledgerflow.payment.domain.PaymentId;
import java.util.UUID;

/**
 * Avro contract → saga input. A message of another type, or with an id that is not a UUID, throws: it is a poison
 * message, which T-031 sends to the dead-letter topic instead of retrying.
 */
final class SagaInputMapper {

    private SagaInputMapper() {}

    static SagaInput toInput(UUID messageId, Object message) {
        return switch (message) {
            case PaymentInitiated event -> new SagaInput.PaymentInitiated(messageId, paymentId(event.getPaymentId()));
            case FundsReserved reply -> new SagaInput.FundsReserved(messageId, paymentId(reply.getPaymentId()));
            case ReservationRefused reply ->
                new SagaInput.ReservationRefused(
                        messageId,
                        paymentId(reply.getPaymentId()),
                        reply.getReason().name(),
                        reply.getDetail());
            case TransferPosted reply -> new SagaInput.TransferPosted(messageId, paymentId(reply.getPaymentId()));
            default ->
                throw new IllegalArgumentException(
                        "Unsupported saga message " + message.getClass().getName());
        };
    }

    private static PaymentId paymentId(String value) {
        return new PaymentId(UUID.fromString(value));
    }
}
