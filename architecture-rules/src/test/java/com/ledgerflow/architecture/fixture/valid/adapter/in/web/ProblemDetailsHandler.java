package com.ledgerflow.architecture.fixture.valid.adapter.in.web;

import com.ledgerflow.architecture.fixture.valid.domain.InvalidCurrencyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemDetailsHandler {
    @ExceptionHandler(InvalidCurrencyException.class)
    public String invalidCurrency() {
        return "400";
    }

    @ExceptionHandler(Exception.class)
    public String unexpected() {
        return "500";
    }
}
