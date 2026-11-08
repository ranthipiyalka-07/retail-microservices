package com.retail.product.service;

import com.retail.common.error.ResourceNotFoundException;
import com.retail.product.domain.Category;
import com.retail.product.dto.CategoryRequest;
import com.retail.product.dto.CategoryResponse;
import com.retail.product.exception.ConflictException;
import com.retail.product.exception.InvalidRequestException;
import com.retail.product.repository.CategoryRepository;
import com.retail.product.repository.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAllByOrderByNameAsc().stream().map(CategoryService::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (request.parentId() != null && !categories.existsById(request.parentId())) {
            throw new InvalidRequestException("Parent category " + request.parentId() + " does not exist");
        }
        String slug = slugFor(request);
        if (categories.existsBySlug(slug)) {
            throw new ConflictException("A category with slug '" + slug + "' already exists");
        }
        Category category = new Category();
        category.setName(request.name().trim());
        category.setParentId(request.parentId());
        category.setSlug(slug);
        category.setActive(request.active() == null || request.active());
        try {
            return toResponse(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A category with slug '" + slug + "' already exists");
        }
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));

        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new InvalidRequestException("A category cannot be its own parent");
            }
            if (!categories.existsById(request.parentId())) {
                throw new InvalidRequestException("Parent category " + request.parentId() + " does not exist");
            }
            if (idsWithDescendants(id).contains(request.parentId())) {
                throw new InvalidRequestException("A category cannot be moved under one of its own sub-categories");
            }
        }
        String slug = slugFor(request);
        if (categories.existsBySlugAndIdNot(slug, id)) {
            throw new ConflictException("A category with slug '" + slug + "' already exists");
        }

        category.setName(request.name().trim());
        category.setParentId(request.parentId());
        category.setSlug(slug);
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return toResponse(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
        if (categories.existsByParentId(id)) {
            throw new ConflictException("Category has sub-categories: move or delete them first");
        }
        if (products.existsByCategoryId(id)) {
            throw new ConflictException("Category still has products: move them to another category first");
        }
        categories.delete(category);
    }

    /** The category itself plus all its descendants. Always contains rootId, even if it does not exist. */
    @Transactional(readOnly = true)
    public Set<Long> idsWithDescendants(Long rootId) {
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (Category c : categories.findAll()) {
            if (c.getParentId() != null) {
                childrenByParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (result.add(current)) {
                queue.addAll(childrenByParent.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    private static String slugFor(CategoryRequest request) {
        String source = request.slug() != null && !request.slug().isBlank() ? request.slug() : request.name();
        String slug = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            throw new InvalidRequestException("Could not build a slug from the name: please provide a 'slug'");
        }
        return slug;
    }

    private static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getParentId(), c.getSlug(), c.isActive());
    }
}