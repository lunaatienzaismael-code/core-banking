package com.ledgerflow.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Risk rule settings (ADR 0014), under {@code ledgerflow.risk}.
 *
 * @param amountLimitMinorUnits payments above this amount are rejected
 */
@ConfigurationProperties("ledgerflow.risk")
record RiskProperties(long amountLimitMinorUnits) {}
