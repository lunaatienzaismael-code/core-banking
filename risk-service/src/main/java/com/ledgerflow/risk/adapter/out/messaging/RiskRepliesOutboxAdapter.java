package com.ledgerflow.risk.adapter.out.messaging;

import com.ledgerflow.messaging.RiskApproved;
import com.ledgerflow.messaging.RiskRejected;
import com.ledgerflow.messaging.RiskRejectionReason;
import com.ledgerflow.messaging.outbox.OutboxWriter;
import com.ledgerflow.risk.application.port.out.RiskReplies;
import com.ledgerflow.risk.domain.RejectionReason;
import org.springframework.stereotype.Component;

/** Writes replies to {@code risk.replies} through the outbox, keyed by payment (ADR 0013). */
@Component
class RiskRepliesOutboxAdapter implements RiskReplies {

    static final String REPLIES = "risk.replies";

    private final OutboxWriter outbox;

    RiskRepliesOutboxAdapter(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void approved(String paymentId) {
        outbox.write(
                REPLIES,
                paymentId,
                RiskApproved.newBuilder().setPaymentId(paymentId).build());
    }

    @Override
    public void rejected(String paymentId, RejectionReason reason, String detail) {
        outbox.write(
                REPLIES,
                paymentId,
                RiskRejected.newBuilder()
                        .setPaymentId(paymentId)
                        .setReason(RiskRejectionReason.valueOf(reason.name()))
                        .setDetail(detail)
                        .build());
    }
}
