package com.retail.order.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.order.domain.CustomerOrder;
import com.retail.order.domain.OrderStatus;
import com.retail.order.dto.OrderResponse;
import com.retail.order.dto.OrderSummary;
import com.retail.order.dto.PageResponse;
import com.retail.order.dto.TrackingResponse;
import com.retail.order.repository.CustomerOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

    private final CustomerOrderRepository orders;
    private final OrderAssembler assembler;

    public OrderQueryService(CustomerOrderRepository orders, OrderAssembler assembler) {
        this.orders = orders;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(Long customerId, Integer page, Integer size) {
        return PageResponse.of(orders.findByCustomerId(customerId, pageable(page, size)).map(assembler::summary));
    }

    /** A customer only ever sees their own orders: somebody else's id is a 404. */
    @Transactional(readOnly = true)
    public OrderResponse get(Long customerId, Long orderId) {
        return assembler.detail(orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId)));
    }

    @Transactional(readOnly = true)
    public TrackingResponse tracking(Long customerId, Long orderId) {
        return assembler.tracking(orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId)));
    }

    /** For callers that already know the order belongs to the customer (the checkout itself). */
    @Transactional(readOnly = true)
    public OrderResponse detailById(Long orderId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return assembler.detail(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> adminList(OrderStatus status, Integer page, Integer size) {
        Pageable pageable = pageable(page, size);
        return PageResponse.of((status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable))
                .map(assembler::summary));
    }

    private static Pageable pageable(Integer page, Integer size) {
        int number = page == null ? 0 : Math.max(page, 0);
        int length = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        return PageRequest.of(number, length, Sort.by(Sort.Direction.DESC, "id"));
    }
}