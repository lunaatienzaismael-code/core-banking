package com.ledgerflow.ledger.adapter.in.web;

import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.CurrencyMismatchException;
import com.ledgerflow.ledger.domain.DomainException;
import com.ledgerflow.ledger.domain.InsufficientFundsException;
import com.ledgerflow.ledger.domain.InvalidCurrencyException;
import com.ledgerflow.ledger.domain.InvalidTransactionException;
import com.ledgerflow.ledger.domain.TransactionNotFoundException;
import com.ledgerflow.ledger.domain.UnbalancedTransactionException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.stream.Collectors;
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

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(AccountNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Account not found");
        return problem;
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ProblemDetail transactionNotFound(TransactionNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Transaction not found");
        return problem;
    }

    @ExceptionHandler(UnbalancedTransactionException.class)
    ProblemDetail unbalancedTransaction(UnbalancedTransactionException exception) {
        return unprocessable("Unbalanced transaction", exception);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail currencyMismatch(CurrencyMismatchException exception) {
        return unprocessable("Currency mismatch", exception);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException exception) {
        return unprocessable("Insufficient funds", exception);
    }

    @ExceptionHandler(InvalidTransactionException.class)
    ProblemDetail invalidTransaction(InvalidTransactionException exception) {
        return unprocessable("Invalid transaction", exception);
    }

    /**
     * The generated contract interfaces are {@code @Validated}, so a constraint on a header, path or query parameter
     * fails through method validation before the controller runs. That is malformed input: 400.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException exception) {
        var detail = exception.getConstraintViolations().stream()
                .map(violation -> lastNode(violation.getPropertyPath()) + ": " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request");
        return problem;
    }

    private static String lastNode(Path path) {
        String name = null;
        for (var node : path) {
            name = node.getName();
        }
        return name;
    }

    /** Last resort: log the cause, answer 500 without exposing internals (messages, types, stack traces). */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    /** Well-formed request that breaks a ledger rule (ADR 0009 HTTP mapping): 422, never 400. */
    private static ProblemDetail unprocessable(String title, DomainException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
