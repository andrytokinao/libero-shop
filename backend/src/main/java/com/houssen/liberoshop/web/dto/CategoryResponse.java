package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Category;

public record CategoryResponse(Long id, String name) {

    public static CategoryResponse of(Category category) {
        return category == null ? null : new CategoryResponse(category.getId(), category.getName());
    }
}
