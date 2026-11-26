package com.houssen.liberoshop.service.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Prix de vente d'un produit dans une unité, et si on le vend dans cette unité. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnitPriceDto(
        boolean on,
        String price
) {}
