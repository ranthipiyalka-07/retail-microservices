package com.retail.product.repository;

import com.retail.product.domain.Product;
import com.retail.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
    boolean existsByCategoryId(Long categoryId);
    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);
    Optional<Product> findByIdAndStatusNot(Long id, ProductStatus status);
    /** Changes nothing (returns 0) unless the pushed version is newer than the stored one. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE products SET avg_rating = :average, review_count = :reviewCount, rating_version = :version, "
            + "updated_at = :now WHERE id = :id AND rating_version < :version", nativeQuery = true)
    int updateRating(@Param("id") Long id, @Param("average") BigDecimal average,
                     @Param("reviewCount") int reviewCount, @Param("version") long version, @Param("now") Instant now);
    java.util.List<Product> findByIdInAndStatus(java.util.Collection<Long> ids, ProductStatus status);
}