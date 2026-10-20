package com.ledgerflow.payment.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.payment.TestcontainersConfiguration;
import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.Money;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentId;
import com.ledgerflow.payment.domain.PaymentStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentPersistenceAdapterIT {

    private static final Instant JAN_15 = Instant.parse("2026-01-15T09:30:00.123456Z");

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savedPaymentIsFoundWithAllItsData() {
        var payment = Payment.initiate(account(), account(), Money.of(2_500, "EUR"), JAN_15);

        payments.save(payment);

        assertThat(payments.findById(payment.id())).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(payment.id());
            assertThat(found.debtor()).isEqualTo(payment.debtor());
            assertThat(found.creditor()).isEqualTo(payment.creditor());
            assertThat(found.amount()).isEqualTo(Money.of(2_500, "EUR"));
            assertThat(found.initiatedAt()).isEqualTo(JAN_15);
            assertThat(found.status()).isEqualTo(PaymentStatus.INITIATED);
            assertThat(found.reason()).isEmpty();
        });
        assertThat(jdbc.queryForObject(
                        "select version from payments where id = ?",
                        Long.class,
                        payment.id().value()))
                .isZero();
    }

    @Test
    void rejectedPaymentIsFoundWithItsStatusAndReason() {
        var payment = Payment.initiate(account(), account(), Money.of(2_500, "EUR"), JAN_15);
        payment.reject("risk refused");

        payments.save(payment);

        assertThat(payments.findById(payment.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(PaymentStatus.REJECTED);
            assertThat(found.reason()).contains("risk refused");
        });
    }

    @Test
    void unknownPaymentIsEmpty() {
        assertThat(payments.findById(PaymentId.random())).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "ck_payments_amount_positive    | 0    | EUR | INITIATED | ",
                "ck_payments_currency           | 100  | eur | INITIATED | ",
                "ck_payments_status             | 100  | EUR | SETTLED   | ",
                "ck_payments_reason             | 100  | EUR | REJECTED  | ",
                "ck_payments_reason             | 100  | EUR | INITIATED | stray",
            })
    void schemaRejectsRowsTheDomainWouldRefuse(
            String constraint, long amount, String currency, String status, String reason) {
        assertThatThrownBy(() -> insert(UUID.randomUUID(), UUID.randomUUID(), amount, currency, status, reason))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @Test
    void schemaRejectsTheSameAccountOnBothSides() {
        var account = UUID.randomUUID();

        assertThatThrownBy(() -> insert(account, account, 100, "EUR", "INITIATED", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_distinct_accounts");
    }

    private void insert(UUID debtor, UUID creditor, long amount, String currency, String status, String reason) {
        jdbc.update(
                "insert into payments (id, debtor_account_id, creditor_account_id, amount, currency, status, reason,"
                        + " initiated_at, version) values (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                UUID.randomUUID(),
                debtor,
                creditor,
                amount,
                currency,
                status,
                reason,
                OffsetDateTime.now());
    }

    private static AccountId account() {
        return new AccountId(UUID.randomUUID());
    }
}
