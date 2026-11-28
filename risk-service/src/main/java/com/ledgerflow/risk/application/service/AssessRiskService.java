package com.ledgerflow.risk.application.service;

import com.ledgerflow.risk.application.port.in.AssessRiskCommand;
import com.ledgerflow.risk.application.port.in.AssessRiskUseCase;
import com.ledgerflow.risk.application.port.out.MessageInbox;
import com.ledgerflow.risk.application.port.out.RiskReplies;
import com.ledgerflow.risk.application.port.out.VelocityCounter;
import com.ledgerflow.risk.domain.AmountRule;
import com.ledgerflow.risk.domain.RiskDecision;
import com.ledgerflow.risk.domain.VelocityRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One transaction per message: record it in the inbox, apply the rules, write the reply to the outbox (ADR 0013,
 * ADR 0014). Rules run in order; amount first, so an oversized payment never touches Redis, then velocity. The inbox check comes before the
 * counter, so a redelivered message does not count twice. If Redis is down the exception rolls the transaction back
 * and the message is retried; it is never approved without the check.
 */
@Service
class AssessRiskService implements AssessRiskUseCase {

    private final MessageInbox inbox;
    private final AmountRule amountRule;
    private final VelocityRule velocityRule;
    private final VelocityCounter velocity;
    private final RiskReplies replies;

    AssessRiskService(
            MessageInbox inbox,
            AmountRule amountRule,
            VelocityRule velocityRule,
            VelocityCounter velocity,
            RiskReplies replies) {
        this.inbox = inbox;
        this.amountRule = amountRule;
        this.velocityRule = velocityRule;
        this.velocity = velocity;
        this.replies = replies;
    }

    @Override
    @Transactional
    public void assess(AssessRiskCommand command) {
        if (!inbox.firstDelivery(command.messageId())) {
            return;
        }
        var decision = amountRule.assess(command.amountMinorUnits(), command.currency());
        if (decision instanceof RiskDecision.Approved) {
            decision = velocityRule.assess(velocity.increment(command.debtorAccountId()));
        }
        switch (decision) {
            case RiskDecision.Approved ignored -> replies.approved(command.paymentId());
            case RiskDecision.Rejected rejected ->
                replies.rejected(command.paymentId(), rejected.reason(), rejected.detail());
        }
    }
}
