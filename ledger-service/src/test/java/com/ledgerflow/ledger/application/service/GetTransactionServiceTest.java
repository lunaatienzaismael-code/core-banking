package com.ledgerflow.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ledgerflow.ledger.application.port.in.TransactionView;
import com.ledgerflow.ledger.application.port.out.TransactionRepository;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.PostingId;
import com.ledgerflow.ledger.domain.PostingLine;
import com.ledgerflow.ledger.domain.Side;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.domain.TransactionId;
import com.ledgerflow.ledger.domain.TransactionNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetTransactionServiceTest {

    private static final Instant JAN_15 = Instant.parse("2026-01-15T09:30:00Z");

    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final GetTransactionService service = new GetTransactionService(transactions);

    @Test
    void transactionLinesCarryUnsignedAmountsWithTheirDirectionInRepositoryOrder() {
        var id = TransactionId.random();
        var debit = new PostingLine(PostingId.random(), id, AccountId.random(), Money.of(2_500, "EUR"));
        var credit = new PostingLine(PostingId.random(), id, AccountId.random(), Money.of(-2_500, "EUR"));
        when(transactions.findById(id)).thenReturn(Optional.of(new Transaction(id, JAN_15, List.of(debit, credit))));

        var view = service.transactionOf(id);

        assertThat(view)
                .isEqualTo(new TransactionView(
                        id,
                        JAN_15,
                        List.of(
                                new TransactionView.Line(
                                        debit.id(), debit.accountId(), Money.of(2_500, "EUR"), Side.DEBIT),
                                new TransactionView.Line(
                                        credit.id(), credit.accountId(), Money.of(2_500, "EUR"), Side.CREDIT))));
    }

    @Test
    void unknownTransactionIsReportedAsNotFound() {
        var id = TransactionId.random();
        when(transactions.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transactionOf(id))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessage("Transaction " + id + " does not exist");
    }
}
