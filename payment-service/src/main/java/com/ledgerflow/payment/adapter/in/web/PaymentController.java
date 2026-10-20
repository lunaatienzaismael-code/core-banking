package com.ledgerflow.payment.adapter.in.web;

import com.ledgerflow.payment.adapter.in.web.api.PaymentsApi;
import com.ledgerflow.payment.adapter.in.web.model.InitiatePaymentRequestDto;
import com.ledgerflow.payment.adapter.in.web.model.PaymentDto;
import com.ledgerflow.payment.application.port.in.GetPaymentUseCase;
import com.ledgerflow.payment.application.port.in.InitiatePaymentUseCase;
import com.ledgerflow.payment.domain.PaymentId;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Implements the generated contract interface (ADR 0008); routes, payloads and validation come from the contract. */
@RestController
class PaymentController implements PaymentsApi {

    private final InitiatePaymentUseCase initiatePayment;
    private final GetPaymentUseCase getPayment;

    PaymentController(InitiatePaymentUseCase initiatePayment, GetPaymentUseCase getPayment) {
        this.initiatePayment = initiatePayment;
        this.getPayment = getPayment;
    }

    @Override
    public ResponseEntity<PaymentDto> initiatePayment(InitiatePaymentRequestDto request) {
        var payment = initiatePayment.initiate(PaymentWebMapper.toCommand(request));
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(payment.id().value())
                .toUri();
        return ResponseEntity.created(location).body(PaymentWebMapper.toDto(payment));
    }

    @Override
    public ResponseEntity<PaymentDto> getPayment(UUID paymentId) {
        return ResponseEntity.ok(PaymentWebMapper.toDto(getPayment.paymentOf(new PaymentId(paymentId))));
    }
}
