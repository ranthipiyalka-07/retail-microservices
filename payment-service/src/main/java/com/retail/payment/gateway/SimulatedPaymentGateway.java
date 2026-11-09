package com.retail.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Development gateway: no real money, state kept in memory (lost on restart). Active unless configured otherwise. */
@Component
@ConditionalOnProperty(name = "retail.payment.gateway", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);
    private static final Pattern LAST4 = Pattern.compile("_(\\d{4})$");

    private final Map<String, GatewayCharge> charges = new ConcurrentHashMap<>();
    private final Map<String, GatewayRefund> refunds = new ConcurrentHashMap<>();

    public SimulatedPaymentGateway() {
        log.warn("SIMULATED payment gateway is active: no real money moves. Never use it outside development.");
    }

    @Override
    public GatewayCharge charge(ChargeCommand command) {
        GatewayCharge known = charges.get(command.idempotencyKey());
        if (known != null) {
            return known;                                           // idempotent replay, like a real gateway
        }
        String token = command.paymentToken().toLowerCase(Locale.ROOT);
        if (token.startsWith("tok_error")) {
            throw new GatewayUnavailableException("Simulated gateway outage");   // nothing recorded
        }
        GatewayCharge result = decide(token);
        charges.put(command.idempotencyKey(), result);
        if (token.startsWith("tok_timeout")) {
            throw new GatewayUnavailableException("Simulated timeout: the charge was processed, the response was lost");
        }
        return result;
    }

    @Override
    public Optional<GatewayCharge> findCharge(String idempotencyKey) {
        return Optional.ofNullable(charges.get(idempotencyKey));
    }

    @Override
    public GatewayRefund refund(RefundCommand command) {
        return refunds.computeIfAbsent(command.idempotencyKey(), key ->
                command.chargeReference() != null && command.chargeReference().startsWith("sim_ch_")
                        ? GatewayRefund.ok("sim_re_" + shortId())
                        : GatewayRefund.declined("unknown_charge", "The gateway does not know this charge"));
    }

    @Override
    public Optional<GatewayRefund> findRefund(String idempotencyKey) {
        return Optional.ofNullable(refunds.get(idempotencyKey));
    }

    private static GatewayCharge decide(String token) {
        if (token.startsWith("tok_ok") || token.startsWith("tok_timeout")) {
            return GatewayCharge.ok("sim_ch_" + shortId(), last4(token));
        }
        if (token.startsWith("tok_insufficient")) {
            return GatewayCharge.declined("insufficient_funds", "The card has insufficient funds");
        }
        if (token.startsWith("tok_declined")) {
            return GatewayCharge.declined("card_declined", "The card was declined");
        }
        return GatewayCharge.declined("invalid_token", "The payment token is not valid");
    }

    private static String last4(String token) {
        Matcher m = LAST4.matcher(token);
        return m.find() ? m.group(1) : "4242";
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}