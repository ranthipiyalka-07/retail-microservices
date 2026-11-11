package com.retail.campaign.web;

import com.retail.campaign.domain.CampaignStatus;
import com.retail.campaign.dto.*;
import com.retail.campaign.service.CampaignService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    /** Public: running campaigns for a promo banner. */
    @GetMapping("/active")
    public List<ActiveCampaignResponse> active() {
        return campaignService.active();
    }

    @GetMapping
    public PageResponse<CampaignSummary> list(@RequestParam(required = false) CampaignStatus status,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return campaignService.list(status, page, size);
    }

    @GetMapping("/{id}")
    public CampaignResponse get(@PathVariable Long id) {
        return campaignService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignResponse create(@Valid @RequestBody CampaignRequest request, Authentication authentication) {
        return campaignService.create(request, authentication.getName());       // the manager's email
    }

    @PutMapping("/{id}")
    public CampaignResponse update(@PathVariable Long id, @Valid @RequestBody CampaignRequest request) {
        return campaignService.update(id, request);
    }

    @PostMapping("/{id}/publish")
    public CampaignResponse publish(@PathVariable Long id) {
        return campaignService.publish(id);
    }

    @PostMapping("/{id}/cancel")
    public CampaignResponse cancel(@PathVariable Long id) {
        return campaignService.cancel(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        campaignService.delete(id);
    }
}