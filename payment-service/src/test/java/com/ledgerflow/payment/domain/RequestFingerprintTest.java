package com.ledgerflow.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    private static final AccountId DEBTOR = new AccountId(UUID.fromString("3f1c2b7e-0000-4000-8000-000000000001"));
    private static final AccountId CREDITOR = new AccountId(UUID.fromString("3f1c2b7e-0000-4000-8000-000000000002"));
    private static final AccountId OTHER = new AccountId(UUID.fromString("3f1c2b7e-0000-4000-8000-000000000003"));

    @Test
    @DisplayName("B2 the fingerprint is SHA-256 of the canonical fields, as lowercase hex")
    void fingerprintIsSha256OfTheCanonicalFields() throws Exception {
        var expected = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(("3f1c2b7e-0000-4000-8000-000000000001|3f1c2b7e-0000-4000-8000-000000000002|2500|EUR")
                                .getBytes(StandardCharsets.UTF_8)));

        assertThat(RequestFingerprint.of(DEBTOR, CREDITOR, Money.of(2_500, "EUR"))
                        .hex())
                .isEqualTo(expected)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void equalRequestsHaveEqualFingerprints() {
        assertThat(RequestFingerprint.of(DEBTOR, CREDITOR, Money.of(2_500, "EUR")))
                .isEqualTo(RequestFingerprint.of(DEBTOR, CREDITOR, Money.of(2_500, "EUR")));
    }

    @Test
    void everyFieldChangesTheFingerprint() {
        var base = RequestFingerprint.of(DEBTOR, CREDITOR, Money.of(2_500, "EUR"));

        assertThat(RequestFingerprint.of(OTHER, CREDITOR, Money.of(2_500, "EUR")))
                .isNotEqualTo(base);
        assertThat(RequestFingerprint.of(DEBTOR, OTHER, Money.of(2_500, "EUR"))).isNotEqualTo(base);
        assertThat(RequestFingerprint.of(DEBTOR, CREDITOR, Money.of(2_501, "EUR")))
                .isNotEqualTo(base);
        assertThat(RequestFingerprint.of(DEBTOR, CREDITOR, Money.of(2_500, "USD")))
                .isNotEqualTo(base);
        assertThat(RequestFingerprint.of(CREDITOR, DEBTOR, Money.of(2_500, "EUR")))
                .isNotEqualTo(base);
    }
}
