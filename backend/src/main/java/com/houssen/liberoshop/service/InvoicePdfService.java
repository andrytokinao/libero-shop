package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The invoice as a PDF, to print or to send to the customer. Readable by whoever may read
 * the invoice itself, and by nobody else.
 *
 * <p>Laid out by the Thymeleaf templates {@code templates/pdf/invoice.html} (A4) and
 * {@code invoice-ticket.html} (a till roll, as wide as its paper), filled with an
 * {@link InvoiceDocument}; the shop's identity is {@link ShopInfoService}'s. The same
 * {@link InvoiceDocument} is what the native ESC/POS ticket is written from, see
 * {@link #documentFor}.
 */
@Service
@Transactional(readOnly = true)
public class InvoicePdfService {

    static final String TEMPLATE = "pdf/invoice";
    static final String TICKET_TEMPLATE = "pdf/invoice-ticket";

    private final InvoiceService invoices;
    private final ShopInfoService shops;
    private final HtmlPdfRenderer pdfs;
    private final String currency;

    /**
     * @param shops    the shop's name, address and contact, printed at the top of every invoice
     * @param currency symbol written after every amount
     */
    public InvoicePdfService(InvoiceService invoices,
                             ShopInfoService shops,
                             HtmlPdfRenderer pdfs,
                             @Value("${liberoshop.currency:Ar}") String currency) {
        this.invoices = invoices;
        this.shops = shops;
        this.pdfs = pdfs;
        this.currency = currency;
    }

    /** The invoice number, to name the file, and the A4 PDF itself. */
    public InvoicePdf pdfFor(Long invoiceId, UserApp reader) {
        return render(documentFor(invoiceId, reader), TEMPLATE);
    }

    /** The same invoice laid out for a till roll: what {@code PdfTicketPrinter} turns into a ticket. */
    public InvoicePdf ticketFor(Long invoiceId, UserApp reader) {
        return render(documentFor(invoiceId, reader), TICKET_TEMPLATE);
    }

    /**
     * What the invoice says, already formatted, for whoever lays it out: the PDF templates, or the
     * native ticket.
     *
     * @throws org.springframework.security.access.AccessDeniedException when it is not theirs to read
     */
    public InvoiceDocument documentFor(Long invoiceId, UserApp reader) {
        InvoiceResponse invoice = invoices.findByIdFor(invoiceId, reader);
        return InvoiceDocument.of(invoice, shops.current(), currency);
    }

    private InvoicePdf render(InvoiceDocument document, String template) {
        return new InvoicePdf(document.number(), pdfs.render(template, "invoice", document));
    }

    /** A PDF and the name it is offered under. */
    public record InvoicePdf(String invoiceNumber, byte[] content) {
    }
}