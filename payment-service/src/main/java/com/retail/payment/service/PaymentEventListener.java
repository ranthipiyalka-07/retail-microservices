package com.retail.payment.service;

import com.retail.common.event.PaymentResultEvent;
import com.retail.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs only AFTER the settling transaction commits. When Kafka is added, replace the log line with
 * kafkaTemplate.send(topic, orderId, event). Order-service gets the result from the HTTP answer today.
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentResult(PaymentResultEvent event) {
        String topic = event.status() == PaymentResultEvent.Status.SUCCESS
                ? Topics.PAYMENT_COMPLETED : Topics.PAYMENT_FAILED;
        log.info("{} event (not sent: Kafka not enabled yet): eventId={}, orderId={}, paymentId={}",
                topic, event.eventId(), event.orderId(), event.paymentId());
    }
}