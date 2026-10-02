package com.houssen.liberoshop.service;

/**
 * Someone took an order on ("je m'en occupe"), or gave it back to the queue. Published by
 * {@link InvoiceService} and heard after the commit, like {@link OrderPaidEvent}: every depot
 * screen shows at once who is on it, so nobody else starts the same order.
 */
public record OrderHandlingChangedEvent(Long invoiceId, String invoiceNumber) {
}
