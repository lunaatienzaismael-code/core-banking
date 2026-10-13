package com.ledgerflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dependency rules of ADR 0002 for one service, expressed once and reused by every service module.
 *
 * <p>Expected layout below the service base package: {@code domain}, {@code application.port.in},
 * {@code application.port.out}, {@code application.service}, {@code adapter.in.*}, {@code adapter.out.*}
 * and {@code config}.
 *
 * <p>Beyond dependencies, it also checks the conventions of ADRs 0002, 0004 and 0006 that ArchUnit can see: no floating
 * point in the core, generated web DTOs kept in the web adapter, transactions on use cases only, one domain exception
 * base, JPA mappings in the persistence adapter and a Problem Details fallback in the web adapter.
 *
 * <p>Every rule tolerates an empty layer, so a service can start with only some layers present.
 */
public final class HexagonalArchitecture {

    private static final Set<String> FLOATING_POINT =
            Set.of(double.class.getName(), float.class.getName(), Double.class.getName(), Float.class.getName());
    private static final List<String> TRANSACTIONAL =
            List.of("org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional");
    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String REST_CONTROLLER_ADVICE = "org.springframework.web.bind.annotation.RestControllerAdvice";
    private static final String EXCEPTION_HANDLER = "org.springframework.web.bind.annotation.ExceptionHandler";
    private static final List<String> JPA_MAPPINGS = List.of(
            "jakarta.persistence.Entity", "jakarta.persistence.Embeddable", "jakarta.persistence.MappedSuperclass");

    private final String domain;
    private final String application;
    private final String applicationService;
    private final String adapter;
    private final String adapterIn;
    private final String adapterOut;
    private final String config;
    private final String webAdapter;
    private final String webModel;
    private final String persistenceAdapter;
    private final String domainExceptionBase;
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
        this.webAdapter = basePackage + ".adapter.in.web..";
        this.webModel = basePackage + ".adapter.in.web.model..";
        this.persistenceAdapter = basePackage + ".adapter.out.persistence..";
        this.domainExceptionBase = basePackage + ".domain.DomainException";
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

    /** ADR 0004: money is {@code long} minor units, so no floating point type appears in the core's signatures. */
    public ArchRule coreHasNoFloatingPoint() {
        return classes()
                .that()
                .resideInAnyPackage(domain, application)
                .should(new ArchCondition<JavaClass>("not use double, float, Double or Float in fields or signatures") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        for (JavaField field : javaClass.getFields()) {
                            reportFloatingPoint(field.getRawType(), field.getFullName(), events);
                        }
                        for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                            reportFloatingPoint(codeUnit.getRawReturnType(), codeUnit.getFullName(), events);
                            codeUnit.getRawParameterTypes()
                                    .forEach(type -> reportFloatingPoint(type, codeUnit.getFullName(), events));
                        }
                    }
                })
                .as("domain and application use no floating point types")
                .allowEmptyShould(true);
    }

    private static void reportFloatingPoint(JavaClass type, String member, ConditionEvents events) {
        String name = type.getBaseComponentType().getName();
        if (FLOATING_POINT.contains(name)) {
            events.add(SimpleConditionEvent.violated(member, member + " uses " + name));
        }
    }

    /** Rule 6 and ADR 0008: the generated contract DTOs are used inside the web adapter only. */
    public ArchRule webModelStaysInTheWebAdapter() {
        return classes()
                .that()
                .resideInAPackage(webModel)
                .should()
                .onlyHaveDependentClassesThat()
                .resideInAPackage(webAdapter)
                .as("generated web DTOs are used only in the web adapter")
                .allowEmptyShould(true);
    }

    /** Rule 4: use cases are the transaction boundary. */
    public ArchRule transactionsOnlyOnApplicationServices() {
        ArchRule rule = CompositeArchRule.of(noClasses()
                        .that()
                        .resideOutsideOfPackage(applicationService)
                        .should()
                        .beAnnotatedWith(anyOf(TRANSACTIONAL))
                        .allowEmptyShould(true))
                .and(noMethods()
                        .that()
                        .areDeclaredInClassesThat()
                        .resideOutsideOfPackage(applicationService)
                        .should()
                        .beAnnotatedWith(anyOf(TRANSACTIONAL))
                        .allowEmptyShould(true));
        return rule.as("@Transactional appears only on application services").allowEmptyShould(true);
    }

    /** Rule 7: every domain exception extends the service's {@code domain.DomainException}. */
    public ArchRule domainExceptionsShareOneBase() {
        return classes()
                .that()
                .resideInAPackage(domain)
                .and()
                .areAssignableTo(Throwable.class)
                .should(new ArchCondition<JavaClass>("extend " + domainExceptionBase) {
                    private boolean baseExists;

                    @Override
                    public void init(Collection<JavaClass> allObjectsToTest) {
                        baseExists = allObjectsToTest.stream()
                                .anyMatch(javaClass -> javaClass.getName().equals(domainExceptionBase));
                    }

                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        if (!baseExists) {
                            events.add(SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() + " has no base to extend: declare " + domainExceptionBase));
                        } else if (!javaClass.isAssignableTo(domainExceptionBase)) {
                            events.add(SimpleConditionEvent.violated(
                                    javaClass, javaClass.getName() + " does not extend " + domainExceptionBase));
                        }
                    }
                })
                .as("domain exceptions extend " + domainExceptionBase)
                .allowEmptyShould(true);
    }

    /** Rule 6: JPA mappings belong to the persistence adapter. */
    public ArchRule jpaMappingsOnlyInPersistenceAdapter() {
        return noClasses()
                .that()
                .resideOutsideOfPackage(persistenceAdapter)
                .should()
                .beAnnotatedWith(anyOf(JPA_MAPPINGS))
                .as("@Entity, @Embeddable and @MappedSuperclass live only in the persistence adapter")
                .allowEmptyShould(true);
    }

    /** ADR 0006: every error leaves the API as Problem Details, including unexpected ones. */
    public ArchRule webAdapterHasProblemDetailsFallback() {
        return classes()
                .that()
                .resideInAPackage(webAdapter)
                .should(new ArchCondition<JavaClass>("be covered by a @RestControllerAdvice handling Exception") {
                    private boolean hasController;
                    private boolean hasFallback;

                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        hasController |= javaClass.isAnnotatedWith(REST_CONTROLLER);
                        hasFallback |= javaClass.isAnnotatedWith(REST_CONTROLLER_ADVICE)
                                && javaClass.getMethods().stream().anyMatch(HexagonalArchitecture::handlesAnyException);
                    }

                    @Override
                    public void finish(ConditionEvents events) {
                        if (hasController && !hasFallback) {
                            events.add(SimpleConditionEvent.violated(
                                    webAdapter,
                                    "web adapter has a @RestController but no @RestControllerAdvice with"
                                            + " @ExceptionHandler(Exception.class): unexpected errors would not be"
                                            + " Problem Details"));
                        }
                    }
                })
                .as("the web adapter maps unexpected exceptions to Problem Details")
                .allowEmptyShould(true);
    }

    /** {@code @ExceptionHandler(Exception.class)}, or a handler without value whose parameter is {@code Exception}. */
    private static boolean handlesAnyException(JavaMethod method) {
        return method.tryGetAnnotationOfType(EXCEPTION_HANDLER)
                .map(handler -> {
                    Object value = handler.get("value").orElse(new JavaClass[0]);
                    List<JavaClass> handled = value instanceof JavaClass[] types && types.length > 0
                            ? List.of(types)
                            : method.getRawParameterTypes();
                    return handled.stream().anyMatch(type -> type.getName().equals(Exception.class.getName()));
                })
                .orElse(false);
    }

    private static DescribedPredicate<JavaAnnotation<?>> anyOf(List<String> annotations) {
        return DescribedPredicate.describe(
                "any of " + annotations,
                annotation -> annotations.contains(annotation.getRawType().getName()));
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
                topLevelPackagesAreFreeOfCycles(),
                coreHasNoFloatingPoint(),
                webModelStaysInTheWebAdapter(),
                transactionsOnlyOnApplicationServices(),
                domainExceptionsShareOneBase(),
                jpaMappingsOnlyInPersistenceAdapter(),
                webAdapterHasProblemDetailsFallback());
    }

    /** Checks every rule and reports all violations together. */
    public void check(JavaClasses classes) {
        CompositeArchRule.of(rules()).check(classes);
    }
}
