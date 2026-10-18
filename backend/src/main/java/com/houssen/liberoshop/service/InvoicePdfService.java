package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The invoice as a PDF, to print or to send to the customer. Readable by whoever may read
 * the invoice itself, and by nobody else.
 */
@Service
@Transactional(readOnly = true)
public class InvoicePdfService {

    private final InvoiceService invoices;
    private final String shopName;

    /**
     * @param shopName printed at the top of every invoice
     */
    public InvoicePdfService(InvoiceService invoices,
                             @Value("${liberoshop.shop-name:Libero Shop}") String shopName) {
        this.invoices = invoices;
        this.shopName = shopName;
    }

    /** The invoice number, to name the file, and the PDF itself. */
    public InvoicePdf pdfFor(Long invoiceId, UserApp reader) {
        InvoiceResponse invoice = invoices.findByIdFor(invoiceId, reader);
        return new InvoicePdf(invoice.invoiceNumber(), InvoicePdfWriter.write(invoice, shopName));
    }

    /** A PDF and the name it is offered under. */
    public record InvoicePdf(String invoiceNumber, byte[] content) {
    }
}
