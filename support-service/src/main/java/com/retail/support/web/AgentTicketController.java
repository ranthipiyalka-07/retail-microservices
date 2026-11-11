package com.retail.support.web;

import com.retail.support.domain.TicketPriority;
import com.retail.support.domain.TicketStatus;
import com.retail.support.dto.*;
import com.retail.support.service.AgentTicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/support/agent/tickets")
public class AgentTicketController {

    private final AgentTicketService agents;

    public AgentTicketController(AgentTicketService agents) {
        this.agents = agents;
    }

    @GetMapping
    public PageResponse<AgentTicketSummary> list(@RequestParam(required = false) TicketStatus status,
                                                 @RequestParam(required = false) TicketPriority priority,
                                                 @RequestParam(required = false) String assignee,
                                                 @RequestParam(required = false) String sort,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return agents.list(status, priority, assignee, sort, CurrentUser.staff(), page, size);
    }

    @GetMapping("/{ticketId}")
    public AgentTicketDetail get(@PathVariable Long ticketId) {
        return agents.get(ticketId);
    }

    @PostMapping("/{ticketId}/claim")
    public AgentTicketDetail claim(@PathVariable Long ticketId) {
        return agents.claim(ticketId, CurrentUser.staff());
    }

    @PostMapping("/{ticketId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentMessage reply(@PathVariable Long ticketId, @Valid @RequestBody AgentMessageRequest request) {
        return agents.reply(ticketId, request.message(), Boolean.TRUE.equals(request.internal()), CurrentUser.staff());
    }

    @PutMapping("/{ticketId}/status")
    public AgentTicketDetail changeStatus(@PathVariable Long ticketId, @Valid @RequestBody StatusRequest request) {
        return agents.changeStatus(ticketId, request.status(), CurrentUser.staff());
    }

    @PutMapping("/{ticketId}/priority")
    public AgentTicketDetail changePriority(@PathVariable Long ticketId, @Valid @RequestBody PriorityRequest request) {
        return agents.changePriority(ticketId, request.priority());
    }
}