package com.houssen.liberoshop.service.delivery.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record FullDeliveryRequest(
        Long supplierId,                              // nullable
        @NotEmpty @Valid List<DeliveryLineRequest> lines,
        @Valid List<SupplyChargeRequest> charges,
        @Valid List<LossRequest> losses,
        @Valid TransformationRequest transformation,
        @Valid List<SalePriceRequest> salePrices
) {}