package com.retail.product.service;

import com.retail.product.client.CampaignClient;
import com.retail.product.client.CampaignPricing;
import com.retail.product.dto.PageResponse;
import com.retail.product.dto.ProductDetail;
import com.retail.product.dto.ProductSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Not @Transactional on purpose: the database reads finish inside ProductService before the remote call starts. */
@Service
public class PricedCatalogService {

    private final ProductService products;
    private final CampaignPricing pricing;

    public PricedCatalogService(ProductService products, CampaignPricing pricing) {
        this.products = products;
        this.pricing = pricing;
    }

    public PageResponse<ProductSummary> search(String q, Long categoryId, String brand, BigDecimal minPrice,
                                               BigDecimal maxPrice, BigDecimal minRating, String sort,
                                               Integer page, Integer size) {
        PageResponse<ProductSummary> result = products.search(q, categoryId, brand, minPrice, maxPrice, minRating,
                sort, page, size);
        return new PageResponse<>(withPrices(result.content()), result.page(), result.size(),
                result.totalElements(), result.totalPages());
    }

    public ProductDetail get(Long id) {
        ProductDetail detail = products.get(id);
        CampaignClient.PriceData quote = pricing.quote(List.of(
                new CampaignClient.PriceQuery.Item(detail.id(), detail.categoryId(), detail.basePrice()))).get(detail.id());
        return quote == null ? detail
                : detail.withPrice(quote.effectivePrice(), quote.discountAmount(), quote.campaignName());
    }

    /** For other services: active products only, with campaign prices. */
    public List<ProductSummary> summaries(List<Long> ids) {
        return withPrices(products.findActiveSummaries(ids));
    }

    private List<ProductSummary> withPrices(List<ProductSummary> summaries) {
        Map<Long, CampaignClient.PriceData> quotes = pricing.quote(summaries.stream()
                .map(s -> new CampaignClient.PriceQuery.Item(s.id(), s.categoryId(), s.basePrice())).toList());
        return summaries.stream().map(s -> {
            CampaignClient.PriceData quote = quotes.get(s.id());
            return quote == null ? s : s.withPrice(quote.effectivePrice(), quote.discountAmount(), quote.campaignName());
        }).toList();
    }
}