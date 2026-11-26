package com.houssen.liberoshop.service.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** Miroir de l'interface EntryState : tout ce que l'utilisateur a saisi dans la fiche. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EntryStateDto(
        List<DeliveryLineDto> lines,
        List<ChargeLineDto> charges,
        boolean withLosses,
        List<LossEntryDto> losses,

        boolean withTransformation,
        List<MaterialUseDto> materials,
        List<OutputLineDto> outputs,
        List<ProductionCostLineDto> productionCosts,
        /** Clé : "<cardKey>|<packagingId ou 'base'>", ex. "L0|base", "O1|12". */
        Map<String, UnitPriceDto> sale
) {}
