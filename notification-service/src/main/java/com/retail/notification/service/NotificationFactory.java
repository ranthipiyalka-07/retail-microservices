package com.retail.notification.service;

import com.retail.common.event.*;
import com.retail.notification.config.NotificationProperties;
import com.retail.notification.domain.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class NotificationFactory {

    private static final Logger log = LoggerFactory.getLogger(NotificationFactory.class);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final NotificationProperties props;

    public NotificationFactory(NotificationProperties props) {
        this.props = props;
    }

    /** An empty list means "this event needs no email". */
    public List<Draft> drafts(DomainEvent event) {
        if (event instanceof CustomerRegisteredEvent e) {
            return List.of(customerRegistered(e));
        }
        if (event instanceof OrderCreatedEvent e) {
            return List.of(orderCreated(e));
        }
        if (event instanceof OrderStatusChangedEvent e) {
            return orderStatusChanged(e);
        }
        if (event instanceof SupportTicketCreatedEvent e) {
            return List.of(ticket(EventType.SUPPORT_TICKET_CREATED, "SUPPORT_TICKET_CREATED", e.eventId(),
                    e.customerEmail(), e.ticketNumber(), e.subject()));
        }
        if (event instanceof SupportTicketRepliedEvent e) {
            return List.of(ticket(EventType.SUPPORT_TICKET_REPLIED, "SUPPORT_TICKET_REPLIED", e.eventId(),
                    e.customerEmail(), e.ticketNumber(), e.subject()));
        }
        if (event instanceof LowStockEvent e) {
            return lowStock(e);
        }
        throw new IllegalArgumentException("Unsupported event type " + event.getClass().getSimpleName());
    }

    private Draft customerRegistered(CustomerRegisteredEvent e) {
        String firstName = Text.line(e.firstName(), 100);
        Map<String, String> vars = vars();
        vars.put("firstName", firstName.isEmpty() ? "there" : firstName);
        vars.put("email", Text.line(e.email(), 255));
        return new Draft(e.eventId(), EventType.CUSTOMER_REGISTERED, e.email(), "CUSTOMER_REGISTERED", vars,
                "CUSTOMER", String.valueOf(e.customerId()));
    }

    private Draft orderCreated(OrderCreatedEvent e) {
        int itemCount = e.items() == null ? 0 : e.items().stream().mapToInt(OrderCreatedEvent.Item::quantity).sum();
        String total = e.grandTotal() == null ? ""
                : e.grandTotal().setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + Text.line(e.currency(), 3);
        Map<String, String> vars = vars();
        vars.put("orderNumber", Text.line(e.orderNumber(), 32));
        vars.put("itemCount", String.valueOf(itemCount));
        vars.put("total", total.trim());
        return new Draft(e.eventId(), EventType.ORDER_CREATED, e.customerEmail(), "ORDER_CONFIRMED", vars,
                "ORDER", String.valueOf(e.orderId()));
    }

    private List<Draft> orderStatusChanged(OrderStatusChangedEvent e) {
        String status = e.newStatus() == null ? "" : e.newStatus().trim().toUpperCase(Locale.ROOT);
        if (!props.orderStatusNotify().contains(status)) {
            log.debug("No email for order status {}", status);
            return List.of();
        }
        Map<String, String> vars = vars();
        vars.put("orderNumber", Text.line(e.orderNumber(), 32));
        vars.put("statusLabel", statusLabel(status));
        vars.put("trackingDetails", trackingDetails(e.trackingInfo()));
        return List.of(new Draft(e.eventId(), EventType.ORDER_STATUS_CHANGED, e.customerEmail(), "ORDER_STATUS_CHANGED",
                vars, "ORDER", String.valueOf(e.orderId())));
    }

    private Draft ticket(EventType type, String templateCode, java.util.UUID eventId, String customerEmail,
                         String ticketNumber, String subject) {
        Map<String, String> vars = vars();
        vars.put("ticketNumber", Text.line(ticketNumber, 32));
        vars.put("subject", Text.line(subject, 200));
        return new Draft(eventId, type, customerEmail, templateCode, vars, "TICKET", Text.line(ticketNumber, 32));
    }

    private List<Draft> lowStock(LowStockEvent e) {
        if (props.lowStockRecipients().isEmpty()) {
            log.info("Low stock for productId={}, but retail.notification.low-stock-recipients is empty", e.productId());
            return List.of();
        }
        List<Draft> drafts = new ArrayList<>();
        for (String recipient : props.lowStockRecipients()) {
            Map<String, String> vars = vars();
            vars.put("productId", String.valueOf(e.productId()));
            vars.put("available", String.valueOf(e.quantityAvailable()));
            vars.put("reorderLevel", String.valueOf(e.reorderLevel()));
            drafts.add(new Draft(e.eventId(), EventType.LOW_STOCK, recipient, "LOW_STOCK_ALERT", vars,
                    "PRODUCT", String.valueOf(e.productId())));
        }
        return drafts;
    }

    private Map<String, String> vars() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("shopName", Text.line(props.shopName(), 100));
        return vars;
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "PACKED" -> "has been packed";
            case "SHIPPED" -> "has been shipped";
            case "OUT_FOR_DELIVERY" -> "is out for delivery";
            case "DELIVERED" -> "has been delivered";
            default -> "is now " + status.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static String trackingDetails(OrderStatusChangedEvent.TrackingInfo t) {
        if (t == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        if (t.carrier() != null && !t.carrier().isBlank()) {
            lines.add("Carrier: " + Text.line(t.carrier(), 100));
        }
        if (t.trackingNumber() != null && !t.trackingNumber().isBlank()) {
            lines.add("Tracking number: " + Text.line(t.trackingNumber(), 100));
        }
        if (t.trackingUrl() != null && !t.trackingUrl().isBlank()) {
            lines.add("Track your parcel: " + Text.line(t.trackingUrl(), 500));
        }
        if (t.estimatedDelivery() != null) {
            lines.add("Estimated delivery: " + DATE.format(t.estimatedDelivery()));
        }
        return String.join("\n", lines);
    }
}