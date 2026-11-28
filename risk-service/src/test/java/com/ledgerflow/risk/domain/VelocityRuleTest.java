package com.ledgerflow.risk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VelocityRuleTest {

    private final VelocityRule rule = new VelocityRule(3);

    @Test
    @DisplayName("AC5.2 the transfer after the limit is rejected with VELOCITY_LIMIT_EXCEEDED")
    void transferAfterTheLimitIsRejected() {
        var decision = rule.assess(4);

        assertThat(decision).isInstanceOfSatisfying(RiskDecision.Rejected.class, rejected -> {
            assertThat(rejected.reason()).isEqualTo(RejectionReason.VELOCITY_LIMIT_EXCEEDED);
            assertThat(rejected.detail()).contains("4").contains("3");
        });
    }

    @Test
    @DisplayName("AC5.2 the transfer that reaches the limit is approved")
    void transferAtTheLimitIsApproved() {
        assertThat(rule.assess(3)).isEqualTo(new RiskDecision.Approved());
    }

    @Test
    @DisplayName("a velocity limit must be positive")
    void limitMustBePositive() {
        assertThatThrownBy(() -> new VelocityRule(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
