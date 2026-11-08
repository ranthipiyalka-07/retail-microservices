package com.retail.product.repository;

import com.retail.product.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByNameAsc();
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
    boolean existsByParentId(Long parentId);
}