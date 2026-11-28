package com.ledgerflow.risk.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Risk rule settings (ADR 0014), under {@code ledgerflow.risk}.
 *
 * @param amountLimitMinorUnits payments above this amount are rejected
 * @param currency ISO-4217 code the limit is expressed in; payments in another currency are malformed
 * @param velocity the per-debtor velocity limit
 */
@ConfigurationProperties("ledgerflow.risk")
record RiskProperties(long amountLimitMinorUnits, String currency, Velocity velocity) {

    /**
     * @param maxTransfers payments one debtor may have assessed per window; the next one is rejected
     * @param window length of the fixed window
     */
    record Velocity(long maxTransfers, Duration window) {}
}
