package com.retail.support.service;

import com.retail.support.config.SupportProperties;
import com.retail.support.domain.TicketStatus;
import com.retail.support.repository.SupportTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Closes tickets that were RESOLVED long enough ago. Safe on several instances: the update is conditional. */
@Component
public class TicketAutoCloseJob {

    private static final Logger log = LoggerFactory.getLogger(TicketAutoCloseJob.class);
    private static final int BATCH_SIZE = 100;

    private final SupportTicketRepository tickets;
    private final TicketService service;
    private final SupportProperties props;

    public TicketAutoCloseJob(SupportTicketRepository tickets, TicketService service, SupportProperties props) {
        this.tickets = tickets;
        this.service = service;
        this.props = props;
    }

    @Scheduled(initialDelayString = "PT45S", fixedDelayString = "${retail.support.auto-close-delay:PT10M}")
    public void run() {
        Instant cutoff = Instant.now().minus(props.autoCloseAfter());
        List<Long> ids = tickets.findIdsResolvedBefore(TicketStatus.RESOLVED, cutoff, PageRequest.of(0, BATCH_SIZE));
        int closed = 0;
        for (Long id : ids) {
            try {
                if (service.autoClose(id, cutoff)) {
                    closed++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not close ticketId={}: {}", id, e.toString());
            }
        }
        if (closed > 0) {
            log.info("Closed {} resolved tickets automatically", closed);
        }
    }
}