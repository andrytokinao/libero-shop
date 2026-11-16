package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.printer.pdf.HtmlPdfRenderer;
import com.houssen.liberoshop.printer.pdf.PaymentDocument;
import com.houssen.liberoshop.printer.service.ShopInfoService;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The receipt of a payment as a PDF, to print or to hand to the customer. Like
 * {@link InvoicePdfService}, it is reached through the invoice: readable by whoever may read
 * the invoice itself, and by nobody else.
 *
 * <p>Laid out by the Thymeleaf templates {@code templates/pdf/payment-receipt.html} (A4) and
 * {@code payment-ticket.html} (a till roll), filled with a {@link PaymentDocument}: the same
 * one the native ESC/POS ticket is written from, see {@link #documentFor}.
 */
@Service
@Transactional(readOnly = true)
public class PaymentPdfService {

    static final String TEMPLATE = "pdf/payment-receipt";
    static final String TICKET_TEMPLATE = "pdf/payment-ticket";

    private final InvoiceService invoices;
    private final PaymentRepository payments;
    private final ShopInfoService shops;
    private final HtmlPdfRenderer pdfs;
    private final String currency;

    /**
     * @param shops    the shop's name, address and contact, printed at the top of every receipt
     * @param currency symbol written after every amount
     */
    public PaymentPdfService(InvoiceService invoices,
                             PaymentRepository payments,
                             ShopInfoService shops,
                             HtmlPdfRenderer pdfs,
                             @Value("${liberoshop.currency:Ar}") String currency) {
        this.invoices = invoices;
        this.payments = payments;
        this.shops = shops;
        this.pdfs = pdfs;
        this.currency = currency;
    }

    /** The A4 receipt of one payment of the invoice. */
    public PaymentPdf receiptFor(Long invoiceId, Long paymentId, UserApp reader) {
        return render(documentFor(invoiceId, paymentId, reader), TEMPLATE);
    }

    /** The same receipt laid out for a till roll. */
    public PaymentPdf ticketFor(Long invoiceId, Long paymentId, UserApp reader) {
        return render(documentFor(invoiceId, paymentId, reader), TICKET_TEMPLATE);
    }

    /**
     * The A4 receipt of the last payment of the invoice: what the till prints right after taking
     * the money, knowing only the invoice.
     *
     * @throws BusinessRuleException when nothing has been paid on the invoice yet
     */
    public PaymentPdf lastReceiptFor(Long invoiceId, UserApp reader) {
        return render(lastDocumentFor(invoiceId, reader), TEMPLATE);
    }

    /** The last payment's receipt, laid out for a till roll. */
    public PaymentPdf lastTicketFor(Long invoiceId, UserApp reader) {
        return render(lastDocumentFor(invoiceId, reader), TICKET_TEMPLATE);
    }

    /**
     * What the receipt says, already formatted, for whoever lays it out.
     *
     * @throws org.springframework.security.access.AccessDeniedException when the invoice is not
     *         theirs to read
     * @throws ResourceNotFoundException when the payment does not exist, or belongs to another invoice
     */
    public PaymentDocument documentFor(Long invoiceId, Long paymentId, UserApp reader) {
        invoices.findByIdFor(invoiceId, reader);
        Payment payment = payments.findDetailedById(paymentId)
                .filter(found -> found.getInvoice().getId().equals(invoiceId))
                .orElseThrow(() -> ResourceNotFoundException.of("Paiement", paymentId));
        BigDecimal paidSoFar = payments.sumUpTo(invoiceId, payment.getId());
        return PaymentDocument.of(payment, shops.current(), paidSoFar, currency);
    }

    /**
     * {@link #documentFor} of the invoice's last payment.
     *
     * @throws BusinessRuleException when nothing has been paid on the invoice yet
     */
    public PaymentDocument lastDocumentFor(Long invoiceId, UserApp reader) {
        invoices.findByIdFor(invoiceId, reader);
        Long paymentId = payments.findLastIdByInvoiceId(invoiceId);
        if (paymentId == null) {
            throw new BusinessRuleException("NO_PAYMENT", "Cette facture n'a pas encore de paiement.");
        }
        return documentFor(invoiceId, paymentId, reader);
    }

    private PaymentPdf render(PaymentDocument document, String template) {
        return new PaymentPdf(document.number(), pdfs.render(template, "payment", document));
    }

    /** @param number the receipt number, to name the file; @param content the PDF itself */
    public record PaymentPdf(String number, byte[] content) {

        public String fileName() {
            return number + ".pdf";
        }
    }
}