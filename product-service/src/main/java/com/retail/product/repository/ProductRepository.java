package com.retail.product.repository;

import com.retail.product.domain.Product;
import com.retail.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
    boolean existsByCategoryId(Long categoryId);
    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);
    Optional<Product> findByIdAndStatusNot(Long id, ProductStatus status);
}