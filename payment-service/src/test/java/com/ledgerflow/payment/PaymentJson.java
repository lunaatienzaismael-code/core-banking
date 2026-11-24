package com.ledgerflow.payment;

import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Test helper: compares payment bodies without the fields the background saga may already have changed. */
final class PaymentJson {

    private PaymentJson() {}

    /** The payment without {@code status} and {@code reason}, which move as the saga advances (ADR 0011). */
    static JsonNode withoutProgress(JsonNode payment) {
        return ((ObjectNode) payment.deepCopy()).remove(List.of("status", "reason"));
    }
}
