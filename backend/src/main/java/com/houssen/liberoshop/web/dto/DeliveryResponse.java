package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;

/**
 * Outcome of a depot hand-over.
 *
 * @param collected cash taken on the spot; zero when the order was already paid
 * @param message   the confirmation wording, composed server-side so the rule "an unpaid
 *                  order is settled at hand-over" is explained in one place
 */
public record DeliveryResponse(InvoiceResponse invoice, BigDecimal collected, String message) {
}
