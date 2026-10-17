package com.houssen.liberoshop.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * A delivery note: one supplier, several products, booked at once.
 *
 * @param supplierId who delivered, or null when no supplier is named
 * @param lines      what came, each as {@link CreateSupplyRequest} would say it
 */
public record CreateDeliveryRequest(Long supplierId,
                                    @NotEmpty @Size(max = 200) List<@Valid Line> lines) {

    /**
     * @param packagingId the unit it came in, or null for the base unit
     * @param quantity    in that unit
     * @param unitCost    what one of that unit cost, or null when not known yet
     */
    public record Line(@NotNull Long productId,
                       Long packagingId,
                       @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity,
                       @PositiveOrZero(message = "le prix d'achat ne peut pas etre negatif")
                       @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {
    }
}
