package com.ledgerflow.ledger.adapter.in.web;

import com.ledgerflow.ledger.domain.InvalidCurrencyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place where exceptions become HTTP responses, as Problem Details (RFC 9457, ADR 0006). Spring MVC's own
 * exceptions (malformed JSON, failed validation, unsupported media type) are handled by the base class.
 */
@RestControllerAdvice
class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidCurrencyException.class)
    ProblemDetail invalidCurrency(InvalidCurrencyException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid currency");
        return problem;
    }
}
