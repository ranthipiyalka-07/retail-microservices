package com.retail.review.service;

/** Internal event, published inside the transaction: after the commit it triggers the push to product-service. */
public record RatingChanged(Long productId) {}