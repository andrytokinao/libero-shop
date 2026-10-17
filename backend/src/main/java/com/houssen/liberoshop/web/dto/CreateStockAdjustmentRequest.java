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
 * @param quantity in {@code packagingId}'s unit, the base unit when null. For {@link AdjustmentReason#INVENTORY_COUNT} it is
 *                 what was counted on the shelf, zero included, and the server works out the
 *                 difference; for every other reason it is what was lost, and must be positive
 * @param comment  optional, except for {@link AdjustmentReason#OTHER}
 * @param packagingId the unit the quantity is said in -- "2 cartons cassés" -- or null for the base unit
 */
public record CreateStockAdjustmentRequest(@NotNull Long productId,
                                           @NotNull AdjustmentReason reason,
                                           @NotNull @PositiveOrZero
                                           @Digits(integer = 11, fraction = 3)
                                           BigDecimal quantity,
                                           @Size(max = 200) String comment,
                                           Long packagingId) {

    /** A correction said in the base unit. */
    public CreateStockAdjustmentRequest(Long productId, AdjustmentReason reason, BigDecimal quantity, String comment) {
        this(productId, reason, quantity, comment, null);
    }
}
