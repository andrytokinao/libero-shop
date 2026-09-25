package com.houssen.libertyshop.web.dto;

import com.houssen.libertyshop.entity.Category;

public record CategoryResponse(Long id, String name) {

    public static CategoryResponse of(Category category) {
        return category == null ? null : new CategoryResponse(category.getId(), category.getName());
    }
}
