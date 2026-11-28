package com.ledgerflow.risk.domain;

/**
 * Velocity limit (ADR 0014): a debtor may have at most {@code maxTransfers} payments assessed per window. The
 * count comes from the counter store and already includes the payment being assessed, so the
 * {@code maxTransfers + 1}-th payment of a window is the first one rejected.
 */
public final class VelocityRule {

    private final long maxTransfers;

    public VelocityRule(long maxTransfers) {
        if (maxTransfers <= 0) {
            throw new IllegalArgumentException("The velocity limit must be positive: " + maxTransfers);
        }
        this.maxTransfers = maxTransfers;
    }

    /** @param transfersInWindow payments of the debtor in the current window, this one included */
    public RiskDecision assess(long transfersInWindow) {
        if (transfersInWindow > maxTransfers) {
            return new RiskDecision.Rejected(
                    RejectionReason.VELOCITY_LIMIT_EXCEEDED,
                    "Transfer " + transfersInWindow + " in the window exceeds the limit of " + maxTransfers);
        }
        return new RiskDecision.Approved();
    }
}
