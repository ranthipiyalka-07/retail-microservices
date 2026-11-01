package com.retail.common.event;

public final class Topics {
    private Topics()
    {
    }
    public static final String CUSTOMER_REGISTERED   = "customer.registered";
    public static final String ORDER_CREATED         = "order.created";
    public static final String ORDER_STATUS_CHANGED  = "order.status-changed";
    public static final String ORDER_CANCELLED       = "order.cancelled";
    public static final String PAYMENT_COMPLETED     = "payment.completed";
    public static final String PAYMENT_FAILED        = "payment.failed";
    public static final String REVIEW_PUBLISHED      = "review.published";
    public static final String SUPPORT_TICKET_CREATED = "support.ticket-created";
    public static final String CAMPAIGN_ACTIVATED    = "campaign.activated";
    public static final String CAMPAIGN_EXPIRED      = "campaign.expired";
    public static final String INVENTORY_LOW_STOCK   = "inventory.low-stock";

    /** Dead-letter topic name for a failed message, e.g. order.created.DLT */
    public static String dlt(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException(
                    "Topic name cannot be null or blank"
            );
        }

        return topic + ".DLT";
    }

}
