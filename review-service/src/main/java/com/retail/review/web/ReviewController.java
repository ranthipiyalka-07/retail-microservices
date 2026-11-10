package com.retail.review.web;

import com.retail.review.dto.*;
import com.retail.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public PageResponse<ReviewResponse> list(@PathVariable Long productId,
                                             @RequestParam(required = false) Integer rating,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return reviewService.list(productId, rating, sort, page, size);
    }

    @GetMapping("/summary")
    public RatingSummaryResponse summary(@PathVariable Long productId) {
        return reviewService.summary(productId);
    }

    @GetMapping("/mine")
    public List<MyReviewResponse> mine(@PathVariable Long productId) {
        return reviewService.mine(CurrentCustomer.id(), productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MyReviewResponse create(@PathVariable Long productId, @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.create(CurrentCustomer.id(), productId, request);
    }

    @PutMapping("/{reviewId}")
    public MyReviewResponse update(@PathVariable Long productId, @PathVariable Long reviewId,
                                   @Valid @RequestBody UpdateReviewRequest request) {
        return reviewService.update(CurrentCustomer.id(), productId, reviewId, request);
    }

    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long productId, @PathVariable Long reviewId) {
        reviewService.delete(CurrentCustomer.id(), productId, reviewId);
    }

    @PutMapping("/{reviewId}/moderation")
    public MyReviewResponse moderate(@PathVariable Long productId, @PathVariable Long reviewId,
                                     @Valid @RequestBody ModerationRequest request, Authentication authentication) {
        return reviewService.moderate(productId, reviewId, request.status(), authentication.getName());
    }
}