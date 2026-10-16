package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A product's sheet as the product dialog sends it: what it is called, what one base unit sells
 * for, the code on it, the rayon it is filed under and what it costs the shop.
 *
 * <p>Not the stock: it moves only through a receipt, a sale or a correction, each of which
 * leaves a movement behind. Nor the units, which have their own requests.
 *
 * @param price          of one base unit; positive, bounded by the column so an absurd figure is
 *                       a 400 rather than an overflow
 * @param barcode        optional; blank means none
 * @param categoryId     the rayon, or null for none -- the dialog always sends the one it shows
 * @param purchaseCost   the purchase cost of one {@code purchaseUnitId}, corrected by hand -- a
 *                       tonne's price for rice bought by the tonne; null leaves the average as
 *                       the receipts made it
 * @param purchaseUnitId the unit the shop buys it in, one of its packagings, or null for the
 *                       base unit -- the dialog always sends the one it shows
 */
public record UpdateProductRequest(@NotBlank @Size(max = 255) String name,
                                   @NotNull @Digits(integer = 10, fraction = 2) BigDecimal price,
                                   @Size(max = 64) String barcode,
                                   Long categoryId,
                                   @PositiveOrZero(message = "le prix d'achat ne peut pas etre negatif")
                                   @Digits(integer = 10, fraction = 2)
                                   BigDecimal purchaseCost,
                                   Long purchaseUnitId) {
}
