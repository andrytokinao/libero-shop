package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One sale unit of a product, as the units dialog and the import send it -- for a creation and
 * an edit alike.
 *
 * <p>Written the way the unit is printed on it: "sac 25 kg" contains 25 of {@code reference}
 * "kg". How many base units that makes is the server's sum, not the operator's.
 *
 * @param factor    how many {@code reference} units it holds -- the contenance; positive, at most
 *                  three decimals
 * @param reference the unit it is written in, by its name: another unit of the same product, or
 *                  null (or the base unit's name) for the base unit itself
 * @param barcode   optional; blank means none
 */
public record PackagingRequest(@NotBlank @Size(max = Product.MAX_UNIT_LENGTH) String label,
                               @NotNull @Digits(integer = 9, fraction = 3) BigDecimal factor,
                               @NotNull @Digits(integer = 10, fraction = 2) BigDecimal price,
                               @Size(max = 64) String barcode,
                               @Size(max = Product.MAX_UNIT_LENGTH) String reference) {

    /** A unit written directly in base units, as every unit was before references. */
    public PackagingRequest(String label, BigDecimal factor, BigDecimal price, String barcode) {
        this(label, factor, price, barcode, null);
    }
}
