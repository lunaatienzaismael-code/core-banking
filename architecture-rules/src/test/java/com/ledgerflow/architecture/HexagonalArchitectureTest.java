package com.ledgerflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
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
                violation("cycle", HexagonalArchitecture::topLevelPackagesAreFreeOfCycles),
                violation("floatingpoint", HexagonalArchitecture::coreHasNoFloatingPoint),
                violation("webmodelleak", HexagonalArchitecture::webModelStaysInTheWebAdapter),
                violation("transactionaloutsideservice", HexagonalArchitecture::transactionsOnlyOnApplicationServices),
                violation("nodomainexceptionbase", HexagonalArchitecture::domainExceptionsShareOneBase),
                violation("exceptionoutsidebase", HexagonalArchitecture::domainExceptionsShareOneBase),
                violation("entityoutsidepersistence", HexagonalArchitecture::jpaMappingsOnlyInPersistenceAdapter),
                violation("nofallbackhandler", HexagonalArchitecture::webAdapterHasProblemDetailsFallback));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("enumeratedCases")
    void ruleReportsEveryCaseItEnumerates(
            String fixture, Function<HexagonalArchitecture, ArchRule> rule, List<String> offenders) {
        String base = FIXTURES + fixture;

        String report = rule.apply(HexagonalArchitecture.of(base))
                .evaluate(importPackage(base))
                .getFailureReport()
                .toString();

        assertThat(report).contains(offenders);
    }

    static Stream<Arguments> enumeratedCases() {
        return Stream.of(
                Arguments.of(
                        "floatingpoint",
                        (Function<HexagonalArchitecture, ArchRule>) HexagonalArchitecture::coreHasNoFloatingPoint,
                        List.of(
                                "DoubleField.amount uses double",
                                "DoubleArrayField.amounts uses double",
                                "FloatParameter.scale(float) uses float",
                                "BoxedDoubleReturn.rate() uses java.lang.Double",
                                "BoxedFloatConstructorParameter.<init>(java.lang.Float) uses java.lang.Float")),
                Arguments.of(
                        "webmodelleak",
                        (Function<HexagonalArchitecture, ArchRule>) HexagonalArchitecture::webModelStaysInTheWebAdapter,
                        List.of("webmodelleak.config.Settings")),
                Arguments.of(
                        "exceptionoutsidebase",
                        (Function<HexagonalArchitecture, ArchRule>) HexagonalArchitecture::domainExceptionsShareOneBase,
                        List.of("InsufficientFundsException does not extend")),
                Arguments.of(
                        "transactionaloutsideservice",
                        (Function<HexagonalArchitecture, ArchRule>)
                                HexagonalArchitecture::transactionsOnlyOnApplicationServices,
                        List.of(
                                "SpringOnClass",
                                "SpringOnMethod.handle()",
                                "JakartaOnClass",
                                "JakartaOnMethod.execute()")),
                Arguments.of(
                        "entityoutsidepersistence",
                        (Function<HexagonalArchitecture, ArchRule>)
                                HexagonalArchitecture::jpaMappingsOnlyInPersistenceAdapter,
                        List.of("OutboxRecord", "AuditStamp", "BaseRecord")));
    }

    @Test
    void fallbackDeclaredThroughTheHandlerParameterCounts() {
        String base = FIXTURES + "fallbackbyparameter";

        assertThat(HexagonalArchitecture.of(base)
                        .webAdapterHasProblemDetailsFallback()
                        .evaluate(importPackage(base))
                        .hasViolation())
                .isFalse();
    }

    @Test
    void missingFallbackIsReportedWithTheReason() {
        String base = FIXTURES + "nofallbackhandler";

        String report = HexagonalArchitecture.of(base)
                .webAdapterHasProblemDetailsFallback()
                .evaluate(importPackage(base))
                .getFailureReport()
                .toString();

        assertThat(report).contains("no @RestControllerAdvice with @ExceptionHandler(Exception.class)");
    }

    @Test
    void missingDomainExceptionBaseIsReportedRatherThanPassingVacuously() {
        String base = FIXTURES + "nodomainexceptionbase";

        String report = HexagonalArchitecture.of(base)
                .domainExceptionsShareOneBase()
                .evaluate(importPackage(base))
                .getFailureReport()
                .toString();

        assertThat(report).contains("has no base to extend: declare " + base + ".domain.DomainException");
    }

    private static Arguments violation(String fixture, Function<HexagonalArchitecture, ArchRule> rule) {
        return Arguments.of(fixture, rule);
    }

    private static JavaClasses importPackage(String base) {
        return new ClassFileImporter().importPackages(base);
    }
}
