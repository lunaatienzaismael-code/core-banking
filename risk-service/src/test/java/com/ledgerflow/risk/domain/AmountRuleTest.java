package com.ledgerflow.risk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AmountRuleTest {

    private final AmountRule rule = new AmountRule(100_000, "EUR");

    @Test
    @DisplayName("AC5.1 a payment above the limit is rejected with AMOUNT_LIMIT_EXCEEDED")
    void paymentAboveTheLimitIsRejected() {
        var decision = rule.assess(100_001, "EUR");

        assertThat(decision).isInstanceOfSatisfying(RiskDecision.Rejected.class, rejected -> {
            assertThat(rejected.reason()).isEqualTo(RejectionReason.AMOUNT_LIMIT_EXCEEDED);
            assertThat(rejected.detail()).contains("100001").contains("100000");
        });
    }

    @Test
    @DisplayName("AC5.1 a payment exactly at the limit is approved")
    void paymentAtTheLimitIsApproved() {
        assertThat(rule.assess(100_000, "EUR")).isEqualTo(new RiskDecision.Approved());
    }

    @Test
    @DisplayName("AC5.1 a payment below the limit is approved")
    void paymentBelowTheLimitIsApproved() {
        assertThat(rule.assess(1, "EUR")).isEqualTo(new RiskDecision.Approved());
    }

    @Test
    @DisplayName("a non-positive amount is a malformed assessment, not a decision")
    void nonPositiveAmountIsInvalid() {
        assertThatThrownBy(() -> rule.assess(0, "EUR")).isInstanceOf(InvalidAssessmentException.class);
        assertThatThrownBy(() -> rule.assess(-5, "EUR")).isInstanceOf(InvalidAssessmentException.class);
    }

    @Test
    @DisplayName("ADR 0004 a payment in another currency is never compared with the limit")
    void otherCurrencyIsInvalid() {
        assertThatThrownBy(() -> rule.assess(1, "USD")).isInstanceOf(InvalidAssessmentException.class);
    }

    @Test
    @DisplayName("a rule without a positive limit cannot be built")
    void limitMustBePositive() {
        assertThatThrownBy(() -> new AmountRule(0, "EUR")).isInstanceOf(IllegalArgumentException.class);
    }
}
