package com.ledgerflow.payment.application.service;

import com.ledgerflow.payment.application.port.in.GetPaymentUseCase;
import com.ledgerflow.payment.application.port.in.PaymentView;
import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.domain.PaymentId;
import com.ledgerflow.payment.domain.PaymentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetPaymentService implements GetPaymentUseCase {

    private final PaymentRepository payments;

    GetPaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentView paymentOf(PaymentId id) {
        return payments.findById(id).map(PaymentView::of).orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
