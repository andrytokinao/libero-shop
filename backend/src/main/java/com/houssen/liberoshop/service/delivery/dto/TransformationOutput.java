package com.houssen.liberoshop.service.delivery.dto;


import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransformationOutput(
        @NotNull @PositiveOrZero Integer index,       // position dans outputs côté front
        @NotNull Long productId,
        Long packagingId,                             // null = unité de base
        @NotNull @Positive BigDecimal quantity,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal costShare   // en %
) {}