package com.ledgerflow.payment.application.service;

import com.ledgerflow.payment.application.port.in.AdvancePaymentSagaUseCase;
import com.ledgerflow.payment.application.port.in.SagaInput;
import com.ledgerflow.payment.application.port.in.SagaInput.FundsReserved;
import com.ledgerflow.payment.application.port.in.SagaInput.PaymentInitiated;
import com.ledgerflow.payment.application.port.in.SagaInput.ReservationRefused;
import com.ledgerflow.payment.application.port.in.SagaInput.TransferPosted;
import com.ledgerflow.payment.application.port.out.LedgerCommands;
import com.ledgerflow.payment.application.port.out.MessageInbox;
import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.domain.PaymentNotFoundException;
import com.ledgerflow.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The payment saga's orchestrator (ADR 0012): one input, one transaction that records the message, applies the ADR 0011
 * transition and writes the next command to the outbox. Each input acts only from the status the ADR 0012 saga table
 * names for it; in any other status (a stale or out-of-order reply) it is recorded and ignored, changing nothing. The
 * ADR 0011 table in the domain still guards every transition underneath.
 */
@Service
class AdvancePaymentSagaService implements AdvancePaymentSagaUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdvancePaymentSagaService.class);

    private final MessageInbox inbox;
    private final PaymentRepository payments;
    private final LedgerCommands ledger;

    AdvancePaymentSagaService(MessageInbox inbox, PaymentRepository payments, LedgerCommands ledger) {
        this.inbox = inbox;
        this.payments = payments;
        this.ledger = ledger;
    }

    @Override
    @Transactional
    public void advance(SagaInput input) {
        if (!inbox.firstDelivery(input.messageId())) {
            return;
        }
        var payment =
                payments.findById(input.paymentId()).orElseThrow(() -> new PaymentNotFoundException(input.paymentId()));
        if (payment.status() != expectedStatusOf(input)) {
            log.info(
                    "Ignoring {} for payment {} in {}",
                    input.getClass().getSimpleName(),
                    payment.id().value(),
                    payment.status());
            return;
        }
        switch (input) {
            case PaymentInitiated ignored -> {
                // Until the risk check exists (Phase 8), every payment is approved (ADR 0012).
                payment.approveRisk();
                ledger.reserveFunds(payment);
            }
            case FundsReserved ignored -> {
                payment.reserveFunds();
                ledger.postTransfer(payment);
            }
            case ReservationRefused refused -> payment.reject(refused.reason() + ": " + refused.detail());
            case TransferPosted ignored -> payment.complete();
        }
        payments.update(payment);
    }

    /** The ADR 0012 saga table: the one status from which each input moves the payment on. */
    private static PaymentStatus expectedStatusOf(SagaInput input) {
        return switch (input) {
            case PaymentInitiated ignored -> PaymentStatus.INITIATED;
            case FundsReserved ignored -> PaymentStatus.RISK_APPROVED;
            case ReservationRefused ignored -> PaymentStatus.RISK_APPROVED;
            case TransferPosted ignored -> PaymentStatus.FUNDS_RESERVED;
        };
    }
}
