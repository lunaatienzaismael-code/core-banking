package com.ledgerflow.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Risk rule settings (ADR 0014), under {@code ledgerflow.risk}.
 *
 * @param amountLimitMinorUnits payments above this amount are rejected
 * @param currency ISO-4217 code the limit is expressed in; payments in another currency are malformed
 */
@ConfigurationProperties("ledgerflow.risk")
record RiskProperties(long amountLimitMinorUnits, String currency) {}
