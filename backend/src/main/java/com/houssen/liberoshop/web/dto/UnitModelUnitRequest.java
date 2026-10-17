package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One unit added to a model, written like a product's: "tonne" contient 1000 "kg".
 *
 * @param quantity  how many {@code reference} units it holds; positive, at most three decimals
 * @param reference another unit of the model, or null (or the model's reference unit) for that one
 */
public record UnitModelUnitRequest(@NotBlank @Size(max = Product.MAX_UNIT_LENGTH) String label,
                                   @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal quantity,
                                   @Size(max = Product.MAX_UNIT_LENGTH) String reference) {
}
