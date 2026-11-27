package com.ledgerflow.risk.domain;

/**
 * Static amount limit (ADR 0014): a payment above the threshold is rejected, one at or below it passes. Amounts are
 * minor units (ADR 0004) and are never compared across currencies: the limit is in one configured currency, and a
 * payment in another is a malformed assessment, not a decision.
 */
public final class AmountRule {

    private final long limitMinorUnits;
    private final String currency;

    public AmountRule(long limitMinorUnits, String currency) {
        if (limitMinorUnits <= 0) {
            throw new IllegalArgumentException("The amount limit must be positive: " + limitMinorUnits);
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("The limit needs a currency");
        }
        this.limitMinorUnits = limitMinorUnits;
        this.currency = currency;
    }

    public RiskDecision assess(long amountMinorUnits, String paymentCurrency) {
        if (!currency.equals(paymentCurrency)) {
            throw new InvalidAssessmentException(
                    "The amount limit is in " + currency + ", the payment is in " + paymentCurrency);
        }
        if (amountMinorUnits <= 0) {
            throw new InvalidAssessmentException("The amount must be positive: " + amountMinorUnits);
        }
        if (amountMinorUnits > limitMinorUnits) {
            return new RiskDecision.Rejected(
                    RejectionReason.AMOUNT_LIMIT_EXCEEDED,
                    "Amount " + amountMinorUnits + " exceeds the limit of " + limitMinorUnits + " " + currency
                            + " minor units");
        }
        return new RiskDecision.Approved();
    }
}
