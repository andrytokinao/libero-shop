package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Supply;

import java.time.LocalDateTime;

/**
 * A SUPPLY row of the stock_movement table: goods coming in.
 *
 * @param supplier who delivered, or null when the entry came from a product import. The UI
 *                 labels that case rather than showing a blank, since "nobody delivered this"
 *                 is information and an empty cell looks like missing data.
 * @param fromImport said outright so a screen does not have to infer it from a null
 */
public record SupplyResponse(Long id,
                             int quantity,
                             LocalDateTime movementDate,
                             ProductResponse product,
                             UserResponse performedBy,
                             SupplierRefResponse supplier,
                             boolean fromImport) {

    public static SupplyResponse of(Supply supply) {
        return new SupplyResponse(
                supply.getId(),
                supply.getQuantity(),
                supply.getMovementDate(),
                ProductResponse.of(supply.getProduct()),
                UserResponse.of(supply.getPerformedBy()),
                supply.getSupplier() == null ? null
                        : new SupplierRefResponse(supply.getSupplier().getId(),
                                supply.getSupplier().getName()),
                supply.isFromImport());
    }

    /** Just enough of the supplier to label the row. */
    public record SupplierRefResponse(Long id, String name) {
    }
}
