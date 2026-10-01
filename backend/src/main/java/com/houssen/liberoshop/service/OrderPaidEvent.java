package com.houssen.liberoshop.service;

/**
 * An unpaid order was settled at the till. Published by {@link InvoiceService} and heard after
 * the commit, like {@link OrderDeliveredEvent}.
 */
public record OrderPaidEvent(Long invoiceId, String invoiceNumber) {
}
