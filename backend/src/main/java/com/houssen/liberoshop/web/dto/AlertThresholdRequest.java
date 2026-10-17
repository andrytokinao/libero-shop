package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * A product's own alert threshold, in base units.
 *
 * @param threshold null to go back to the shop's common threshold
 */
public record AlertThresholdRequest(@PositiveOrZero(message = "le seuil d'alerte ne peut pas etre negatif")
                                    @Digits(integer = 11, fraction = 3)
                                    BigDecimal threshold) {
}
