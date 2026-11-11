package com.retail.campaign.web;

import com.retail.campaign.dto.PriceQuery;
import com.retail.campaign.dto.PriceResponse;
import com.retail.campaign.service.PriceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Service-to-service only: not routed by the gateway, protected by X-Internal-Key. */
@RestController
@RequestMapping("/internal/prices")
public class InternalPriceController {

    private final PriceService priceService;

    public InternalPriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @PostMapping("/effective")
    public List<PriceResponse> effective(@Valid @RequestBody PriceQuery query) {
        return priceService.effectivePrices(query.items());
    }
}