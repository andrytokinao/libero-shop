package com.houssen.liberoshop.service.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Perte en quantité sur une ligne d'achat (dans l'unité de réception de la ligne). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LossEntryDto(
        LossReasonCode reason,
        Integer lineIndex,        // nullable
        String quantity
) {}
