package com.retail.customer.service;

import com.retail.common.event.CustomerRegisteredEvent;
import com.retail.customer.client.NotificationClient;
import com.retail.customer.client.NotificationEnvelope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class NotificationBridgeTest {

    private final NotificationClient client = mock(NotificationClient.class);
    private final NotificationBridge bridge = new NotificationBridge(client, "test-key");

    private static CustomerRegisteredEvent event() {
        return new CustomerRegisteredEvent(UUID.randomUUID(), Instant.now(), 3L, "john@example.com", "John");
    }

    @Test
    void forwardsTheEventWithTheInternalKeyInTheBackground() {
        CustomerRegisteredEvent event = event();

        bridge.send("CUSTOMER_REGISTERED", event);

        ArgumentCaptor<NotificationEnvelope> envelope = ArgumentCaptor.forClass(NotificationEnvelope.class);
        verify(client, timeout(2000)).send(eq("test-key"), envelope.capture());
        assertThat(envelope.getValue().type()).isEqualTo("CUSTOMER_REGISTERED");
        assertThat(envelope.getValue().payload()).isSameAs(event);
        bridge.stop();
    }

    @Test
    void aFailingCallIsLoggedAndNeverReachesTheCaller() {
        doThrow(new RuntimeException("notification-service is down")).when(client).send(any(), any());

        assertThatCode(() -> bridge.send("CUSTOMER_REGISTERED", event())).doesNotThrowAnyException();

        verify(client, timeout(2000)).send(any(), any());
        bridge.stop();
    }
}