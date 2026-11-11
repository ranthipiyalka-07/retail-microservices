package com.retail.support.repository;

import com.retail.support.domain.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    /** Everything, internal notes included: staff only. */
    List<TicketMessage> findByTicketIdOrderByIdAsc(Long ticketId);

    /** What the customer may see. */
    List<TicketMessage> findByTicketIdAndInternalNoteFalseOrderByIdAsc(Long ticketId);

    long countByTicketId(Long ticketId);
}