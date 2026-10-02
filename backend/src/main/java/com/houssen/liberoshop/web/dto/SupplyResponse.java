package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Supply;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A SUPPLY row of the stock_movement table: goods coming in.
 *
 * @param quantity         what the stock rose by, in base units: 1 750 kapoka
 * @param receivedQuantity the same goods as received: 10 for "10 sacs"
 * @param unitLabel        the unit they came in -- "sac 50 kg" -- or null for the base unit
 * @param supplier         who delivered, or null when the entry came from a product import. The UI
 *                         labels that case rather than showing a blank, since "nobody delivered
 *                         this" is information and an empty cell looks like missing data.
 * @param fromImport       said outright so a screen does not have to infer it from a null
 * @param unitCost         what one unit as received cost -- the sack's price -- or null when it
 *                         was not given
 * @param totalCost        {@code unitCost × receivedQuantity}, the supplier invoice's line, or null
 */
public record SupplyResponse(Long id,
                             BigDecimal quantity,
                             BigDecimal receivedQuantity,
                             String unitLabel,
                             LocalDateTime movementDate,
                             ProductResponse product,
                             UserResponse performedBy,
                             SupplierRefResponse supplier,
                             boolean fromImport,
                             BigDecimal unitCost,
                             BigDecimal totalCost) {

    public static SupplyResponse of(Supply supply) {
        return new SupplyResponse(
                supply.getId(),
                supply.getQuantity(),
                supply.receivedQuantity(),
                supply.getUnitLabel(),
                supply.getMovementDate(),
                ProductResponse.withoutUnits(supply.getProduct()),
                UserResponse.of(supply.getPerformedBy()),
                supply.getSupplier() == null ? null
                        : new SupplierRefResponse(supply.getSupplier().getId(),
                                supply.getSupplier().getName()),
                supply.isFromImport(),
                supply.getUnitCost(),
                supply.totalCost());
    }

    /** Just enough of the supplier to label the row. */
    public record SupplierRefResponse(Long id, String name) {
    }
}
