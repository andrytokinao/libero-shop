package com.houssen.libertyshop.web.dto;

import com.houssen.libertyshop.entity.Supply;

import java.time.LocalDateTime;

/** A SUPPLY row of the stock_movement table: goods coming in. */
public record SupplyResponse(Long id,
                             int quantity,
                             LocalDateTime movementDate,
                             ProductResponse product,
                             UserResponse performedBy,
                             SupplierRefResponse supplier) {

    public static SupplyResponse of(Supply supply) {
        return new SupplyResponse(
                supply.getId(),
                supply.getQuantity(),
                supply.getMovementDate(),
                ProductResponse.of(supply.getProduct()),
                UserResponse.of(supply.getPerformedBy()),
                new SupplierRefResponse(supply.getSupplier().getId(), supply.getSupplier().getName()));
    }

    /** Just enough of the supplier to label the row. */
    public record SupplierRefResponse(Long id, String name) {
    }
}
