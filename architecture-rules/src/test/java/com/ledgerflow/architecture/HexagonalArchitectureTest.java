package com.ledgerflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Proves the rules have teeth: a compliant fixture passes every rule, and each violating fixture is caught by
 * exactly the rule written for it.
 */
class HexagonalArchitectureTest {

    private static final String FIXTURES = "com.ledgerflow.architecture.fixture.";

    @Test
    void compliantServicePassesEveryRule() {
        String base = FIXTURES + "valid";

        assertThatCode(() -> HexagonalArchitecture.of(base).check(importPackage(base)))
                .doesNotThrowAnyException();
    }

    @Test
    void emptyServicePassesEveryRule() {
        String base = FIXTURES + "doesnotexist";

        assertThatCode(() -> HexagonalArchitecture.of(base).check(importPackage(base)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("violations")
    void ruleDetectsItsViolation(String fixture, Function<HexagonalArchitecture, ArchRule> rule) {
        String base = FIXTURES + fixture;

        ArchRule checked = rule.apply(HexagonalArchitecture.of(base));

        assertThat(checked.evaluate(importPackage(base)).hasViolation())
                .as("rule '%s' on fixture '%s'", checked.getDescription(), fixture)
                .isTrue();
    }

    static Stream<Arguments> violations() {
        return Stream.of(
                violation("domainwithspring", HexagonalArchitecture::domainDependsOnlyOnTheJdk),
                violation("applicationwithjpa", HexagonalArchitecture::applicationIsFreeOfInfrastructureFrameworks),
                violation("applicationtoadapter", HexagonalArchitecture::applicationDoesNotDependOnAdaptersOrConfig),
                violation("adapterbypassesport", HexagonalArchitecture::adaptersUsePortsNotServices),
                violation("inboundtooutbound", HexagonalArchitecture::inboundAdaptersDoNotDependOnOutboundAdapters),
                violation("outboundtoinbound", HexagonalArchitecture::outboundAdaptersDoNotDependOnInboundAdapters),
                violation("adapterstoadapters", HexagonalArchitecture::adaptersAreIndependentOfEachOther),
                violation("cycle", HexagonalArchitecture::topLevelPackagesAreFreeOfCycles));
    }

    private static Arguments violation(String fixture, Function<HexagonalArchitecture, ArchRule> rule) {
        return Arguments.of(fixture, rule);
    }

    private static JavaClasses importPackage(String base) {
        return new ClassFileImporter().importPackages(base);
    }
}
