package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * One sale price corrected in the "Prix et unites" table.
 *
 * @param packagingId the unit whose price changes, or null for the base unit
 * @param price       what one of that unit now sells for
 */
public record SalePriceRequest(Long packagingId,
                               @NotNull @Positive(message = "le prix doit etre superieur a zero")
                               @Digits(integer = 10, fraction = 2) BigDecimal price) {
}
