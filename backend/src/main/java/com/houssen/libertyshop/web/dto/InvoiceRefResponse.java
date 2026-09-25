package com.houssen.libertyshop.web.dto;

import com.houssen.libertyshop.entity.DeliveryStatus;
import com.houssen.libertyshop.entity.Invoice;

import java.math.BigDecimal;

/**
 * Compact stand-in for an invoice, used where a full {@link InvoiceResponse} would be a
 * back-reference: a payment or a stock output points at its invoice, and serialising the
 * whole sale with its lines from there would send the same rows several times over.
 */
public record InvoiceRefResponse(Long id,
                                 String invoiceNumber,
                                 String clientName,
                                 BigDecimal totalAmount,
                                 DeliveryStatus deliveryStatus) {

    public static InvoiceRefResponse of(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        return new InvoiceRefResponse(invoice.getId(), invoice.getInvoiceNumber(),
                invoice.getClientName(), invoice.getSale().getTotalAmount(),
                invoice.getDeliveryStatus());
    }
}
