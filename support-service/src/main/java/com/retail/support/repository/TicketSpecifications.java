package com.retail.support.repository;

import com.retail.support.domain.SupportTicket;
import com.retail.support.domain.TicketPriority;
import com.retail.support.domain.TicketStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TicketSpecifications {
    private TicketSpecifications() {}

    /** Null means "no filter". unassignedOnly and assignedTo are independent: the service sets at most one. */
    public static Specification<SupportTicket> forAgents(TicketStatus status, TicketPriority priority,
                                                         Long assignedTo, boolean unassignedOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (assignedTo != null) {
                predicates.add(cb.equal(root.get("assignedTo"), assignedTo));
            }
            if (unassignedOnly) {
                predicates.add(cb.isNull(root.get("assignedTo")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}