package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One sale unit of a product, as the units dialog sends it -- for a creation and an edit alike.
 *
 * @param factor  how many base units it holds; positive, at most three decimals
 * @param barcode optional; blank means none
 */
public record PackagingRequest(@NotBlank @Size(max = Product.MAX_UNIT_LENGTH) String label,
                               @NotNull BigDecimal factor,
                               @NotNull BigDecimal price,
                               @Size(max = 64) String barcode) {
}
