package com.retail.product.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.product.client.CampaignClient;
import com.retail.product.client.CampaignPricing;
import com.retail.product.domain.ProductStatus;
import com.retail.product.dto.PageResponse;
import com.retail.product.dto.ProductDetail;
import com.retail.product.dto.ProductSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricedCatalogServiceTest {

    @Mock ProductService products;
    @Mock CampaignPricing pricing;

    private PricedCatalogService service;

    @BeforeEach
    void setUp() {
        service = new PricedCatalogService(products, pricing);
    }

    private static ProductSummary summary(long id, String base) {
        BigDecimal price = new BigDecimal(base);
        return new ProductSummary(id, "SKU-" + id, "Product " + id, "Northwind", 3L, price, price,
                BigDecimal.ZERO.setScale(2), null, BigDecimal.ZERO, 0, null);
    }

    private PageResponse<ProductSummary> pageOf(ProductSummary... summaries) {
        return new PageResponse<>(List.of(summaries), 0, 20, summaries.length, 1);
    }

    private void searchReturns(PageResponse<ProductSummary> page) {
        when(products.search(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(page);
    }

    private PageResponse<ProductSummary> search() {
        return service.search(null, null, null, null, null, null, null, null, null);
    }

    @Test
    void searchShowsTheCampaignPriceOnlyOnTheDiscountedProducts() {
        searchReturns(pageOf(summary(1, "19.99"), summary(2, "24.50")));
        when(pricing.quote(any())).thenReturn(Map.of(1L, new CampaignClient.PriceData(1L,
                new BigDecimal("15.99"), new BigDecimal("4.00"), "Tee Sale")));

        PageResponse<ProductSummary> result = search();

        assertThat(result.content().get(0).effectivePrice()).isEqualByComparingTo("15.99");
        assertThat(result.content().get(0).discountAmount()).isEqualByComparingTo("4.00");
        assertThat(result.content().get(0).campaignName()).isEqualTo("Tee Sale");
        assertThat(result.content().get(0).basePrice()).isEqualByComparingTo("19.99");
        assertThat(result.content().get(1).effectivePrice()).isEqualByComparingTo("24.50");
        assertThat(result.content().get(1).campaignName()).isNull();
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    void searchKeepsTheBasePricesWhenCampaignPricingIsUnavailable() {
        searchReturns(pageOf(summary(1, "19.99")));
        when(pricing.quote(any())).thenReturn(Map.of());

        PageResponse<ProductSummary> result = search();

        assertThat(result.content().get(0).effectivePrice()).isEqualByComparingTo("19.99");
        assertThat(result.content().get(0).campaignName()).isNull();
    }

    @Test
    void detailShowsTheCampaignPrice() {
        BigDecimal base = new BigDecimal("19.99");
        when(products.get(1L)).thenReturn(new ProductDetail(1L, "SKU-1", "Tee", null, "Northwind", 3L, base, base,
                BigDecimal.ZERO.setScale(2), null, ProductStatus.ACTIVE, BigDecimal.ZERO, 0, List.of(), List.of(),
                Instant.now()));
        when(pricing.quote(any())).thenReturn(Map.of(1L, new CampaignClient.PriceData(1L,
                new BigDecimal("15.99"), new BigDecimal("4.00"), "Tee Sale")));

        ProductDetail detail = service.get(1L);

        assertThat(detail.effectivePrice()).isEqualByComparingTo("15.99");
        assertThat(detail.campaignName()).isEqualTo("Tee Sale");
        assertThat(detail.basePrice()).isEqualByComparingTo("19.99");
    }

    @Test
    void anUnknownProductStaysNotFound() {
        when(products.get(99L)).thenThrow(new ResourceNotFoundException("Product", 99L));

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}