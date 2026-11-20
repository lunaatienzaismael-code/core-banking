package com.ledgerflow.ledger.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No production endpoint of the ledger carries a method-validation constraint yet, so a test-scope {@code @Validated}
 * controller stands in for one.
 */
@WebMvcTest(ConstraintViolationTest.ConstrainedController.class)
@Import(ConstraintViolationTest.ConstrainedController.class)
class ConstraintViolationTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("a violated method-validation constraint (test-scope @Validated controller) is a 400 Problem Details"
            + " naming the parameter and the constraint")
    void methodValidationViolationIsA400ProblemDetailsNamingParameterAndConstraint() {
        var result = mvc.get().uri("/constrained").queryParam("code", "TOOLONG").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid request");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .startsWith("code:")
                .contains("size must be between 0 and 3");
    }

    @Validated
    @RestController
    static class ConstrainedController {

        @GetMapping("/constrained")
        String constrained(@RequestParam @Size(max = 3) String code) {
            return code;
        }
    }
}
