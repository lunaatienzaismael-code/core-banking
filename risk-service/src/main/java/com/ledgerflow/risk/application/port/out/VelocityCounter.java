package com.ledgerflow.risk.application.port.out;

/** Counts payments per debtor in a fixed window (ADR 0014). Holds derived, expirable counters only. */
public interface VelocityCounter {

    /**
     * Atomically counts one more payment of the debtor and returns the count in the current window, this one
     * included. Throws when the store is unavailable: the assessment must fail and retry, never approve.
     */
    long increment(String debtorAccountId);
}
