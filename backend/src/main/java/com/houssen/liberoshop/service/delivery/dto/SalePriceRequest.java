package com.houssen.liberoshop.service.delivery.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record SalePriceRequest(

        @NotNull(message = "La source est obligatoire")
        SalePriceSource source,

        @NotNull(message = "L'index est obligatoire")
        @PositiveOrZero(message = "L'index doit être supérieur ou égal à zéro")
        Integer index,

        @NotNull(message = "Le produit est obligatoire")
        @Positive(message = "L'identifiant du produit doit être positif")
        Long productId,

        @NotEmpty(message = "La liste des unités ne doit pas être vide")
        List<@Valid UnitPriceRequest> units

) {
}

