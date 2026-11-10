package com.retail.order.web;

import com.retail.order.domain.OrderStatus;
import com.retail.order.dto.*;
import com.retail.order.exception.InvalidRequestException;
import com.retail.order.service.FulfilmentService;
import com.retail.order.service.OrderQueryService;
import com.retail.order.service.OrderSaga;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    private final OrderSaga saga;
    private final OrderQueryService queries;
    private final FulfilmentService fulfilment;

    public OrderController(OrderSaga saga, OrderQueryService queries, FulfilmentService fulfilment) {
        this.saga = saga;
        this.queries = queries;
        this.fulfilment = fulfilment;
    }

    /** 201 confirmed, 200 replay of a finished order, 202 still PENDING (poll GET /orders/{id}). */
    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody CheckoutRequest request,
                                               @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key.trim()).matches()) {
            throw new InvalidRequestException("Header Idempotency-Key is required (8 to 64 letters, digits, '.', '_', ':' or '-')");
        }
        OrderSaga.PlaceResult result = saga.checkout(CurrentCustomer.id(), CurrentCustomer.email(), key.trim(), request);
        HttpStatus http = result.status() == OrderStatus.PENDING ? HttpStatus.ACCEPTED
                : result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(http).cacheControl(CacheControl.noStore()).body(queries.detailById(result.orderId()));
    }

    @GetMapping
    public PageResponse<OrderSummary> list(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return queries.list(CurrentCustomer.id(), page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable Long orderId) {
        return queries.get(CurrentCustomer.id(), orderId);
    }

    @GetMapping("/{orderId}/tracking")
    public TrackingResponse tracking(@PathVariable Long orderId) {
        return queries.tracking(CurrentCustomer.id(), orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable Long orderId) {
        Long customerId = CurrentCustomer.id();
        saga.cancel(customerId, orderId);
        return queries.get(customerId, orderId);
    }

    @GetMapping("/admin")
    public PageResponse<OrderSummary> adminList(@RequestParam(required = false) OrderStatus status,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return queries.adminList(status, page, size);
    }

    @PutMapping("/{orderId}/status")
    public OrderResponse updateStatus(@PathVariable Long orderId, @Valid @RequestBody UpdateStatusRequest request,
                                      Authentication authentication) {
        return fulfilment.update(orderId, request, authentication.getName());     // the staff member's email
    }
}