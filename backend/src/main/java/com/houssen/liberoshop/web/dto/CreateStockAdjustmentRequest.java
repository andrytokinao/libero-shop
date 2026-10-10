package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.AdjustmentReason;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A correction of the stock, as the operator says it. Who made it is the authenticated user.
 *
 * @param quantity in the product's base unit. For {@link AdjustmentReason#INVENTORY_COUNT} it is
 *                 what was counted on the shelf, zero included, and the server works out the
 *                 difference; for every other reason it is what was lost, and must be positive
 * @param comment  optional, except for {@link AdjustmentReason#OTHER}
 */
public record CreateStockAdjustmentRequest(@NotNull Long productId,
                                           @NotNull AdjustmentReason reason,
                                           @NotNull @PositiveOrZero
                                           @Digits(integer = 11, fraction = 3)
                                           BigDecimal quantity,
                                           @Size(max = 255) String comment) {
}
