package com.ledgerflow.payment.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 of an initiation request's meaning, as lowercase hex (ADR 0010). Built from the parsed fields in a fixed order,
 * so JSON field order and whitespace never make two equal requests differ.
 */
public record RequestFingerprint(String hex) {

    public RequestFingerprint {
        Objects.requireNonNull(hex, "hex");
    }

    public static RequestFingerprint of(AccountId debtor, AccountId creditor, Money amount) {
        var canonical = String.join(
                "|",
                debtor.value().toString(),
                creditor.value().toString(),
                Long.toString(amount.amount()),
                amount.currency().getCurrencyCode());
        return new RequestFingerprint(HexFormat.of().formatHex(sha256(canonical)));
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform must provide SHA-256 (MessageDigest Javadoc).
            throw new IllegalStateException(e);
        }
    }
}
