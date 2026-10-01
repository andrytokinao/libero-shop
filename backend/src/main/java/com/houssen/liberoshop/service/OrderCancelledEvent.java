package com.houssen.liberoshop.service;

/**
 * An unpaid order was cancelled: it leaves the depot queue and the till's list of orders to
 * collect. Published by {@link InvoiceService} and heard after the commit.
 */
public record OrderCancelledEvent(Long invoiceId, String invoiceNumber) {
}
