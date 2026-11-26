package com.houssen.liberoshop.service.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * general = true  : montant total de la réception, réparti sur tous les produits.
 * general = false : s'applique à la ligne d'achat lineIndex, par lot ou par unité.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChargeLineDto(
        String label,
        BigDecimal amount,        // nullable
        ChargeBasis basis,
        boolean general,
        Integer lineIndex         // nullable
) {}
