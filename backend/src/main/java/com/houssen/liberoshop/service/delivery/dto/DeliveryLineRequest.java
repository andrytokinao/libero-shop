package com.houssen.liberoshop.service.delivery.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DeliveryLineRequest(
        @NotNull Long productId,
        Long packagingId,                             // null = unité de base
        @NotNull @Positive BigDecimal quantity,
        BigDecimal unitCost                           // nullable
) {}