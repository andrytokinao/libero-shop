package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Units of a model ticked for a product, each with the product's own price.
 *
 * <p>The anchor says how the model meets the product when the product does not already sell in
 * one of the model's units: "1 kg = 3,5 kapoka" for rice counted in kapoka. Ignored when it does
 * -- a product counted in kg, or one that already has its kg -- since the product then says it.
 *
 * @param anchorLabel    one of the model's units
 * @param anchorQuantity how many base units of the product one {@code anchorLabel} holds
 */
public record ApplyUnitModelRequest(@NotNull Long modelId,
                                    @Size(max = Product.MAX_UNIT_LENGTH) String anchorLabel,
                                    BigDecimal anchorQuantity,
                                    @NotEmpty @Valid List<Choice> units) {

    /** One unit of the model, at its price for this product. */
    public record Choice(@NotBlank @Size(max = Product.MAX_UNIT_LENGTH) String label,
                         @NotNull BigDecimal price) {
    }
}
