package com.ledgerflow.risk.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.risk.application.port.in.AssessRiskCommand;
import com.ledgerflow.risk.application.port.out.MessageInbox;
import com.ledgerflow.risk.application.port.out.RiskReplies;
import com.ledgerflow.risk.domain.AmountRule;
import com.ledgerflow.risk.domain.RejectionReason;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Mockito stands in for the outbound ports only; the rule is the real one. */
class AssessRiskServiceTest {

    private static final String PAYMENT = "11111111-1111-1111-1111-111111111111";

    private final MessageInbox inbox = mock(MessageInbox.class);
    private final RiskReplies replies = mock(RiskReplies.class);
    private final AssessRiskService service = new AssessRiskService(inbox, new AmountRule(100_000), replies);

    @Test
    @DisplayName("AC5.1 a payment above the threshold is answered with a rejection")
    void aboveTheThresholdIsRejected() {
        when(inbox.firstDelivery(any())).thenReturn(true);

        service.assess(command(UUID.randomUUID(), 100_001));

        verify(replies).rejected(eq(PAYMENT), eq(RejectionReason.AMOUNT_LIMIT_EXCEEDED), anyString());
    }

    @Test
    @DisplayName("AC5.1 a payment at the threshold is answered with an approval")
    void atTheThresholdIsApproved() {
        when(inbox.firstDelivery(any())).thenReturn(true);

        service.assess(command(UUID.randomUUID(), 100_000));

        verify(replies).approved(PAYMENT);
    }

    @Test
    @DisplayName("B1 a redelivered message is neither assessed nor answered again")
    void redeliveryChangesNothing() {
        var messageId = UUID.randomUUID();
        when(inbox.firstDelivery(messageId)).thenReturn(false);

        service.assess(command(messageId, 100_001));

        verifyNoInteractions(replies);
    }

    private static AssessRiskCommand command(UUID messageId, long amount) {
        return new AssessRiskCommand(messageId, PAYMENT, "22222222-2222-2222-2222-222222222222", amount, "EUR");
    }
}
