package com.ledgerflow.risk.application.port.out;

import java.util.UUID;

/** Counts payments per debtor in a fixed window (ADR 0014). Holds derived, expirable counters only. */
public interface VelocityCounter {

    /**
     * Atomically counts one more payment of the debtor and returns the count in the current window, this one
     * included. Counting is idempotent per message: the same {@code messageId} is counted once per window, so a
     * retry after a rolled-back transaction does not count the payment twice (B1). Throws when the store is unavailable: the assessment must fail and retry, never approve.
     */
    long increment(String debtorAccountId, UUID messageId);
}
