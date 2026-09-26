package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new rayon.
 *
 * @param parentId the rayon it goes inside, or null for a root
 */
public record CreateCategoryRequest(@NotBlank @Size(max = 60) String name, Long parentId) {
}
