package com.retail.inventory.web;

import com.retail.inventory.dto.AvailabilityResponse;
import com.retail.inventory.dto.ReservationResponse;
import com.retail.inventory.dto.ReserveRequest;
import com.retail.inventory.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Service-to-service only: not routed by the gateway, protected by X-Internal-Key. */
@RestController
@RequestMapping("/internal/inventory")
public class InternalInventoryController {

    private final ReservationService reservationService;

    public InternalInventoryController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/reservations")
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return reservationService.reserve(request);
    }

    @PostMapping("/reservations/{orderId}/commit")
    public ReservationResponse commit(@PathVariable Long orderId) {
        return reservationService.commit(orderId);
    }

    @PostMapping("/reservations/{orderId}/release")
    public ReservationResponse release(@PathVariable Long orderId) {
        return reservationService.release(orderId);
    }

    @GetMapping("/availability")
    public List<AvailabilityResponse> availability(@RequestParam List<Long> productIds) {
        return reservationService.availability(productIds);
    }
}