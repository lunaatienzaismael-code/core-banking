package com.ledgerflow.architecture.fixture.fallbackbyparameter.adapter.in.web;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class Handler {
    @ExceptionHandler
    public String unexpected(Exception exception) {
        return "500";
    }
}
