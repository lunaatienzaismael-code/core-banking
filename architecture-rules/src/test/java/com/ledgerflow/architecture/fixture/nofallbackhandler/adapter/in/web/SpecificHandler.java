package com.ledgerflow.architecture.fixture.nofallbackhandler.adapter.in.web;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SpecificHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public String badArgument() {
        return "400";
    }
}
