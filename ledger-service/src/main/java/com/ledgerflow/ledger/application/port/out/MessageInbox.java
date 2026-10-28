package com.ledgerflow.ledger.application.port.out;

import java.util.UUID;

/** Remembers which messages were processed, in the transaction of their effect (ADR 0013). */
public interface MessageInbox {

    /** {@code true} the first time a message is seen; {@code false} for a redelivery, which must change nothing. */
    boolean firstDelivery(UUID messageId);
}
