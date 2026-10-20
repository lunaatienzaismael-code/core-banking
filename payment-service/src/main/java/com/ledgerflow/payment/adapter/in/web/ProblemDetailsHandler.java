package com.ledgerflow.payment.adapter.in.web;

import com.ledgerflow.payment.domain.InvalidCurrencyException;
import com.ledgerflow.payment.domain.InvalidPaymentException;
import com.ledgerflow.payment.domain.PaymentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place where exceptions become HTTP responses, as Problem Details (RFC 9457, ADR 0006). Spring MVC's own
 * exceptions (malformed JSON, failed validation, path variable type mismatch, unsupported media type) are handled by
 * the base class.
 */
@RestControllerAdvice
class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    @ExceptionHandler(InvalidCurrencyException.class)
    ProblemDetail invalidCurrency(InvalidCurrencyException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid currency");
        return problem;
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail paymentNotFound(PaymentNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Payment not found");
        return problem;
    }

    /** Well-formed request that breaks a payment rule: 422, never 400 (ADR 0009 HTTP mapping, ADR 0010). */
    @ExceptionHandler(InvalidPaymentException.class)
    ProblemDetail invalidPayment(InvalidPaymentException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setTitle("Invalid payment");
        return problem;
    }

    /** Last resort: log the cause, answer 500 without exposing internals (messages, types, stack traces). */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
}
