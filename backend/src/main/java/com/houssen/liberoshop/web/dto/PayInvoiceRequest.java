package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.PaymentMethod;

/**
 * Settling an unpaid order at the till. The amount is not asked: it is the order's total, read
 * on the server.
 *
 * @param paymentMethod cash when absent
 */
public record PayInvoiceRequest(PaymentMethod paymentMethod) {
}
