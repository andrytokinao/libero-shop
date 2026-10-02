package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.CancelReason;
import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.SaleLine;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param itemCount    total quantity on the invoice, each line in its own unit, so the lists do
 *                     not have to sum the lines client-side just to show a column
 * @param cancellation who cancelled the order, when and why; null for an order that stands
 * @param cashTrail    where the money is while it is not in the till -- see {@link CashTrail};
 *                     null when it is in the till, when there is none, or when the response was
 *                     built without looking ({@link #of(Invoice)})
 * @param handledBy    who is preparing the order (in progress) or served it (delivered); null
 *                     while it waits in the queue
 * @param handledSince when they took it on
 */
public record InvoiceResponse(Long id,
                              String invoiceNumber,
                              LocalDateTime invoiceDate,
                              String clientName,
                              PaymentStatus paymentStatus,
                              DeliveryStatus deliveryStatus,
                              boolean printed,
                              BigDecimal itemCount,
                              SaleResponse sale,
                              Cancellation cancellation,
                              CashTrail cashTrail,
                              UserResponse handledBy,
                              LocalDateTime handledSince) {

    /** Without the cash trail: for the responses that only confirm what was just done. */
    public static InvoiceResponse of(Invoice invoice) {
        return of(invoice, null);
    }

    public static InvoiceResponse of(Invoice invoice, CashTrail cashTrail) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getClientName(),
                invoice.getPaymentStatus(),
                invoice.getDeliveryStatus(),
                invoice.isPrinted(),
                invoice.getSale().getLines().stream().map(SaleLine::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add),
                SaleResponse.of(invoice.getSale()),
                Cancellation.of(invoice),
                cashTrail,
                UserResponse.of(invoice.getHandledBy()),
                invoice.getHandledSince());
    }

    /**
     * The order's cash on its way to the till: who took it from the customer and still holds it
     * ({@code COLLECTED}), or the slip they handed in with it, awaiting a cashier's count
     * ({@code REMITTED}). What lets a screen say "argent détenu par Fatima" and offer the right
     * person the right button.
     *
     * @param holderId     who took the money -- and, a remittance only carrying its submitter's
     *                     own cash, who handed the slip in
     * @param remittanceId the slip awaiting confirmation; null while the cash is still in hand
     * @param holderPhotoVersion the holder's {@link UserResponse#photoVersion()}, so the screens
     *                     show their face beside the money
     */
    public record CashTrail(Long holderId, String holderName, Long remittanceId, Long holderPhotoVersion) {

        public static CashTrail of(Payment payment) {
            return new CashTrail(payment.getCollectedBy().getId(), payment.getCollectedBy().getFullName(),
                    payment.getCashRemittance() == null ? null : payment.getCashRemittance().getId(),
                    payment.getCollectedBy().getPhotoVersion());
        }
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
