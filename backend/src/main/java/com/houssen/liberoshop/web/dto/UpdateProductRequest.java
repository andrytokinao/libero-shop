package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A product's sheet as the units dialog sends it: what it is called, what one base unit sells
 * for, and the code on it.
 *
 * <p>Neither the stock nor the rayon: the first moves only through a supply or a sale, the
 * second has its own screen.
 *
 * @param price   of one base unit; positive, bounded by the column so an absurd figure is a 400
 *                rather than an overflow
 * @param barcode optional; blank means none
 */
public record UpdateProductRequest(@NotBlank @Size(max = 255) String name,
                                   @NotNull @Digits(integer = 10, fraction = 2) BigDecimal price,
                                   @Size(max = 64) String barcode) {
}
