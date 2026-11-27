package com.ledgerflow.risk.domain;

/**
 * Static amount limit (ADR 0014): a payment above the threshold is rejected, one at or below it passes. Amounts are
 * minor units (ADR 0004); the platform is single-currency (EUR, SEPA), so one threshold serves every payment.
 */
public final class AmountRule {

    private final long limitMinorUnits;

    public AmountRule(long limitMinorUnits) {
        if (limitMinorUnits <= 0) {
            throw new IllegalArgumentException("The amount limit must be positive: " + limitMinorUnits);
        }
        this.limitMinorUnits = limitMinorUnits;
    }

    public RiskDecision assess(long amountMinorUnits) {
        if (amountMinorUnits <= 0) {
            throw new InvalidAssessmentException("The amount must be positive: " + amountMinorUnits);
        }
        if (amountMinorUnits > limitMinorUnits) {
            return new RiskDecision.Rejected(
                    RejectionReason.AMOUNT_LIMIT_EXCEEDED,
                    "Amount " + amountMinorUnits + " exceeds the limit of " + limitMinorUnits + " minor units");
        }
        return new RiskDecision.Approved();
    }
}
