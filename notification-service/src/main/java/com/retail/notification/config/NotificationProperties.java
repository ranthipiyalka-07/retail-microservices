package com.retail.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "retail.notification")
public record NotificationProperties(
        @DefaultValue("Retail Store") String shopName,
        @DefaultValue("4") int maxAttempts,                                   // the first try plus 3 retries
        @DefaultValue({"1m", "5m", "30m"}) List<Duration> retryDelays,        // the wait after attempt 1, 2, 3...
        @DefaultValue("2m") Duration lease,                                   // longer than the SMTP time limits
        @DefaultValue({"SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED"}) List<String> orderStatusNotify,
        List<String> lowStockRecipients,
        @DefaultValue("90d") Duration retention,
        @DefaultValue Mail mail) {

    /** mode: "log" prints the email to the console, "smtp" sends it through spring.mail.*. */
    public record Mail(@DefaultValue("log") String mode, @DefaultValue("no-reply@retail.local") String from) {}

    public NotificationProperties {
        lowStockRecipients = lowStockRecipients == null ? List.of() : List.copyOf(lowStockRecipients);
        orderStatusNotify = orderStatusNotify == null ? List.of() : List.copyOf(orderStatusNotify);
        retryDelays = retryDelays == null || retryDelays.isEmpty() ? List.of(Duration.ofMinutes(1)) : List.copyOf(retryDelays);
    }

    /** The wait before the next try, after the given number of attempts. The last delay repeats. */
    public Duration delayAfterAttempt(int attempts) {
        int index = Math.min(Math.max(attempts, 1) - 1, retryDelays.size() - 1);
        return retryDelays.get(index);
    }
}