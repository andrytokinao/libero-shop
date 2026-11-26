package com.houssen.liberoshop.service.delivery.dto;




import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductionCostRequest(
        @NotBlank String raison,
        @NotNull @Positive BigDecimal cost
) {}