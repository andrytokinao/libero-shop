package com.houssen.liberoshop.printer.service;

import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.printer.service.tiket.PaymentTicketFactory;
import com.houssen.liberoshop.service.InvoiceService;
import com.houssen.liberoshop.service.PaymentPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Sends the receipt of a payment to a printer, starting from the invoice like the rest of the
 * till does. Like {@link InvoicePrintService}, the receipt is offered as a
 * {@link PrintableDocument}: the A4 PDF for an A4 printer, and for a till printer the native ticket
 * of {@link PaymentTicketFactory} -- or, with {@code forcePdf}, the ticket-width PDF drawn as an
 * image.
 *
 * <p>No {@code @Transactional} here, on purpose: the documents are built in the read-only
 * transaction of {@link PaymentPdfService}, which is over when printing starts, so a slow or
 * offline printer holds no database connection. Call it once the payment is committed -- from the
 * controller, after {@code InvoiceService.pay} / {@code deliver} returned -- never from the
 * transaction that writes it: a printing failure must not undo a collection.
 */
@Service
@RequiredArgsConstructor
public class PaymentPrintService {

    private final PaymentPdfService receipts;
    private final InvoiceService invoices;
    private final PrintService printService;
    private final PaymentTicketFactory tickets;

    /**
     * Prints the receipt of the invoice's last payment.
     *
     * @param printerId the printer to use, or null for the first one able to take it
     * @param forcePdf  true to print the PDF instead of generating the ticket
     * @throws org.springframework.security.access.AccessDeniedException when the invoice is not
     *         theirs to read
     */
    public void printLast(Long invoiceId, UserApp reader, String printerId, boolean forcePdf) {
        PrintableDocument.Builder document = named(invoiceId, reader)
                .a4Pdf(() -> receipts.lastReceiptFor(invoiceId, reader).content())
                .ticketPdf(() -> receipts.lastTicketFor(invoiceId, reader).content());
        if (!forcePdf) {
            document.nativeTicket(printer -> tickets.create(receipts.lastDocumentFor(invoiceId, reader)));
        }
        printService.printDocument(printerId, document.build());
    }

    /** Prints the receipt of one payment of the invoice. */
    public void print(Long invoiceId, Long paymentId, UserApp reader, String printerId, boolean forcePdf) {
        PrintableDocument.Builder document = named(invoiceId, reader)
                .a4Pdf(() -> receipts.receiptFor(invoiceId, paymentId, reader).content())
                .ticketPdf(() -> receipts.ticketFor(invoiceId, paymentId, reader).content());
        if (!forcePdf) {
            document.nativeTicket(printer -> tickets.create(receipts.documentFor(invoiceId, paymentId, reader)));
        }
        printService.printDocument(printerId, document.build());
    }

    /** Read first: refuses an invoice that is not theirs, and names the job after its number. */
    private PrintableDocument.Builder named(Long invoiceId, UserApp reader) {
        return PrintableDocument.titled("Recu " + invoices.findByIdFor(invoiceId, reader).invoiceNumber());
    }
}