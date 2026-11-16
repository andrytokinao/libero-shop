package com.houssen.liberoshop.printer.service;

import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.printer.service.tiket.InvoiceTicketFactory;
import com.houssen.liberoshop.service.InvoicePdfService;
import com.houssen.liberoshop.service.InvoiceService;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Prints an invoice from its id, and marks it printed once the printer has taken it.
 *
 * <p>The invoice is offered as a {@link PrintableDocument}, and {@link PrintService} picks, printer
 * by printer, the form it takes:
 * <ul>
 *   <li>an A4 printer: the A4 PDF, whatever {@code forcePdf} says -- a ticket does not fit it;</li>
 *   <li>a till printer (ESC/POS), {@code forcePdf} false: the ticket written for it by
 *       {@link InvoiceTicketFactory}, sharp and light;</li>
 *   <li>a till printer, {@code forcePdf} true: the ticket-width PDF, drawn as an image -- for when
 *       the PDF layout is the one wanted, or the native ticket does not come out right.</li>
 * </ul>
 * Each form is only built if some printer asks for it.
 *
 * <p>No {@code @Transactional} here, on purpose: the documents are built in the read-only
 * transaction of {@link InvoicePdfService}, which is over when printing starts, so a slow or
 * offline printer holds no database connection. A printing failure leaves the invoice unmarked.
 */
@Service
@RequiredArgsConstructor
public class InvoicePrintService {

    private final InvoicePdfService pdfs;
    private final InvoiceService invoices;
    private final PrintService printService;
    private final InvoiceTicketFactory tickets;

    /**
     * Prints the invoice on the first printer able to take it, as a native ticket on a till printer.
     *
     * @throws org.springframework.security.access.AccessDeniedException when it is not theirs to read
     */
    public InvoiceResponse print(Long invoiceId, UserApp reader) {
        return print(invoiceId, reader, null, false);
    }

    /** Same, on the printer {@code printerId} (null for the first one able to take the invoice). */
    public InvoiceResponse print(Long invoiceId, UserApp reader, String printerId) {
        return print(invoiceId, reader, printerId, false);
    }

    /**
     * @param printerId the printer to use, or null for the first one able to take the invoice
     * @param forcePdf  true to print the PDF (as an image on a till printer) instead of the native
     *                  ticket; false to generate the ticket
     * @throws org.springframework.security.access.AccessDeniedException when it is not theirs to read
     */
    public InvoiceResponse print(Long invoiceId, UserApp reader, String printerId, boolean forcePdf) {
        // Read first: refuses an invoice that is not theirs before any printer is looked at, and
        // gives the number the job is named after.
        InvoiceResponse invoice = invoices.findByIdFor(invoiceId, reader);
        PrintableDocument.Builder document = PrintableDocument.titled("Facture " + invoice.invoiceNumber())
                .a4Pdf(() -> pdfs.pdfFor(invoiceId, reader).content())
                .ticketPdf(() -> pdfs.ticketFor(invoiceId, reader).content());
        if (!forcePdf) {
            document.nativeTicket(printer -> tickets.create(pdfs.documentFor(invoiceId, reader)));
        }
        printService.printDocument(printerId, document.build());
        return invoices.markPrinted(invoiceId);
    }
}