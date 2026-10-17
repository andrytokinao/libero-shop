package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The products to file in a rayon: one, picked, or a few. */
public record FileProductsRequest(@NotEmpty @Size(max = 500) List<@NotNull Long> productIds) {
}
