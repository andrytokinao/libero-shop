package com.houssen.liberoshop.entity;

import com.houssen.liberoshop.entity.ChargeBasis;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * Une charge payée pour faire arriver la marchandise : transport, embarquement...
 *
 * <p>{@code cust} est le montant TOTAL de la charge, déjà calculé par le front (lot, ou unité x
 * quantité). {@code basis} dit seulement comment il a été saisi.
 * {@code general} : la charge couvre toutes les lignes ; sinon {@code lineIndexes} désigne la ou les
 * lignes concernées.
 */
public record SupplyChargeRequest(
        @NotBlank(message = "La raison est obligatoire")
        String raison,

        @NotNull(message = "Le montant est obligatoire")
        @Positive(message = "Le montant doit être supérieur à zéro")
        @Digits(integer = 10, fraction = 2)
        BigDecimal cust,

        @NotNull(message = "La base de calcul est obligatoire")
        ChargeBasis basis,

        boolean general,

        List<Integer> lineIndexes
) {}
