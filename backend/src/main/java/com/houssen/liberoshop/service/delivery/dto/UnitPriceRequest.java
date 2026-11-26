package com.houssen.liberoshop.service.delivery.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UnitPriceRequest(
        Long packagingId,

        @NotNull(message = "Le prix est obligatoire")
        @Positive(message = "Le prix doit être supérieur à zéro")
        @Digits(integer = 10, fraction = 2)
        BigDecimal price
) {
}
