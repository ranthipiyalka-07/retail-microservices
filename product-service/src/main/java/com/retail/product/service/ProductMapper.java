package com.retail.product.service;

import com.retail.product.domain.Product;
import com.retail.product.domain.ProductImage;
import com.retail.product.dto.*;

import java.util.List;

final class ProductMapper {
    private ProductMapper() {}

    static ProductSummary toSummary(Product p) {
        String primary = p.getImages().stream().filter(ProductImage::isPrimaryImage).findFirst()
                .or(() -> p.getImages().stream().findFirst())
                .map(ProductImage::getUrl).orElse(null);
        return new ProductSummary(p.getId(), p.getSku(), p.getName(), p.getBrand(), p.getCategoryId(),
                p.getBasePrice(), p.getAvgRating(), p.getReviewCount(), primary);
    }

    static ProductDetail toDetail(Product p) {
        List<ImageResponse> images = p.getImages().stream()
                .map(i -> new ImageResponse(i.getId(), i.getUrl(), i.getSortOrder(), i.isPrimaryImage())).toList();
        List<AttributeResponse> attributes = p.getAttributes().stream()
                .map(a -> new AttributeResponse(a.getName(), a.getValue())).toList();
        return new ProductDetail(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getBrand(),
                p.getCategoryId(), p.getBasePrice(), p.getStatus(), p.getAvgRating(), p.getReviewCount(),
                images, attributes, p.getCreatedAt());
    }
}