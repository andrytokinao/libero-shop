package com.houssen.liberoshop.service;

import java.math.BigDecimal;

/**
 * An order was handed over at the depot: it leaves the queue of orders to hand over.
 *
 * <p>Published by {@link InvoiceService} inside the hand-over transaction and meant to be heard
 * after it commits, like {@link SaleRecordedEvent}. Plain values only, for the same reason.
 *
 * @param sellerId  who made the sale at the desk, and so wants to know the customer got the goods
 * @param agentId   who handed it over
 * @param collected cash taken at hand-over; zero when the order was paid at the desk
 */
public record OrderDeliveredEvent(Long invoiceId,
                                  String invoiceNumber,
                                  String clientName,
                                  Long sellerId,
                                  Long agentId,
                                  String agentName,
                                  BigDecimal collected) {
}
