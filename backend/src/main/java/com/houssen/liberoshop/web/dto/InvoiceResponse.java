package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.CancelReason;
import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.PaymentStatus;

import java.time.LocalDateTime;

/**
 * @param itemCount    total units on the invoice, so the lists do not have to sum the lines
 *                     client-side just to show a column
 * @param cancellation who cancelled the order, when and why; null for an order that stands
 */
public record InvoiceResponse(Long id,
                              String invoiceNumber,
                              LocalDateTime invoiceDate,
                              String clientName,
                              PaymentStatus paymentStatus,
                              DeliveryStatus deliveryStatus,
                              boolean printed,
                              int itemCount,
                              SaleResponse sale,
                              Cancellation cancellation) {

    public static InvoiceResponse of(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getClientName(),
                invoice.getPaymentStatus(),
                invoice.getDeliveryStatus(),
                invoice.isPrinted(),
                invoice.getSale().getLines().stream().mapToInt(line -> line.getQuantity()).sum(),
                SaleResponse.of(invoice.getSale()),
                Cancellation.of(invoice));
    }

    /** @param byName who cancelled it -- a name, not an account: the list only displays it */
    public record Cancellation(LocalDateTime at, String byName, CancelReason reason, String comment) {

        static Cancellation of(Invoice invoice) {
            if (invoice.getCancelledAt() == null) {
                return null;
            }
            return new Cancellation(invoice.getCancelledAt(),
                    invoice.getCancelledBy() == null ? null : invoice.getCancelledBy().getFullName(),
                    invoice.getCancelReason(), invoice.getCancelComment());
        }
    }
}
