package com.retail.order.client;

import com.retail.order.client.NotificationEnvelope;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "notification-service")
public interface NotificationClient {

    @PostMapping("/internal/notifications/events")
    void send(@RequestHeader("X-Internal-Key") String key, @RequestBody NotificationEnvelope envelope);
}