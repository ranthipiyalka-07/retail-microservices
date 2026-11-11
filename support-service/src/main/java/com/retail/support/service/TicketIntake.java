package com.retail.support.service;

import com.retail.support.client.OrderVerifier;
import com.retail.support.dto.CreateTicketRequest;
import com.retail.support.exception.ConflictException;
import com.retail.support.exception.InvalidRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TicketIntake {

    private final TicketService tickets;
    private final OrderVerifier orders;

    public TicketIntake(TicketService tickets, OrderVerifier orders) {
        this.tickets = tickets;
        this.orders = orders;
    }

    public TicketService.Created create(Long customerId, String customerEmail, String idempotencyKey,
                                        CreateTicketRequest request) {
        // A retry is answered before anything else, so it never needs order-service.
        if (idempotencyKey != null) {
            Optional<TicketService.Created> replay = tickets.findReplay(customerId, idempotencyKey, request);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        boolean verified = false;
        if (request.orderId() != null) {
            OrderVerifier.Result result = orders.check(customerId, request.orderId());
            if (result == OrderVerifier.Result.NOT_OWNED) {
                throw new InvalidRequestException("Order " + request.orderId() + " was not found in your orders");
            }
            verified = result == OrderVerifier.Result.OWNED;      // UNKNOWN: the ticket is kept, marked unverified
        }

        try {
            return tickets.create(customerId, customerEmail, idempotencyKey, request, verified);
        } catch (DataIntegrityViolationException raced) {
            // A parallel request with the same key won the unique key: answer with its ticket.
            if (idempotencyKey != null) {
                return tickets.findReplay(customerId, idempotencyKey, request)
                        .orElseThrow(() -> new ConflictException(
                                "The ticket could not be created because of a parallel request. Please try again."));
            }
            throw new ConflictException("The ticket could not be created because of a parallel request. Please try again.");
        }
    }
}