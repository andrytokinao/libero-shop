package com.houssen.liberoshop.service.delivery.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TransformationRequest(
        @NotEmpty @Valid List<TransformationInput> inputs,
        @Valid List<ProductionCostRequest> productionCosts,
        @NotEmpty @Valid List<TransformationOutput> outputs
) {
    public TransformationRequest {
        productionCosts = productionCosts == null ? List.of() : productionCosts;
    }
}