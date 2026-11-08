package com.retail.product.web;

import com.retail.product.dto.PageResponse;
import com.retail.product.dto.ProductDetail;
import com.retail.product.dto.ProductRequest;
import com.retail.product.dto.ProductSummary;
import com.retail.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<ProductSummary> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) String brand,
                                               @RequestParam(required = false) BigDecimal minPrice,
                                               @RequestParam(required = false) BigDecimal maxPrice,
                                               @RequestParam(required = false) BigDecimal minRating,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return productService.search(q, categoryId, brand, minPrice, maxPrice, minRating, sort, page, size);
    }

    @GetMapping("/{id}")
    public ProductDetail get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetail create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductDetail update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}