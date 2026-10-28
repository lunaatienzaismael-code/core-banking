package com.ledgerflow.ledger.adapter.out.messaging;

import com.ledgerflow.ledger.application.port.out.TransferReplies;
import com.ledgerflow.ledger.domain.TransactionId;
import com.ledgerflow.messaging.FundsReleased;
import com.ledgerflow.messaging.FundsReserved;
import com.ledgerflow.messaging.PostingRefused;
import com.ledgerflow.messaging.RefusalReason;
import com.ledgerflow.messaging.ReservationRefused;
import com.ledgerflow.messaging.TransferPosted;
import com.ledgerflow.messaging.outbox.OutboxWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

/** Writes replies to {@code ledger.replies} through the outbox, keyed by payment (ADR 0013). */
@Component
class TransferRepliesOutboxAdapter implements TransferReplies {

    static final String REPLIES = "ledger.replies";

    private final OutboxWriter outbox;

    TransferRepliesOutboxAdapter(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void fundsReserved(String paymentId, TransactionId transaction) {
        write(
                paymentId,
                FundsReserved.newBuilder()
                        .setPaymentId(paymentId)
                        .setTransactionId(transaction.value().toString())
                        .build());
    }

    @Override
    public void reservationRefused(String paymentId, Refusal reason, String detail) {
        write(
                paymentId,
                ReservationRefused.newBuilder()
                        .setPaymentId(paymentId)
                        .setReason(RefusalReason.valueOf(reason.name()))
                        .setDetail(detail)
                        .build());
    }

    @Override
    public void transferPosted(String paymentId, TransactionId transaction) {
        write(
                paymentId,
                TransferPosted.newBuilder()
                        .setPaymentId(paymentId)
                        .setTransactionId(transaction.value().toString())
                        .build());
    }

    @Override
    public void postingRefused(String paymentId, Refusal reason, String detail) {
        write(
                paymentId,
                PostingRefused.newBuilder()
                        .setPaymentId(paymentId)
                        .setReason(RefusalReason.valueOf(reason.name()))
                        .setDetail(detail)
                        .build());
    }

    @Override
    public void fundsReleased(String paymentId, TransactionId transaction, String reason) {
        write(
                paymentId,
                FundsReleased.newBuilder()
                        .setPaymentId(paymentId)
                        .setTransactionId(transaction.value().toString())
                        .setReason(reason)
                        .build());
    }

    private void write(String paymentId, SpecificRecord reply) {
        outbox.write(REPLIES, paymentId, reply);
    }
}
