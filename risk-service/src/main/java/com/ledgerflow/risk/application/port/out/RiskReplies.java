package com.ledgerflow.risk.application.port.out;

import com.ledgerflow.risk.domain.RejectionReason;

/** Replies to the orchestrator, written in the transaction of the assessment (ADR 0013). */
public interface RiskReplies {

    void approved(String paymentId);

    void rejected(String paymentId, RejectionReason reason, String detail);
}
