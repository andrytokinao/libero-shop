package com.houssen.libertyshop.service;

import com.houssen.libertyshop.entity.DeliveryStatus;
import com.houssen.libertyshop.entity.Invoice;
import com.houssen.libertyshop.entity.Payment;
import com.houssen.libertyshop.entity.PaymentMethod;
import com.houssen.libertyshop.entity.PaymentStatus;
import com.houssen.libertyshop.entity.UserApp;
import com.houssen.libertyshop.license.RequiresActiveLicense;
import com.houssen.libertyshop.repository.InvoiceRepository;
import com.houssen.libertyshop.repository.PaymentRepository;
import com.houssen.libertyshop.service.exception.BusinessRuleException;
import com.houssen.libertyshop.service.exception.ResourceNotFoundException;
import com.houssen.libertyshop.web.dto.InvoiceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Invoice lookup and the depot hand-over.
 *
 * <p>Handing an unpaid order over is the moment the money changes hands, so it is also the
 * moment a {@link Payment} is written, collected by the agent doing the hand-over. That
 * payment stays un-remitted until the agent takes the cash to the desk, which is what makes
 * the whole depot-cash trail auditable.
 */
@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final BusinessCalendar calendar;

    public InvoiceService(InvoiceRepository invoices, PaymentRepository payments, BusinessCalendar calendar) {
        this.invoices = invoices;
        this.payments = payments;
        this.calendar = calendar;
    }

    /**
     * The one query behind every invoice list in the UI.
     *
     * @param sellerId       restrict to one seller, or null for all
     * @param paymentStatus  null for both
     * @param deliveryStatus null for both
     * @param todayOnly      restrict to the current business day
     * @param search         free text on number, client and seller name
     */
    public List<InvoiceResponse> search(Long sellerId, PaymentStatus paymentStatus,
                                        DeliveryStatus deliveryStatus, boolean todayOnly, String search) {
        LocalDateTime from = todayOnly ? calendar.startOfToday() : null;
        LocalDateTime to = todayOnly ? calendar.startOfTomorrow() : null;
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        return invoices.search(sellerId, paymentStatus, deliveryStatus, from, to).stream()
                .filter(invoice -> needle.isEmpty() || matches(invoice, needle))
                .map(InvoiceResponse::of)
                .toList();
    }

    public InvoiceResponse findById(Long id) {
        return InvoiceResponse.of(load(id));
    }

    /**
     * Hands the order to the customer. Settles it in cash first when it was left unpaid at
     * the desk.
     *
     * @return the updated invoice; {@code collected} tells the UI how much was taken in
     */
    @RequiresActiveLicense
    @Transactional
    public DeliveryResult deliver(Long invoiceId, UserApp agent) {
        Invoice invoice = load(invoiceId);
        if (invoice.getDeliveryStatus() == DeliveryStatus.DELIVERED) {
            throw new BusinessRuleException("ALREADY_DELIVERED",
                    "La commande " + invoice.getInvoiceNumber() + " a deja ete remise.");
        }

        BigDecimal collected = BigDecimal.ZERO;
        if (invoice.getPaymentStatus() == PaymentStatus.UNPAID) {
            collected = invoice.getSale().getTotalAmount();
            payments.save(Payment.builder()
                    .amount(collected)
                    .paymentMethod(PaymentMethod.CASH)
                    .paymentDate(calendar.now())
                    .invoice(invoice)
                    .collectedBy(agent)
                    // Left null: the agent holds the cash until they remit it.
                    .cashRemittance(null)
                    .build());
            invoice.setPaymentStatus(PaymentStatus.PAID);
            invoice.getSale().setPaymentStatus(PaymentStatus.PAID);
        }

        invoice.setDeliveryStatus(DeliveryStatus.DELIVERED);
        return new DeliveryResult(InvoiceResponse.of(invoice), collected);
    }

    /** Front-end counterpart of {@code Invoice#print()}. */
    @RequiresActiveLicense
    @Transactional
    public InvoiceResponse markPrinted(Long invoiceId) {
        Invoice invoice = load(invoiceId);
        invoice.print();
        return InvoiceResponse.of(invoice);
    }

    public long countPendingDeliveries() {
        return invoices.countByDeliveryStatus(DeliveryStatus.PENDING);
    }

    public long countDelivered() {
        return invoices.countByDeliveryStatus(DeliveryStatus.DELIVERED);
    }

    public long countUnpaid() {
        return invoices.countByPaymentStatus(PaymentStatus.UNPAID);
    }

    public BigDecimal unpaidAmount() {
        return invoices.sumAmountByPaymentStatus(PaymentStatus.UNPAID);
    }

    private Invoice load(Long id) {
        return invoices.findDetailedById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Facture", id));
    }

    private static boolean matches(Invoice invoice, String needle) {
        return invoice.getInvoiceNumber().toLowerCase(Locale.ROOT).contains(needle)
                || invoice.getClientName().toLowerCase(Locale.ROOT).contains(needle)
                || invoice.getSale().getSeller().getFullName().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** @param collected cash taken at hand-over; zero when the order was already paid */
    public record DeliveryResult(InvoiceResponse invoice, BigDecimal collected) {
    }
}
