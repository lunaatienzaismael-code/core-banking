package com.ledgerflow.ledger.adapter.out.messaging;

import com.ledgerflow.ledger.application.port.out.ReservationReplies;
import com.ledgerflow.ledger.domain.TransactionId;
import com.ledgerflow.messaging.FundsReserved;
import com.ledgerflow.messaging.RefusalReason;
import com.ledgerflow.messaging.ReservationRefused;
import com.ledgerflow.messaging.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

/** Writes replies to {@code ledger.replies} through the outbox, keyed by payment (ADR 0013). */
@Component
class ReservationRepliesOutboxAdapter implements ReservationReplies {

    static final String REPLIES = "ledger.replies";

    private final OutboxWriter outbox;

    ReservationRepliesOutboxAdapter(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void fundsReserved(String paymentId, TransactionId transaction) {
        outbox.write(
                REPLIES,
                paymentId,
                FundsReserved.newBuilder()
                        .setPaymentId(paymentId)
                        .setTransactionId(transaction.value().toString())
                        .build());
    }

    @Override
    public void reservationRefused(String paymentId, Refusal reason, String detail) {
        outbox.write(
                REPLIES,
                paymentId,
                ReservationRefused.newBuilder()
                        .setPaymentId(paymentId)
                        .setReason(RefusalReason.valueOf(reason.name()))
                        .setDetail(detail)
                        .build());
    }
}
