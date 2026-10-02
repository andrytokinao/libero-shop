package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Goods received at the depot. The receiving agent is the authenticated user.
 *
 * @param unitCost what one unit cost from this supplier on this delivery. Optional, because the
 *                 supplier's invoice sometimes arrives after the goods; a receipt without it
 *                 still raises the stock, it just teaches the average cost nothing.
 */
public record CreateSupplyRequest(@NotNull Long productId,
                                  @NotNull Long supplierId,
                                  @NotNull @Positive
                                  @Digits(integer = 11, fraction = 3)
                                  BigDecimal quantity,
                                  @PositiveOrZero(message = "le prix d'achat ne peut pas etre negatif")
                                  @Digits(integer = 10, fraction = 2)
                                  BigDecimal unitCost) {
}
