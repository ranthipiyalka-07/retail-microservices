package com.retail.product.dto;

public record CategoryResponse(Long id, String name, Long parentId, String slug, boolean active) {}