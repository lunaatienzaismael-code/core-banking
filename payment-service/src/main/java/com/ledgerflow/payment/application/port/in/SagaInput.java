package com.ledgerflow.payment.application.port.in;

import com.ledgerflow.payment.domain.PaymentId;
import java.util.Objects;
import java.util.UUID;

/**
 * A message that moves a payment's saga on (ADR 0012): the payment's own event or a ledger reply. Each carries the id
 * of the message it came in, so a redelivery is recognised (ADR 0013).
 */
public sealed interface SagaInput {

    UUID messageId();

    PaymentId paymentId();

    /** The payment was initiated; the saga approves risk and asks the ledger to reserve funds. */
    record PaymentInitiated(UUID messageId, PaymentId paymentId) implements SagaInput {
        public PaymentInitiated {
            requireIds(messageId, paymentId);
        }
    }

    /** The ledger holds the debtor's funds. */
    record FundsReserved(UUID messageId, PaymentId paymentId) implements SagaInput {
        public FundsReserved {
            requireIds(messageId, paymentId);
        }
    }

    /** The ledger reserved nothing, for {@code reason} (e.g. INSUFFICIENT_FUNDS), explained by {@code detail}. */
    record ReservationRefused(UUID messageId, PaymentId paymentId, String reason, String detail) implements SagaInput {
        public ReservationRefused {
            requireIds(messageId, paymentId);
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /** The ledger moved the reserved amount to the creditor. */
    record TransferPosted(UUID messageId, PaymentId paymentId) implements SagaInput {
        public TransferPosted {
            requireIds(messageId, paymentId);
        }
    }

    private static void requireIds(UUID messageId, PaymentId paymentId) {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(paymentId, "paymentId");
    }
}
