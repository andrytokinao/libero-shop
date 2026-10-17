package com.houssen.liberoshop.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * A rayon counted in one go: what was found on the shelf, product by product.
 *
 * @param categoryId the rayon counted, named in the history; null for a count of loose products
 * @param lines      the products counted -- those left out are not touched
 */
public record CreateInventoryRequest(Long categoryId,
                                     @NotEmpty @Size(max = 500) List<@Valid Line> lines) {

    /** @param counted in base units, zero included: a shelf may be found empty */
    public record Line(@NotNull Long productId,
                       @NotNull @PositiveOrZero @Digits(integer = 11, fraction = 3) BigDecimal counted) {
    }
}
