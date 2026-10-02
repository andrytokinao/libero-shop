package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Goods received at the depot, as the supplier's invoice says it. The receiving agent is the
 * authenticated user.
 *
 * @param packagingId the unit the goods came in -- "sac 50 kg" -- or null for the base unit
 * @param quantity    in that unit: 10 for ten sacks
 * @param unitCost    what one of that unit cost from this supplier on this delivery: the sack's
 *                    price. Optional, because the supplier's invoice sometimes arrives after the
 *                    goods; a receipt without it still raises the stock, it just teaches the
 *                    average cost nothing.
 */
public record CreateSupplyRequest(@NotNull Long productId,
                                  @NotNull Long supplierId,
                                  Long packagingId,
                                  @NotNull @Positive
                                  @Digits(integer = 11, fraction = 3)
                                  BigDecimal quantity,
                                  @PositiveOrZero(message = "le prix d'achat ne peut pas etre negatif")
                                  @Digits(integer = 10, fraction = 2)
                                  BigDecimal unitCost) {

    /** In the base unit, the only kind of receipt there was before units. */
    public CreateSupplyRequest(Long productId, Long supplierId, BigDecimal quantity, BigDecimal unitCost) {
        this(productId, supplierId, null, quantity, unitCost);
    }
}
