package com.houssen.liberoshop.service.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.List;

public record SupplyChargeRequest(
        @NotBlank(message = "La raison est obligatoire")
        String raison,
        @NotNull(message = "Le client est obligatoire")
        @Positive(message = "L'identifiant du client doit être positif")
        Long cust,
        @NotNull(message = "La base de calcul est obligatoire")
        ChargeBasis basis,
        boolean general,
        List<Integer> lineIndexes
) {}
