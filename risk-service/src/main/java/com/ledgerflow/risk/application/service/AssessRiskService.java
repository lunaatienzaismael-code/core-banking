package com.ledgerflow.risk.application.service;

import com.ledgerflow.risk.application.port.in.AssessRiskCommand;
import com.ledgerflow.risk.application.port.in.AssessRiskUseCase;
import com.ledgerflow.risk.application.port.out.MessageInbox;
import com.ledgerflow.risk.application.port.out.RiskReplies;
import com.ledgerflow.risk.domain.AmountRule;
import com.ledgerflow.risk.domain.RiskDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One transaction per message: record it in the inbox, apply the rules, write the reply to the outbox (ADR 0013,
 * ADR 0014). Rules run in order; today only the amount rule exists.
 */
@Service
class AssessRiskService implements AssessRiskUseCase {

    private final MessageInbox inbox;
    private final AmountRule amountRule;
    private final RiskReplies replies;

    AssessRiskService(MessageInbox inbox, AmountRule amountRule, RiskReplies replies) {
        this.inbox = inbox;
        this.amountRule = amountRule;
        this.replies = replies;
    }

    @Override
    @Transactional
    public void assess(AssessRiskCommand command) {
        if (!inbox.firstDelivery(command.messageId())) {
            return;
        }
        switch (amountRule.assess(command.amountMinorUnits(), command.currency())) {
            case RiskDecision.Approved ignored -> replies.approved(command.paymentId());
            case RiskDecision.Rejected rejected ->
                replies.rejected(command.paymentId(), rejected.reason(), rejected.detail());
        }
    }
}
