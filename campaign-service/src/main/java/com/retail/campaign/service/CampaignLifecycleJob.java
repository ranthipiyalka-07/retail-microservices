package com.retail.campaign.service;

import com.retail.campaign.domain.CampaignStatus;
import com.retail.campaign.repository.CampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Keeps the status labels in step with the clock and announces the changes. Prices do NOT depend on this job:
 * they look at the campaign's time window directly. Safe on several instances: only the instance whose update
 * changes the row publishes the event.
 */
@Component
public class CampaignLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(CampaignLifecycleJob.class);
    private static final int BATCH_SIZE = 100;

    private final CampaignRepository campaigns;
    private final CampaignService service;

    public CampaignLifecycleJob(CampaignRepository campaigns, CampaignService service) {
        this.campaigns = campaigns;
        this.service = service;
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${retail.campaign.schedule-delay:PT30S}")
    public void run() {
        Instant now = Instant.now();
        for (Long id : campaigns.findIdsToActivate(CampaignStatus.SCHEDULED, now, PageRequest.of(0, BATCH_SIZE))) {
            try {
                service.activate(id);
            } catch (RuntimeException e) {
                log.warn("Could not activate campaignId={}: {}", id, e.toString());
            }
        }
        for (Long id : campaigns.findIdsToExpire(CampaignService.OPEN, now, PageRequest.of(0, BATCH_SIZE))) {
            try {
                service.expire(id);
            } catch (RuntimeException e) {
                log.warn("Could not expire campaignId={}: {}", id, e.toString());
            }
        }
    }
}