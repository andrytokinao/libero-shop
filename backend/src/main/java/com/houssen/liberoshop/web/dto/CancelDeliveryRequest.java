package com.houssen.liberoshop.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A delivery note taken back whole, right after it was booked.
 *
 * @param supplyIds the receipts the note created
 */
public record CancelDeliveryRequest(@NotEmpty @Size(max = 200) List<@NotNull Long> supplyIds) {
}
