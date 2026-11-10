package com.retail.order.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.common.event.OrderStatusChangedEvent;
import com.retail.order.domain.CustomerOrder;
import com.retail.order.domain.OrderStatus;
import com.retail.order.domain.OrderStatusHistory;
import com.retail.order.domain.Shipment;
import com.retail.order.dto.UpdateStatusRequest;
import com.retail.order.exception.ConflictException;
import com.retail.order.exception.InvalidRequestException;
import com.retail.order.repository.CustomerOrderRepository;
import com.retail.order.repository.OrderStatusHistoryRepository;
import com.retail.order.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FulfilmentServiceTest {

    @Mock CustomerOrderRepository orders;
    @Mock OrderStatusHistoryRepository history;
    @Mock ShipmentRepository shipments;
    @Mock OrderAssembler assembler;
    @Mock ApplicationEventPublisher events;

    private FulfilmentService service;

    @BeforeEach
    void setUp() {
        service = new FulfilmentService(orders, history, shipments, assembler, events);
    }

    private void orderIs(OrderStatus status) {
        CustomerOrder order = new CustomerOrder();
        order.setId(1L);
        order.setOrderNumber("ORD-TEST");
        order.setCustomerId(5L);
        order.setCustomerEmail("john@example.com");
        order.setStatus(status);
        when(orders.findById(1L)).thenReturn(Optional.of(order));
    }

    private static UpdateStatusRequest status(OrderStatus to) {
        return new UpdateStatusRequest(to, null, null, null, null, null);
    }

    @Test
    void confirmedOrderCanBePackedWithAHistoryEntryAndAnEvent() {
        orderIs(OrderStatus.CONFIRMED);
        when(orders.advance(eq(1L), eq(OrderStatus.CONFIRMED), eq(OrderStatus.PACKED), any(Instant.class))).thenReturn(1);

        service.update(1L, status(OrderStatus.PACKED), "manager@retail.local");

        ArgumentCaptor<OrderStatusHistory> entry = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().getFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(entry.getValue().getToStatus()).isEqualTo(OrderStatus.PACKED);
        assertThat(entry.getValue().getChangedBy()).isEqualTo("manager@retail.local");
        verify(events).publishEvent(any(OrderStatusChangedEvent.class));
        verify(shipments, never()).save(any());
    }

    @Test
    void shippingCreatesTheShipmentWithTrackingDetails() {
        orderIs(OrderStatus.PACKED);
        when(orders.advance(eq(1L), eq(OrderStatus.PACKED), eq(OrderStatus.SHIPPED), any(Instant.class))).thenReturn(1);

        service.update(1L, new UpdateStatusRequest(OrderStatus.SHIPPED, "On its way", " DHL ", " JD001 ",
                "https://example.com/track/JD001", null), "manager@retail.local");

        ArgumentCaptor<Shipment> shipment = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).save(shipment.capture());
        assertThat(shipment.getValue().getCarrier()).isEqualTo("DHL");
        assertThat(shipment.getValue().getTrackingNumber()).isEqualTo("JD001");
        assertThat(shipment.getValue().getShippedAt()).isNotNull();
    }

    @Test
    void shippingWithoutTrackingDetailsIsRejected() {
        assertThatThrownBy(() -> service.update(1L, status(OrderStatus.SHIPPED), "manager@retail.local"))
                .isInstanceOf(InvalidRequestException.class);
        verify(orders, never()).advance(any(), any(), any(), any());
    }

    @Test
    void stepsCannotBeSkipped() {
        orderIs(OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> service.update(1L, status(OrderStatus.DELIVERED), "manager@retail.local"))
                .isInstanceOf(ConflictException.class);
        verify(orders, never()).advance(any(), any(), any(), any());
    }

    @Test
    void cancelledOrFailedStatusesCannotBeSetByStaff() {
        assertThatThrownBy(() -> service.update(1L, status(OrderStatus.CANCELLED), "manager@retail.local"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.update(1L, status(OrderStatus.CONFIRMED), "manager@retail.local"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void losingTheRaceAgainstACancellationIsAConflict() {
        orderIs(OrderStatus.CONFIRMED);
        when(orders.advance(eq(1L), eq(OrderStatus.CONFIRMED), eq(OrderStatus.PACKED), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.update(1L, status(OrderStatus.PACKED), "manager@retail.local"))
                .isInstanceOf(ConflictException.class);
        verify(history, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void unknownOrderIsNotFound() {
        when(orders.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, status(OrderStatus.PACKED), "manager@retail.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}