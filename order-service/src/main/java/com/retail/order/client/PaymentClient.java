package com.retail.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/internal/payments")
    PaymentData charge(@RequestHeader("X-Internal-Key") String key, @RequestBody ChargeRequest request);

    @GetMapping("/internal/payments/{paymentId}")
    PaymentData get(@RequestHeader("X-Internal-Key") String key, @PathVariable("paymentId") Long paymentId);

    @GetMapping("/internal/payments/by-order/{orderId}")
    List<PaymentData> byOrder(@RequestHeader("X-Internal-Key") String key, @PathVariable("orderId") Long orderId);

    @PostMapping("/internal/payments/{paymentId}/refund")
    RefundData refund(@RequestHeader("X-Internal-Key") String key, @PathVariable("paymentId") Long paymentId,
                      @RequestBody RefundBody body);

    record ChargeRequest(Long orderId, Long customerId, BigDecimal amount, String currency, String method,
                         String paymentToken, String idempotencyKey) {
        @Override
        public String toString() {                              // the token is a credential: never print it
            return "ChargeRequest[orderId=" + orderId + ", amount=" + amount + " " + currency + "]";
        }
    }

    /** status: INITIATED, SUCCESS, FAILED, REFUNDED, PARTIALLY_REFUNDED (kept as text on purpose). */
    record PaymentData(Long id, Long orderId, BigDecimal amount, String status, String failureReason) {}

    record RefundBody(String idempotencyKey, BigDecimal amount, String reason) {}

    /** status: PENDING, SUCCESS, FAILED. */
    record RefundData(Long id, String status, String paymentStatus) {}
}