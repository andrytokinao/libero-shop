package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.CancelReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cancelling an unpaid order.
 *
 * @param comment required for {@link CancelReason#OTHER}, and for an order already handed over
 */
public record CancelInvoiceRequest(@NotNull CancelReason reason,
                                   @Size(max = 255) String comment) {
}
