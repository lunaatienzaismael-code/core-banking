package com.ledgerflow.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiskContractsTest {

    private static final String PAYMENT_ID = UUID.randomUUID().toString();

    @Test
    @DisplayName("B3 AssessRisk carries what the risk rules need and survives serialization")
    void assessRiskSurvivesSerialization() throws IOException {
        var command = AssessRisk.newBuilder()
                .setPaymentId(PAYMENT_ID)
                .setDebtorAccountId(UUID.randomUUID().toString())
                .setAmountMinorUnits(250_000L)
                .setCurrency("EUR")
                .build();

        assertThat(roundTrip(command, AssessRisk.class)).isEqualTo(command);
    }

    @Test
    @DisplayName("B3 RiskApproved replies for a payment and survives serialization")
    void riskApprovedSurvivesSerialization() throws IOException {
        var reply = RiskApproved.newBuilder().setPaymentId(PAYMENT_ID).build();

        assertThat(roundTrip(reply, RiskApproved.class)).isEqualTo(reply);
    }

    @Test
    @DisplayName("B3 RiskRejected names the rule that rejected and survives serialization")
    void riskRejectedSurvivesSerialization() throws IOException {
        var reply = RiskRejected.newBuilder()
                .setPaymentId(PAYMENT_ID)
                .setReason(RiskRejectionReason.AMOUNT_LIMIT_EXCEEDED)
                .setDetail("Amount 250000 EUR exceeds the limit of 100000")
                .build();

        assertThat(roundTrip(reply, RiskRejected.class)).isEqualTo(reply);
    }

    private static <T extends SpecificRecordBase> T roundTrip(T record, Class<T> type) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(bytes, null);
        new SpecificDatumWriter<>(type).write(record, encoder);
        encoder.flush();
        var decoder = DecoderFactory.get().binaryDecoder(bytes.toByteArray(), null);
        return new SpecificDatumReader<>(type).read(null, decoder);
    }
}
