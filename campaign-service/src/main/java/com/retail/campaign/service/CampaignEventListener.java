package com.retail.campaign.service;

import com.retail.common.event.CampaignChangedEvent;
import com.retail.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs only AFTER the transaction commits. When Kafka is added, replace the log line with
 * kafkaTemplate.send(topic, campaignId, event).
 */
@Component
public class CampaignEventListener {

    private static final Logger log = LoggerFactory.getLogger(CampaignEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(CampaignChangedEvent event) {
        String topic = event.changeType() == CampaignChangedEvent.ChangeType.ACTIVATED
                ? Topics.CAMPAIGN_ACTIVATED : Topics.CAMPAIGN_EXPIRED;
        log.info("{} event (not sent: Kafka not enabled yet): eventId={}, campaignId={}, productIds={}",
                topic, event.eventId(), event.campaignId(), event.productIds());
    }
}