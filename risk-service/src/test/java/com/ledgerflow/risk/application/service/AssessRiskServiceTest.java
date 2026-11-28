package com.ledgerflow.risk.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.risk.application.port.in.AssessRiskCommand;
import com.ledgerflow.risk.application.port.out.MessageInbox;
import com.ledgerflow.risk.application.port.out.RiskReplies;
import com.ledgerflow.risk.application.port.out.VelocityCounter;
import com.ledgerflow.risk.domain.AmountRule;
import com.ledgerflow.risk.domain.RejectionReason;
import com.ledgerflow.risk.domain.VelocityRule;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Mockito stands in for the outbound ports only; the rule is the real one. */
class AssessRiskServiceTest {

    private static final String DEBTOR = "22222222-2222-2222-2222-222222222222";
    private static final String PAYMENT = "11111111-1111-1111-1111-111111111111";

    private final MessageInbox inbox = mock(MessageInbox.class);
    private final RiskReplies replies = mock(RiskReplies.class);
    private final VelocityCounter velocity = mock(VelocityCounter.class);
    private final AssessRiskService service =
            new AssessRiskService(inbox, new AmountRule(100_000, "EUR"), new VelocityRule(3), velocity, replies);

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
    @DisplayName("AC5.2 the transfer after the limit is rejected with VELOCITY_LIMIT_EXCEEDED")
    void transferAfterTheVelocityLimitIsRejected() {
        when(inbox.firstDelivery(any())).thenReturn(true);
        when(velocity.increment(eq(DEBTOR), any())).thenReturn(4L);

        service.assess(command(UUID.randomUUID(), 1_000));

        verify(replies).rejected(eq(PAYMENT), eq(RejectionReason.VELOCITY_LIMIT_EXCEEDED), anyString());
    }

    @Test
    @DisplayName("AC5.2 the transfer that reaches the limit is still approved")
    void transferThatReachesTheVelocityLimitIsApproved() {
        when(inbox.firstDelivery(any())).thenReturn(true);
        when(velocity.increment(eq(DEBTOR), any())).thenReturn(3L);

        service.assess(command(UUID.randomUUID(), 1_000));

        verify(replies).approved(PAYMENT);
    }

    @Test
    @DisplayName("ADR 0014 an oversized payment is rejected without counting it against the debtor")
    void oversizedPaymentDoesNotTouchTheCounter() {
        when(inbox.firstDelivery(any())).thenReturn(true);

        service.assess(command(UUID.randomUUID(), 100_001));

        verifyNoInteractions(velocity);
    }

    @Test
    @DisplayName("B2 when the counter store fails the assessment fails and nothing is answered")
    void counterFailureNeverApproves() {
        when(inbox.firstDelivery(any())).thenReturn(true);
        when(velocity.increment(eq(DEBTOR), any())).thenThrow(new IllegalStateException("Redis is down"));

        assertThatThrownBy(() -> service.assess(command(UUID.randomUUID(), 1_000)))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(replies);
    }

    @Test
    @DisplayName("B1 a redelivered message is neither assessed nor answered again")
    void redeliveryChangesNothing() {
        var messageId = UUID.randomUUID();
        when(inbox.firstDelivery(messageId)).thenReturn(false);

        service.assess(command(messageId, 100_001));

        verifyNoInteractions(replies);
        verify(velocity, never()).increment(any(), any());
    }

    private static AssessRiskCommand command(UUID messageId, long amount) {
        return new AssessRiskCommand(messageId, PAYMENT, DEBTOR, amount, "EUR");
    }
}
