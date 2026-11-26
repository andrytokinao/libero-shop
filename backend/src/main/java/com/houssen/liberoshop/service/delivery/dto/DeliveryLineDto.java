package com.houssen.liberoshop.service.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Miroir partiel de DeliveryLine (core/stock/delivery-note).
 * Seuls les champs utilisés dans SupplyService sont présents.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryLineDto(
        ProductDto product,
        String quantity,
        String unitCost,          // nullable ; String car saisi (virgule possible)
        Long packagingId          // nullable
        // TODO : ajouter les autres champs de DeliveryLine
) {}
