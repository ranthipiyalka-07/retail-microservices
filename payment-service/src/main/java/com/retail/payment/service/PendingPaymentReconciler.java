package com.retail.payment.service;

import com.retail.payment.config.PaymentProperties;
import com.retail.payment.domain.Payment;
import com.retail.payment.domain.PaymentStatus;
import com.retail.payment.domain.Refund;
import com.retail.payment.domain.RefundStatus;
import com.retail.payment.gateway.GatewayAccess;
import com.retail.payment.gateway.GatewayCharge;
import com.retail.payment.gateway.GatewayRefund;
import com.retail.payment.repository.PaymentRepository;
import com.retail.payment.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Safe on several instances: settling is idempotent (the payment row is locked and its status is checked). */
@Component
public class PendingPaymentReconciler {

    private static final Logger log = LoggerFactory.getLogger(PendingPaymentReconciler.class);
    private static final int BATCH_SIZE = 100;

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final GatewayAccess gateway;
    private final PaymentService paymentService;
    private final PaymentProperties props;

    public PendingPaymentReconciler(PaymentRepository payments, RefundRepository refunds, GatewayAccess gateway,
                                    PaymentService paymentService, PaymentProperties props) {
        this.payments = payments;
        this.refunds = refunds;
        this.gateway = gateway;
        this.paymentService = paymentService;
        this.props = props;
    }

    @Scheduled(initialDelayString = "PT45S", fixedDelayString = "${retail.payment.reconcile-delay:PT1M}")
    public void reconcile() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(props.staleAfter());
        Instant giveUpBefore = now.minus(props.giveUpAfter());
        Pageable page = PageRequest.of(0, BATCH_SIZE, Sort.by("id"));

        for (Payment payment : payments.findByStatusAndCreatedAtBefore(PaymentStatus.INITIATED, staleBefore, page)) {
            try {
                Optional<GatewayCharge> found = gateway.findCharge(payment.getIdempotencyKey());
                if (found.isPresent()) {
                    paymentService.settleCharge(payment.getId(), found.get());
                } else if (payment.getCreatedAt().isBefore(giveUpBefore)) {
                    log.warn("Closing paymentId={} as failed: the gateway has no record of it", payment.getId());
                    paymentService.settleCharge(payment.getId(),
                            GatewayCharge.declined("NO_GATEWAY_RECORD", "The gateway never received this charge"));
                }
            } catch (RuntimeException e) {
                log.warn("Could not reconcile paymentId={}: {}", payment.getId(), e.toString());
            }
        }

        for (Refund refund : refunds.findByStatusAndCreatedAtBefore(RefundStatus.PENDING, staleBefore, page)) {
            try {
                Optional<GatewayRefund> found = gateway.findRefund(refund.getIdempotencyKey());
                if (found.isPresent()) {
                    paymentService.settleRefund(refund.getPaymentId(), refund.getId(), found.get());
                } else if (refund.getCreatedAt().isBefore(giveUpBefore)) {
                    log.warn("Closing refundId={} as failed: the gateway has no record of it", refund.getId());
                    paymentService.settleRefund(refund.getPaymentId(), refund.getId(),
                            GatewayRefund.declined("NO_GATEWAY_RECORD", "The gateway never received this refund"));
                }
            } catch (RuntimeException e) {
                log.warn("Could not reconcile refundId={}: {}", refund.getId(), e.toString());
            }
        }
    }
}