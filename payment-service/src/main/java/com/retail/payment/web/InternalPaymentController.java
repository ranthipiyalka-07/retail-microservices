package com.retail.payment.web;

import com.retail.payment.domain.PaymentStatus;
import com.retail.payment.domain.RefundStatus;
import com.retail.payment.dto.ChargeRequest;
import com.retail.payment.dto.PaymentResponse;
import com.retail.payment.dto.RefundRequest;
import com.retail.payment.dto.RefundResponse;
import com.retail.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Service-to-service only: not routed by the gateway, protected by X-Internal-Key. */
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private final PaymentService paymentService;

    public InternalPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 200 = settled (SUCCESS or FAILED). 202 = outcome unknown (INITIATED): poll GET /{id}. */
    @PostMapping
    public ResponseEntity<PaymentResponse> charge(@Valid @RequestBody ChargeRequest request) {
        PaymentResponse response = paymentService.charge(request);
        HttpStatus status = response.status() == PaymentStatus.INITIATED ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable Long paymentId) {
        return paymentService.get(paymentId);
    }

    @GetMapping("/by-order/{orderId}")
    public List<PaymentResponse> byOrder(@PathVariable Long orderId) {
        return paymentService.findByOrder(orderId);
    }

    /** 200 = settled (SUCCESS or FAILED). 202 = outcome unknown (PENDING). */
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<RefundResponse> refund(@PathVariable Long paymentId, @Valid @RequestBody RefundRequest request) {
        RefundResponse response = paymentService.refund(paymentId, request);
        HttpStatus status = response.status() == RefundStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(response);
    }
}