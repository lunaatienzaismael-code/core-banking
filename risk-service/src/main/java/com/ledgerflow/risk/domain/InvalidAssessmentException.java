package com.ledgerflow.risk.domain;

/** A payment to assess is malformed (e.g. a non-positive amount); retrying cannot fix it. */
public class InvalidAssessmentException extends DomainException {

    public InvalidAssessmentException(String message) {
        super(message);
    }
}
