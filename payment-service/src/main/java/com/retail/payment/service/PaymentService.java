
package com.retail.payment.service;

import com.retail.common.audit.Audit;
import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.PaymentResultEvent;
import com.retail.payment.config.PaymentProperties;
import com.retail.payment.domain.*;
import com.retail.payment.dto.ChargeRequest;
import com.retail.payment.dto.PaymentResponse;
import com.retail.payment.dto.RefundRequest;
import com.retail.payment.dto.RefundResponse;
import com.retail.payment.exception.ConflictException;
import com.retail.payment.exception.InvalidRequestException;
import com.retail.payment.gateway.*;
import com.retail.payment.repository.PaymentRepository;
import com.retail.payment.repository.PaymentTransactionRepository;
import com.retail.payment.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentService.class);

    private static final List<PaymentStatus> ACTIVE =
            List.of(
                    PaymentStatus.INITIATED,
                    PaymentStatus.SUCCESS,
                    PaymentStatus.PARTIALLY_REFUNDED
            );

    private static final List<RefundStatus> PENDING_OR_DONE =
            List.of(
                    RefundStatus.PENDING,
                    RefundStatus.SUCCESS
            );

    private static final List<RefundStatus> DONE =
            List.of(RefundStatus.SUCCESS);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentTransactionRepository ledger;
    private final GatewayAccess gateway;
    private final ApplicationEventPublisher events;
    private final PaymentProperties props;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public PaymentService(
            PaymentRepository payments,
            RefundRepository refunds,
            PaymentTransactionRepository ledger,
            GatewayAccess gateway,
            ApplicationEventPublisher events,
            PaymentProperties props,
            PlatformTransactionManager txManager) {

        this.payments = payments;
        this.refunds = refunds;
        this.ledger = ledger;
        this.gateway = gateway;
        this.events = events;
        this.props = props;

        this.tx = new TransactionTemplate(txManager);

        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    private record Started(
            PaymentResponse payment,
            boolean callGateway) {
    }

    private record RefundStarted(
            RefundResponse refund,
            String chargeReference,
            boolean callGateway) {
    }

    // ================= CHARGE =================

    public PaymentResponse charge(ChargeRequest request) {

        validate(request);

        String key = request.idempotencyKey().trim();

        // Phase 1: Create or retrieve the payment in a short transaction.
        Started started = begin(request, key);

        if (!started.callGateway()) {
            return started.payment();
        }

        // Phase 2: Call the payment gateway outside the transaction.
        Long paymentId = started.payment().id();

        GatewayCharge result;

        try {
            result = gateway.charge(
                    new ChargeCommand(
                            key,
                            request.orderId(),
                            money(request.amount()),
                            currency(request.currency()),
                            request.method(),
                            request.paymentToken()
                    )
            );

        } catch (GatewayUnavailableException e) {

            log.warn(
                    "Payment gateway unavailable: paymentId={}, orderId={}. Outcome unknown, the payment stays INITIATED.",
                    paymentId,
                    request.orderId()
            );

            return started.payment();
        }

        // Phase 3: Settle the payment in a short transaction.
        return settleCharge(paymentId, result);
    }

    /**
     * Records the gateway's answer.
     *
     * The payment row is locked.
     * Only a payment that is still INITIATED can be changed.
     */
    public PaymentResponse settleCharge(
            Long paymentId,
            GatewayCharge result) {

        return tx.execute(s -> {

            Payment p = payments.findLockedById(paymentId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Payment",
                                    paymentId
                            )
                    );

            // Another request may have already settled this payment.
            if (p.getStatus() != PaymentStatus.INITIATED) {
                return toResponse(p);
            }

            if (result.approved()) {

                p.setStatus(PaymentStatus.SUCCESS);
                p.setGatewayReference(result.reference());
                p.setCardLast4(result.cardLast4());

            } else {

                p.setStatus(PaymentStatus.FAILED);
                p.setFailureReason(
                        truncate(result.responseCode(), 255)
                );
            }

            // Update the existing managed payment entity.
            payments.save(p);

            // Record the charge transaction in the ledger.
            ledger.save(
                    new PaymentTransaction(
                            p.getId(),
                            null,
                            TransactionType.CHARGE,
                            p.getAmount(),
                            result.approved()
                                    ? TransactionStatus.SUCCESS
                                    : TransactionStatus.FAILED,
                            result.responseCode()
                    )
            );

            // Publish the payment result event.
            events.publishEvent(
                    new PaymentResultEvent(
                            UUID.randomUUID(),
                            Instant.now(),
                            p.getOrderId(),
                            p.getId(),
                            result.approved()
                                    ? PaymentResultEvent.Status.SUCCESS
                                    : PaymentResultEvent.Status.FAILED,
                            p.getAmount(),
                            p.getCurrency(),
                            p.getFailureReason()
                    )
            );

            log.info(
                    "Payment settled: paymentId={}, orderId={}, status={}",
                    p.getId(),
                    p.getOrderId(),
                    p.getStatus()
            );

            Audit.record("payment.charge", result.approved() ? Audit.Outcome.SUCCESS : Audit.Outcome.FAILURE, "payment", p.getId(), "orderId=" + p.getOrderId() + " amount=" + p.getAmount() + " " + p.getCurrency());

            return toResponse(p);
        });
    }

    // ================= VALIDATION =================

    private void validate(ChargeRequest r) {

        if (r.method() == PaymentMethod.COD) {
            throw new InvalidRequestException(
                    "Cash on delivery is not handled by payment-service yet"
            );
        }

        if (!currency(r.currency()).equals(
                props.currency().toUpperCase(Locale.ROOT))) {

            throw new InvalidRequestException(
                    "Only " + props.currency() + " payments are supported"
            );
        }

        if (money(r.amount()).compareTo(props.maxAmount()) > 0) {
            throw new InvalidRequestException(
                    "The amount exceeds the maximum of " + props.maxAmount()
            );
        }
    }

    // ================= BEGIN PAYMENT =================

    private Started begin(ChargeRequest request, String key) {

        try {
            return tx.execute(s -> doBegin(request, key));

        } catch (DataIntegrityViolationException first) {

            // A parallel request may have won the unique-key insert.
            // Retry once to find the winning payment.
            try {
                return tx.execute(s -> doBegin(request, key));

            } catch (DataIntegrityViolationException second) {

                throw new ConflictException(
                        "Order " + request.orderId()
                                + " already has a payment in progress or completed"
                );
            }
        }
    }

    private Started doBegin(ChargeRequest r, String key) {

        BigDecimal amount = money(r.amount());
        String currency = currency(r.currency());

        Payment existing = payments.findByIdempotencyKey(key)
                .orElse(null);

        if (existing != null) {

            boolean same =
                    existing.getOrderId().equals(r.orderId())
                            && existing.getCustomerId().equals(r.customerId())
                            && existing.getAmount().compareTo(amount) == 0
                            && existing.getCurrency().equals(currency)
                            && existing.getMethod() == r.method();

            if (!same) {
                throw new ConflictException(
                        "This idempotency key was already used for a different payment request"
                );
            }

            return new Started(
                    toResponse(existing),
                    existing.getStatus() == PaymentStatus.INITIATED
            );
        }

        if (payments.existsByOrderIdAndStatusIn(r.orderId(), ACTIVE)) {

            throw new ConflictException(
                    "Order " + r.orderId()
                            + " already has a payment in progress or completed"
            );
        }

        Payment payment = new Payment();

        payment.setOrderId(r.orderId());
        payment.setCustomerId(r.customerId());
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setMethod(r.method());
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setIdempotencyKey(key);

        payments.saveAndFlush(payment);

        return new Started(toResponse(payment), true);
    }

    // ================= REFUND =================

    public RefundResponse refund(
            Long paymentId,
            RefundRequest request) {

        String key = request.idempotencyKey().trim();

        RefundStarted started =
                beginRefund(paymentId, key, request);

        if (!started.callGateway()) {
            return started.refund();
        }

        GatewayRefund result;

        try {

            result = gateway.refund(
                    new RefundCommand(
                            key,
                            started.chargeReference(),
                            started.refund().amount(),
                            request.reason().trim()
                    )
            );

        } catch (GatewayUnavailableException e) {

            log.warn(
                    "Payment gateway unavailable: refundId={}, paymentId={}. The refund stays PENDING.",
                    started.refund().id(),
                    paymentId
            );

            return started.refund();
        }

        return settleRefund(
                paymentId,
                started.refund().id(),
                result
        );
    }

    /**
     * Locks the payment first, then changes the refund
     * only while it is still PENDING.
     */
    public RefundResponse settleRefund(
            Long paymentId,
            Long refundId,
            GatewayRefund result) {

        return tx.execute(s -> {

            Payment payment = payments.findLockedById(paymentId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Payment",
                                    paymentId
                            )
                    );

            Refund refund = refunds.findById(refundId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Refund",
                                    refundId
                            )
                    );

            if (refund.getStatus() != RefundStatus.PENDING) {
                return toRefundResponse(
                        refund,
                        payment.getStatus()
                );
            }

            if (result.approved()) {

                refund.setStatus(RefundStatus.SUCCESS);
                refund.setGatewayReference(result.reference());

            } else {

                refund.setStatus(RefundStatus.FAILED);
                refund.setFailureReason(
                        truncate(result.responseCode(), 255)
                );
            }

            refunds.saveAndFlush(refund);

            ledger.save(
                    new PaymentTransaction(
                            paymentId,
                            refundId,
                            TransactionType.REFUND,
                            refund.getAmount(),
                            result.approved()
                                    ? TransactionStatus.SUCCESS
                                    : TransactionStatus.FAILED,
                            result.responseCode()
                    )
            );

            if (result.approved()) {

                BigDecimal totalRefunded =
                        refunded(paymentId, DONE);

                payment.setStatus(
                        totalRefunded.compareTo(payment.getAmount()) >= 0
                                ? PaymentStatus.REFUNDED
                                : PaymentStatus.PARTIALLY_REFUNDED
                );

                payments.save(payment);
            }

            log.info(
                    "Refund settled: refundId={}, paymentId={}, status={}",
                    refundId,
                    paymentId,
                    refund.getStatus()
            );

            Audit.record("payment.refund", result.approved() ? Audit.Outcome.SUCCESS : Audit.Outcome.FAILURE, "payment", paymentId, "refundId=" + refundId + " amount=" + refund.getAmount());

            return toRefundResponse(
                    refund,
                    payment.getStatus()
            );
        });
    }

    // ================= BEGIN REFUND =================

    private RefundStarted beginRefund(
            Long paymentId,
            String key,
            RefundRequest request) {

        try {
            return tx.execute(
                    s -> doBeginRefund(paymentId, key, request)
            );

        } catch (DataIntegrityViolationException first) {

            try {
                return tx.execute(
                        s -> doBeginRefund(paymentId, key, request)
                );

            } catch (DataIntegrityViolationException second) {

                throw new ConflictException(
                        "The refund could not be created because of a parallel request. Please retry."
                );
            }
        }
    }

    private RefundStarted doBeginRefund(
            Long paymentId,
            String key,
            RefundRequest request) {

        Refund existing = refunds.findByIdempotencyKey(key)
                .orElse(null);

        if (existing != null) {

            boolean same =
                    existing.getPaymentId().equals(paymentId)
                            && (request.amount() == null
                            || existing.getAmount().compareTo(
                            money(request.amount())
                    ) == 0);

            if (!same) {
                throw new ConflictException(
                        "This idempotency key was already used for a different refund request"
                );
            }

            Payment payment = payments.findById(paymentId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Payment",
                                    paymentId
                            )
                    );

            return new RefundStarted(
                    toRefundResponse(existing, payment.getStatus()),
                    payment.getGatewayReference(),
                    existing.getStatus() == RefundStatus.PENDING
            );
        }

        // Lock the payment so concurrent refunds are checked sequentially.
        Payment payment = payments.findLockedById(paymentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Payment",
                                paymentId
                        )
                );

        if (payment.getStatus() != PaymentStatus.SUCCESS
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {

            throw new ConflictException(
                    "Payment " + paymentId + " is "
                            + payment.getStatus()
                            + " and cannot be refunded"
            );
        }

        BigDecimal refundable =
                payment.getAmount().subtract(
                        refunded(paymentId, PENDING_OR_DONE)
                );

        BigDecimal amount =
                request.amount() == null
                        ? refundable
                        : money(request.amount());

        if (amount.signum() <= 0) {
            throw new ConflictException(
                    "Nothing is left to refund for payment " + paymentId
            );
        }

        if (amount.compareTo(refundable) > 0) {
            throw new ConflictException(
                    "Only " + refundable.toPlainString()
                            + " can still be refunded"
            );
        }

        Refund refund = new Refund();

        refund.setPaymentId(paymentId);
        refund.setAmount(amount);
        refund.setReason(request.reason().trim());
        refund.setStatus(RefundStatus.PENDING);
        refund.setIdempotencyKey(key);

        refunds.saveAndFlush(refund);

        return new RefundStarted(
                toRefundResponse(refund, payment.getStatus()),
                payment.getGatewayReference(),
                true
        );
    }

    // ================= READ OPERATIONS =================

    public PaymentResponse get(Long paymentId) {

        return readTx.execute(s ->
                payments.findById(paymentId)
                        .map(this::toResponse)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Payment",
                                        paymentId
                                )
                        )
        );
    }

    public List<PaymentResponse> findByOrder(Long orderId) {

        return readTx.execute(s ->
                payments.findByOrderIdOrderByIdDesc(orderId)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    // ================= HELPERS =================

    private BigDecimal refunded(
            Long paymentId,
            Collection<RefundStatus> statuses) {

        BigDecimal sum = refunds.sumAmount(paymentId, statuses);

        return sum == null ? BigDecimal.ZERO : sum;
    }

    private PaymentResponse toResponse(Payment p) {

        return new PaymentResponse(
                p.getId(),
                p.getOrderId(),
                p.getCustomerId(),
                p.getAmount(),
                p.getCurrency(),
                p.getMethod(),
                p.getStatus(),
                p.getCardLast4(),
                p.getFailureReason(),
                refunded(p.getId(), DONE),
                p.getCreatedAt()
        );
    }

    private static RefundResponse toRefundResponse(
            Refund r,
            PaymentStatus paymentStatus) {

        return new RefundResponse(
                r.getId(),
                r.getPaymentId(),
                r.getAmount(),
                r.getReason(),
                r.getStatus(),
                r.getFailureReason(),
                paymentStatus,
                r.getCreatedAt()
        );
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String currency(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String truncate(String value, int max) {
        return value == null
                ? null
                : value.substring(0, Math.min(value.length(), max));
    }
}
