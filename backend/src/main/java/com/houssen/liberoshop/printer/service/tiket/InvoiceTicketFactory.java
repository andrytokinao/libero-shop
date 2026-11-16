package com.houssen.liberoshop.printer.service.tiket;

import com.houssen.liberoshop.entity.PrintJob;
import com.houssen.liberoshop.printer.pdf.InvoiceDocument;
import com.houssen.liberoshop.printer.service.tiket.EscPosWriter.Align;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Le ticket ESC/POS d'une facture : écrit directement pour l'imprimante, sans passer par un PDF,
 * donc net, rapide et léger -- ce que {@code PdfTicketPrinter} ne peut qu'approcher avec une image.
 *
 * <p>Il met en page le même {@link InvoiceDocument} que le gabarit PDF : textes et montants sont
 * déjà formatés, il ne fait que les disposer.
 */
@Component
@RequiredArgsConstructor
public class InvoiceTicketFactory {

    private final EscPosWriterFactory writers;

    public PrintJob create(InvoiceDocument invoice) {
        EscPosWriter ticket = writers.newWriter().shopHeader(invoice.shop());

        ticket.align(Align.CENTER).bold(true).line("FACTURE " + invoice.number()).bold(false)
                .line(invoice.date());
        if (invoice.cancelledOn() != null) {
            ticket.bold(true).line(invoice.cancelledOn()).bold(false);
        }
        ticket.align(Align.LEFT);
        if (invoice.customerName() != null) {
            ticket.line("Client : " + invoice.customerName());
        }
        if (invoice.sellerName() != null) {
            ticket.line("Vendeur : " + invoice.sellerName());
        }
        ticket.rule();

        for (InvoiceDocument.Line line : invoice.lines()) {
            ticket.bold(true).line(line.label()).bold(false)
                    .row(line.quantity() + " x " + line.unitPrice(), line.total());
        }
        ticket.rule();

        ticket.bold(true).tall(true).row("TOTAL", invoice.total()).tall(false)
                .align(Align.CENTER).line(invoice.status()).bold(false)
                .rule()
                .line("Merci de votre visite.")
                .feed(3).cut();
        return PrintJob.escpos("Facture " + invoice.number(), ticket.bytes());
    }
}