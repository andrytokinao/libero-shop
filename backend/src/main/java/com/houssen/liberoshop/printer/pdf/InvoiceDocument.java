package com.houssen.liberoshop.printer.pdf;

import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.ShopInfo;
import com.houssen.liberoshop.util.Money;
import com.houssen.liberoshop.util.Quantities;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import com.houssen.liberoshop.web.dto.SaleLineResponse;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Modèle affiché par le gabarit Thymeleaf {@code templates/pdf/invoice.html}.
 * Tout est déjà formaté (dates, montants, libellés) : le gabarit ne contient aucune logique.
 *
 * @param paid         faux = le statut est affiché en orange (à payer, annulée)
 * @param cancelledOn  null = facture non annulée ; sinon « FACTURE ANNULÉE le ... », en bandeau
 * @param customerName null = pas de ligne client
 * @param sellerName   null = pas de ligne vendeur
 */
public record InvoiceDocument(
        ShopInfo shop,
        String number,
        String date,
        String status,
        boolean paid,
        String cancelledOn,
        String customerName,
        String sellerName,
        List<Line> lines,
        String total) {

    /** @param quantity « 2 carton », « 1,5 kg » : l'unité dans laquelle la ligne a été vendue */
    public record Line(int index, String label, String quantity, String unitPrice, String total) {
    }

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    public static InvoiceDocument of(InvoiceResponse invoice, ShopInfo shop, String currency) {
        List<SaleLineResponse> source = invoice.sale().lines();
        List<Line> lines = IntStream.range(0, source.size())
                .mapToObj(i -> {
                    SaleLineResponse line = source.get(i);
                    return new Line(i + 1, line.product().name(), quantityOf(line),
                            Money.format(line.unitPrice(), currency), Money.format(line.subtotal(), currency));
                })
                .toList();
        PaymentStatus status = invoice.paymentStatus();
        String customer = invoice.clientName();
        return new InvoiceDocument(
                shop,
                invoice.invoiceNumber(),
                WHEN.format(invoice.invoiceDate()),
                statusLabel(status),
                status != PaymentStatus.UNPAID && status != PaymentStatus.CANCELLED,
                invoice.cancellation() == null ? null
                        : "FACTURE ANNULÉE le " + WHEN.format(invoice.cancellation().at()),
                customer == null || customer.isBlank() ? null : customer,
                invoice.sale().seller() == null ? null : invoice.sale().seller().fullName(),
                lines,
                Money.format(invoice.sale().totalAmount(), currency));
    }

    /** « 2 carton », « 1,5 kg » : l'unité de la ligne, ou à défaut celle du produit. */
    private static String quantityOf(SaleLineResponse line) {
        String unit = line.unitLabel() != null ? line.unitLabel() : line.product().unit();
        String quantity = Quantities.format(line.quantity());
        return unit == null || unit.isBlank() ? quantity : quantity + " " + unit;
    }

    /** Ce que le client lit : l'argent encaissé par le dépôt ou remis en caisse est déjà payé. */
    static String statusLabel(PaymentStatus status) {
        return switch (status) {
            case UNPAID -> "À payer";
            case CANCELLED -> "Annulée";
            case PAID, COLLECTED, REMITTED -> "Payée";
        };
    }
}