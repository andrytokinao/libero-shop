package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.UnitModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A new model of units, with the units it starts with -- the one just added to a product, when
 * the model is created from the product's sheet.
 *
 * @param baseLabel the unit the others are counted in: "g", "kg"
 * @param units     in the order they are written: each in the reference unit or in one before it;
 *                  null or empty for none yet
 */
public record CreateUnitModelRequest(@NotBlank @Size(max = UnitModel.MAX_NAME_LENGTH) String name,
                                     @NotBlank @Size(max = Product.MAX_UNIT_LENGTH) String baseLabel,
                                     @Valid List<UnitModelUnitRequest> units) {
}
