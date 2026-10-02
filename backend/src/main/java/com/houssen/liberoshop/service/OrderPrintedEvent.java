package com.houssen.liberoshop.service;

/**
 * An invoice was sent to the printer: the other screens' "Imprimer" turns to "Réimprimer".
 * Published by {@link InvoiceService} and heard after the commit, like {@link OrderPaidEvent}.
 */
public record OrderPrintedEvent(Long invoiceId, String invoiceNumber) {
}
