package com.houssen.liberoshop.service.delivery.dto;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record TransformationInput(
        @NotNull @PositiveOrZero Integer lineIndex,   // index dans lines
        @NotNull @Positive BigDecimal quantity        // pertes de fabrication comprises
) {}
