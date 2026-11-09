package com.retail.inventory.service;

import com.retail.inventory.domain.ReservationStatus;
import com.retail.inventory.domain.StockReservation;
import com.retail.inventory.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Returns stock held by orders that were never paid. Each reservation is expired in its own transaction. */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);
    private static final int BATCH_SIZE = 200;

    private final StockReservationRepository reservations;
    private final ReservationService reservationService;

    public ReservationExpiryJob(StockReservationRepository reservations, ReservationService reservationService) {
        this.reservations = reservations;
        this.reservationService = reservationService;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${retail.inventory.expiry-check-delay:PT1M}")
    public void expireDueReservations() {
        List<StockReservation> due = reservations.findByStatusAndExpiresAtBefore(
                ReservationStatus.RESERVED, Instant.now(), PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
        int expired = 0;
        for (StockReservation reservation : due) {
            try {
                if (reservationService.expire(reservation.getId())) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire reservation id={}: {}", reservation.getId(), e.toString());
            }
        }
        if (expired > 0) {
            log.info("Expired {} stock reservations", expired);
        }
    }
}