package com.retail.product.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.product.domain.Product;
import com.retail.product.domain.ProductAttribute;
import com.retail.product.domain.ProductImage;
import com.retail.product.domain.ProductStatus;
import com.retail.product.dto.*;
import com.retail.product.exception.ConflictException;
import com.retail.product.exception.InvalidRequestException;
import com.retail.product.repository.CategoryRepository;
import com.retail.product.repository.ProductRepository;
import com.retail.product.repository.ProductSearchCriteria;
import com.retail.product.repository.ProductSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_TOKENS = 5;
    private static final int MAX_TOKEN_LENGTH = 100;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CategoryService categoryService;

    public ProductService(ProductRepository products, CategoryRepository categories, CategoryService categoryService) {
        this.products = products;
        this.categories = categories;
        this.categoryService = categoryService;
    }

    // ---------- customer side ----------

    @Transactional(readOnly = true)
    public PageResponse<ProductSummary> search(String q, Long categoryId, String brand,
                                               BigDecimal minPrice, BigDecimal maxPrice, BigDecimal minRating,
                                               String sort, Integer page, Integer size) {
        if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0) {
            throw new InvalidRequestException("Prices cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new InvalidRequestException("minPrice cannot be greater than maxPrice");
        }
        if (minRating != null && (minRating.signum() < 0 || minRating.compareTo(BigDecimal.valueOf(5)) > 0)) {
            throw new InvalidRequestException("minRating must be between 0 and 5");
        }

        List<String> tokens = q == null || q.isBlank() ? List.of()
                : Arrays.stream(q.trim().split("\\s+")).limit(MAX_TOKENS)
                .map(t -> t.length() > MAX_TOKEN_LENGTH ? t.substring(0, MAX_TOKEN_LENGTH) : t).toList();
        Set<Long> categoryIds = categoryId == null ? null : categoryService.idsWithDescendants(categoryId);
        String brandFilter = brand == null || brand.isBlank() ? null : brand.trim();

        int pageNumber = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sortFor(sort));

        var criteria = new ProductSearchCriteria(tokens, categoryIds, brandFilter, minPrice, maxPrice, minRating);
        return PageResponse.of(products.findAll(ProductSpecifications.from(criteria), pageable)
                .map(ProductMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public ProductDetail get(Long id) {
        return ProductMapper.toDetail(products.findByIdAndStatus(id, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id)));
    }

    // ---------- admin side ----------

    @Transactional
    public ProductDetail create(ProductRequest request) {
        String sku = normalizeSku(request.sku());
        if (products.existsBySku(sku)) {
            throw new ConflictException("SKU '" + sku + "' already exists");
        }
        requireCategory(request.categoryId());

        Product product = new Product();
        product.setSku(sku);
        product.setStatus(statusOrDefault(request.status()));
        apply(product, request);
        try {
            products.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("SKU '" + sku + "' already exists");
        }
        log.info("Product created: id={}, sku={}", product.getId(), sku);
        return ProductMapper.toDetail(product);
    }

    /** Full replace: images and attributes in the request become the complete new lists. */
    @Transactional
    public ProductDetail update(Long id, ProductRequest request) {
        Product product = products.findByIdAndStatusNot(id, ProductStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        String sku = normalizeSku(request.sku());
        if (products.existsBySkuAndIdNot(sku, id)) {
            throw new ConflictException("SKU '" + sku + "' already exists");
        }
        requireCategory(request.categoryId());

        product.setSku(sku);
        if (request.status() != null) {
            product.setStatus(statusOrDefault(request.status()));
        }
        apply(product, request);
        log.info("Product updated: id={}", id);
        return ProductMapper.toDetail(product);
    }

    /** Soft delete: the row stays for order history, and the product disappears from the catalog. */
    @Transactional
    public void delete(Long id) {
        Product product = products.findByIdAndStatusNot(id, ProductStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setStatus(ProductStatus.DELETED);
        log.info("Product deleted (soft): id={}", id);
    }

    // ---------- helpers ----------

    private void requireCategory(Long categoryId) {
        if (!categories.existsById(categoryId)) {
            throw new InvalidRequestException("Category " + categoryId + " does not exist");
        }
    }

    private static ProductStatus statusOrDefault(ProductStatus status) {
        if (status == null) return ProductStatus.ACTIVE;
        if (status == ProductStatus.DELETED) {
            throw new InvalidRequestException("status must be ACTIVE or INACTIVE: use DELETE to remove a product");
        }
        return status;
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static void apply(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        product.setBrand(request.brand() == null || request.brand().isBlank() ? null : request.brand().trim());
        product.setCategoryId(request.categoryId());
        product.setBasePrice(request.basePrice().setScale(2, RoundingMode.HALF_UP));

        // The collections are cleared and refilled, never replaced: orphanRemoval deletes the old rows.
        product.getImages().clear();
        List<ImageRequest> images = request.images() == null ? List.of() : request.images();
        int primaryIndex = 0;
        for (int i = 0; i < images.size(); i++) {
            if (Boolean.TRUE.equals(images.get(i).primary())) { primaryIndex = i; break; }
        }
        for (int i = 0; i < images.size(); i++) {
            ImageRequest r = images.get(i);
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setUrl(r.url().trim());
            image.setSortOrder(r.sortOrder() == null ? i : r.sortOrder());
            image.setPrimaryImage(i == primaryIndex);
            product.getImages().add(image);
        }

        product.getAttributes().clear();
        if (request.attributes() != null) {
            for (AttributeRequest r : request.attributes()) {
                ProductAttribute attribute = new ProductAttribute();
                attribute.setProduct(product);
                attribute.setName(r.name().trim());
                attribute.setValue(r.value().trim());
                product.getAttributes().add(attribute);
            }
        }
    }

    /** Whitelist: the client can never sort by an arbitrary column. "id" keeps the paging stable. */
    private static Sort sortFor(String key) {
        String k = key == null || key.isBlank() ? "newest" : key.trim().toLowerCase(Locale.ROOT);
        Sort base = switch (k) {
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"));
            case "price_asc" -> Sort.by(Sort.Order.asc("basePrice"));
            case "price_desc" -> Sort.by(Sort.Order.desc("basePrice"));
            case "name" -> Sort.by(Sort.Order.asc("name"));
            case "rating" -> Sort.by(Sort.Order.desc("avgRating"), Sort.Order.desc("reviewCount"));
            default -> throw new InvalidRequestException(
                    "Unknown sort '" + key + "'. Allowed: newest, price_asc, price_desc, rating, name");
        };
        return base.and(Sort.by(Sort.Order.asc("id")));
    }
}