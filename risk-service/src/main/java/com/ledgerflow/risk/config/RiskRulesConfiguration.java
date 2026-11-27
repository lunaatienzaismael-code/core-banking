package com.ledgerflow.risk.config;

import com.ledgerflow.risk.domain.AmountRule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the framework-free domain rules from configuration (ADR 0014). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RiskProperties.class)
class RiskRulesConfiguration {

    @Bean
    AmountRule amountRule(RiskProperties properties) {
        return new AmountRule(properties.amountLimitMinorUnits());
    }
}
