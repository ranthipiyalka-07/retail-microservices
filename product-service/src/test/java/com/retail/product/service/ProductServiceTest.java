package com.retail.product.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.product.domain.Product;
import com.retail.product.domain.ProductStatus;
import com.retail.product.dto.ImageRequest;
import com.retail.product.dto.ProductDetail;
import com.retail.product.dto.ProductRequest;
import com.retail.product.exception.ConflictException;
import com.retail.product.exception.InvalidRequestException;
import com.retail.product.repository.CategoryRepository;
import com.retail.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository products;
    @Mock CategoryRepository categories;
    @Mock CategoryService categoryService;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, categories, categoryService);
    }

    private static ProductRequest request(String sku, List<ImageRequest> images) {
        return new ProductRequest(sku, " Classic Tee ", null, "Northwind", 1L,
                new BigDecimal("19.990"), null, images, null);
    }

    @Test
    void createNormalizesSkuAndMarksTheFlaggedImageAsPrimary() {
        when(products.existsBySku("TEE-001")).thenReturn(false);
        when(categories.existsById(1L)).thenReturn(true);
        when(products.saveAndFlush(any(Product.class))).thenAnswer(returnsFirstArg());

        ProductDetail detail = service.create(request(" tee-001 ", List.of(
                new ImageRequest("https://example.com/a.jpg", null, false),
                new ImageRequest("https://example.com/b.jpg", null, true))));

        assertThat(detail.sku()).isEqualTo("TEE-001");
        assertThat(detail.name()).isEqualTo("Classic Tee");
        assertThat(detail.basePrice()).isEqualByComparingTo("19.99");
        assertThat(detail.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(detail.images()).hasSize(2);
        assertThat(detail.images().get(0).primary()).isFalse();
        assertThat(detail.images().get(1).primary()).isTrue();
    }

    @Test
    void firstImageIsPrimaryWhenNoneIsFlagged() {
        when(products.existsBySku("TEE-001")).thenReturn(false);
        when(categories.existsById(1L)).thenReturn(true);
        when(products.saveAndFlush(any(Product.class))).thenAnswer(returnsFirstArg());

        ProductDetail detail = service.create(request("tee-001", List.of(
                new ImageRequest("https://example.com/a.jpg", null, null),
                new ImageRequest("https://example.com/b.jpg", null, null))));

        assertThat(detail.images().get(0).primary()).isTrue();
        assertThat(detail.images().get(1).primary()).isFalse();
    }

    @Test
    void duplicateSkuIsRejected() {
        when(products.existsBySku("TEE-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("tee-001", null))).isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownCategoryIsRejected() {
        when(products.existsBySku("TEE-001")).thenReturn(false);
        when(categories.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("tee-001", null))).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void deletedStatusCannotBeSetThroughTheRequest() {
        when(products.existsBySku("TEE-001")).thenReturn(false);
        when(categories.existsById(1L)).thenReturn(true);
        var bad = new ProductRequest("tee-001", "Tee", null, null, 1L, BigDecimal.TEN,
                ProductStatus.DELETED, null, null);

        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void updatingAnUnknownProductGivesNotFound() {
        when(products.findByIdAndStatusNot(99L, ProductStatus.DELETED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request("tee-001", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteIsASoftDelete() {
        Product product = new Product();
        product.setId(5L);
        product.setStatus(ProductStatus.ACTIVE);
        when(products.findByIdAndStatusNot(5L, ProductStatus.DELETED)).thenReturn(Optional.of(product));

        service.delete(5L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELETED);
    }

    @Test
    void searchRejectsMinPriceAboveMaxPrice() {
        assertThatThrownBy(() -> service.search(null, null, null, new BigDecimal("50"), new BigDecimal("10"),
                null, null, null, null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void searchRejectsAnUnknownSortKey() {
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, "colour", null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Allowed");
    }

    @Test
    void searchClampsThePageSize() {
        when(products.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, null, null, null, null, null, -3, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(products).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getPageNumber()).isZero();
    }
}