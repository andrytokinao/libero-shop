package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import jakarta.validation.constraints.Size;

/**
 * Renames the unit a product's stock is counted in.
 *
 * @param unit "kapoka", "pièce"; blank clears it, for a product counted in bare units
 */
public record BaseUnitRequest(@Size(max = Product.MAX_UNIT_LENGTH) String unit) {
}
