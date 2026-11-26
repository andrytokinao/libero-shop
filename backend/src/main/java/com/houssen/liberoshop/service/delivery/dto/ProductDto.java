package com.houssen.liberoshop.service.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Miroir partiel de Product (core/models).
 * Utilisés dans SupplyService : id et name.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
        Long id,
        String name
        // TODO : ajouter les autres champs de Product
) {}
