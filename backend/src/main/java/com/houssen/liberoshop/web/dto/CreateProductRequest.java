package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A product typed in by hand, one at a time -- what the import does by the hundred.
 *
 * @param price        of one base unit; positive
 * @param unit         how the shelf counts it -- "kg", "bouteille"; blank for bare units
 * @param barcode      optional; blank means none
 * @param categoryId   the rayon it is filed under, or null for none
 * @param purchaseCost what one base unit costs the shop, or null when it is not known yet
 * @param initialStock what is already on the shelf, in base units; null or zero for none. Booked
 *                     as an entry without a supplier, like an import's, so the stock of a product
 *                     is explained by its movements from the first unit
 * @param units        units ticked in a model of units ("Poids"), created with the product; null
 *                     for none
 */
public record CreateProductRequest(@NotBlank @Size(max = 255) String name,
                                   @NotNull @Digits(integer = 10, fraction = 2) BigDecimal price,
                                   @Size(max = Product.MAX_UNIT_LENGTH) String unit,
                                   @Size(max = 64) String barcode,
                                   Long categoryId,
                                   @PositiveOrZero(message = "le prix d'achat ne peut pas etre negatif")
                                   @Digits(integer = 10, fraction = 2)
                                   BigDecimal purchaseCost,
                                   @PositiveOrZero(message = "le stock de depart ne peut pas etre negatif")
                                   @Digits(integer = 11, fraction = 3)
                                   BigDecimal initialStock,
                                   @Valid ApplyUnitModelRequest units) {

    /** A product sold in its base unit only, as every product was created before models. */
    public CreateProductRequest(String name, BigDecimal price, String unit, String barcode, Long categoryId,
                                BigDecimal purchaseCost, BigDecimal initialStock) {
        this(name, price, unit, barcode, categoryId, purchaseCost, initialStock, null);
    }
}
