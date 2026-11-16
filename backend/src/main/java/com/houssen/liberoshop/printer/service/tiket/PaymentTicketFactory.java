package com.houssen.liberoshop.printer.service.tiket;

import com.houssen.liberoshop.entity.PrintJob;
import com.houssen.liberoshop.printer.pdf.PaymentDocument;
import com.houssen.liberoshop.printer.service.tiket.EscPosWriter.Align;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Le ticket ESC/POS d'un reçu de paiement, écrit directement pour l'imprimante : même
 * {@link PaymentDocument} que le gabarit PDF, mis en page ici sans passer par une image.
 */
@Component
@RequiredArgsConstructor
public class PaymentTicketFactory {

    private final EscPosWriterFactory writers;

    public PrintJob create(PaymentDocument payment) {
        EscPosWriter ticket = writers.newWriter().shopHeader(payment.shop());

        ticket.align(Align.CENTER).bold(true).line("REÇU " + payment.number()).bold(false)
                .line(payment.date())
                .align(Align.LEFT)
                .line("Facture : " + payment.invoiceNumber())
                .line("Client : " + payment.customerName())
                .rule();

        ticket.align(Align.CENTER).line("MONTANT REÇU")
                .bold(true).tall(true).line(payment.amount()).tall(false).bold(false)
                .align(Align.LEFT)
                .line("Mode : " + payment.method())
                .line("Encaissé par : " + payment.collectedBy());
        if (payment.remittance() != null) {
            ticket.line(payment.remittance());
        }
        ticket.rule()
                .row("Total commande", payment.invoiceTotal())
                .row("Déjà réglé", payment.totalPaid())
                .bold(true).row("Reste à payer", payment.remaining()).bold(false)
                .rule()
                .align(Align.CENTER).line("Merci de votre confiance.")
                .feed(3).cut();
        return PrintJob.escpos("Recu " + payment.number(), ticket.bytes());
    }
}