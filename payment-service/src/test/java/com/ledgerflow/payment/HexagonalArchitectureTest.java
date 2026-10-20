package com.ledgerflow.payment;

import com.ledgerflow.architecture.HexagonalArchitecture;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private static final String BASE_PACKAGE = "com.ledgerflow.payment";

    @Test
    void productionCodeFollowsTheHexagonalDependencyRules() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        HexagonalArchitecture.of(BASE_PACKAGE).check(classes);
    }
}
