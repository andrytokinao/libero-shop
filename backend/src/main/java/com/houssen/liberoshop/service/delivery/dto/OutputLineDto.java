package com.houssen.liberoshop.service.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Produit obtenu par la transformation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutputLineDto(
        ProductDto product,
        Long packagingId,         // nullable : unité de base
        String quantity,
        BigDecimal costShare      // nullable, part du coût en %
) {}
