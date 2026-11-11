package com.retail.support.web;

import com.retail.support.domain.TicketStatus;
import com.retail.support.dto.*;
import com.retail.support.exception.InvalidRequestException;
import com.retail.support.service.TicketIntake;
import com.retail.support.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/support/tickets")
public class CustomerTicketController {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    private final TicketIntake intake;
    private final TicketService tickets;

    public CustomerTicketController(TicketIntake intake, TicketService tickets) {
        this.intake = intake;
        this.tickets = tickets;
    }

    /** 201 for a new ticket, 200 for a retry with the same Idempotency-Key (the header is optional). */
    @PostMapping
    public ResponseEntity<CustomerTicketDetail> create(@Valid @RequestBody CreateTicketRequest request,
                                                       @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        String idempotencyKey = null;
        if (key != null && !key.isBlank()) {
            idempotencyKey = key.trim();
            if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
                throw new InvalidRequestException("Idempotency-Key must be 8 to 64 letters, digits, '.', '_', ':' or '-'");
            }
        }
        TicketService.Created result = intake.create(CurrentUser.customerId(), CurrentUser.email(), idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.ticket());
    }

    @GetMapping
    public PageResponse<CustomerTicketSummary> list(@RequestParam(required = false) TicketStatus status,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return tickets.list(CurrentUser.customerId(), status, page, size);
    }

    @GetMapping("/{ticketId}")
    public CustomerTicketDetail get(@PathVariable Long ticketId) {
        return tickets.get(CurrentUser.customerId(), ticketId);
    }

    @PostMapping("/{ticketId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerMessage reply(@PathVariable Long ticketId, @Valid @RequestBody MessageRequest request) {
        return tickets.reply(CurrentUser.customerId(), ticketId, request.message());
    }

    @PostMapping("/{ticketId}/close")
    public CustomerTicketDetail close(@PathVariable Long ticketId) {
        return tickets.close(CurrentUser.customerId(), ticketId);
    }
}