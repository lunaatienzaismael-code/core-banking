package com.ledgerflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.List;
import java.util.Objects;

/**
 * Dependency rules of ADR 0002 for one service, expressed once and reused by every service module.
 *
 * <p>Expected layout below the service base package: {@code domain}, {@code application.port.in},
 * {@code application.port.out}, {@code application.service}, {@code adapter.in.*}, {@code adapter.out.*}
 * and {@code config}.
 *
 * <p>Every rule tolerates an empty layer, so a service can start with only some layers present.
 */
public final class HexagonalArchitecture {

    private final String domain;
    private final String application;
    private final String applicationService;
    private final String adapter;
    private final String adapterIn;
    private final String adapterOut;
    private final String config;
    private final String basePackage;

    private HexagonalArchitecture(String basePackage) {
        this.basePackage = Objects.requireNonNull(basePackage, "basePackage");
        this.domain = basePackage + ".domain..";
        this.application = basePackage + ".application..";
        this.applicationService = basePackage + ".application.service..";
        this.adapter = basePackage + ".adapter..";
        this.adapterIn = basePackage + ".adapter.in..";
        this.adapterOut = basePackage + ".adapter.out..";
        this.config = basePackage + ".config..";
    }

    public static HexagonalArchitecture of(String basePackage) {
        return new HexagonalArchitecture(basePackage);
    }

    /** Rule 1 and 6: the domain depends on the JDK and itself only. */
    public ArchRule domainDependsOnlyOnTheJdk() {
        return classes()
                .that()
                .resideInAPackage(domain)
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", domain)
                .as("domain depends only on the JDK and on itself")
                .allowEmptyShould(true);
    }

    /** Rule 6: application may use Spring's container and transactions, but no persistence, web or JSON. */
    public ArchRule applicationIsFreeOfInfrastructureFrameworks() {
        return noClasses()
                .that()
                .resideInAPackage(application)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "org.springframework.web..",
                        "org.springframework.http..",
                        "jakarta.servlet..",
                        "com.fasterxml.jackson..",
                        "tools.jackson..")
                .as("application does not use persistence, web or serialization frameworks")
                .allowEmptyShould(true);
    }

    /** Rule 2: application depends inwards only. */
    public ArchRule applicationDoesNotDependOnAdaptersOrConfig() {
        return noClasses()
                .that()
                .resideInAPackage(application)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(adapter, config)
                .as("application does not depend on adapters or config")
                .allowEmptyShould(true);
    }

    /** Rule 3 and 4: adapters reach the application only through ports. */
    public ArchRule adaptersUsePortsNotServices() {
        return noClasses()
                .that()
                .resideInAPackage(adapter)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(applicationService)
                .as("adapters use ports, never application services directly")
                .allowEmptyShould(true);
    }

    /** Rule 3 and 5. */
    public ArchRule inboundAdaptersDoNotDependOnOutboundAdapters() {
        return noClasses()
                .that()
                .resideInAPackage(adapterIn)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(adapterOut)
                .as("inbound adapters do not depend on outbound adapters")
                .allowEmptyShould(true);
    }

    /** Rule 4 and 5. */
    public ArchRule outboundAdaptersDoNotDependOnInboundAdapters() {
        return noClasses()
                .that()
                .resideInAPackage(adapterOut)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(adapterIn)
                .as("outbound adapters do not depend on inbound adapters")
                .allowEmptyShould(true);
    }

    /** Rule 5: adapters of the same direction stay independent too (e.g. web vs messaging). */
    public ArchRule adaptersAreIndependentOfEachOther() {
        return slices().matching(basePackage + ".adapter.(*).(*)..")
                .should()
                .notDependOnEachOther()
                .as("each adapter is independent of every other adapter")
                .allowEmptyShould(true);
    }

    public ArchRule topLevelPackagesAreFreeOfCycles() {
        return slices().matching(basePackage + ".(*)..")
                .should()
                .beFreeOfCycles()
                .as("top-level packages are free of cycles")
                .allowEmptyShould(true);
    }

    public List<ArchRule> rules() {
        return List.of(
                domainDependsOnlyOnTheJdk(),
                applicationIsFreeOfInfrastructureFrameworks(),
                applicationDoesNotDependOnAdaptersOrConfig(),
                adaptersUsePortsNotServices(),
                inboundAdaptersDoNotDependOnOutboundAdapters(),
                outboundAdaptersDoNotDependOnInboundAdapters(),
                adaptersAreIndependentOfEachOther(),
                topLevelPackagesAreFreeOfCycles());
    }

    /** Checks every rule and reports all violations together. */
    public void check(JavaClasses classes) {
        CompositeArchRule.of(rules()).check(classes);
    }
}
