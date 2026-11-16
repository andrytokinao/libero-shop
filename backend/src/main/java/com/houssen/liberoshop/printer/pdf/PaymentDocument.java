package com.houssen.liberoshop.printer.pdf;

import com.houssen.liberoshop.entity.CashRemittance;
import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.ShopInfo;
import com.houssen.liberoshop.util.Money;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Modèle affiché par le gabarit Thymeleaf {@code templates/pdf/payment-receipt.html}.
 * Tout est déjà formaté (dates, montants, libellés) : le gabarit ne contient aucune logique.
 *
 * @param customerPhone null = ligne non affichée (la facture ne porte pas de téléphone)
 * @param remittance    null = paiement pas encore remis en caisse (ligne non affichée)
 * @param note          null = pas de note
 */
public record PaymentDocument(
        ShopInfo shop,
        String number,
        String date,
        String invoiceNumber,
        String invoiceDate,
        String invoiceStatus,
        String customerName,
        String customerPhone,
        String amount,
        String method,
        String collectedBy,
        String remittance,
        String invoiceTotal,
        String totalPaid,
        String remaining,
        String note) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * @param payment   le paiement, avec sa facture, sa vente, son encaisseur et sa remise chargés
     * @param paidSoFar tout ce qui a été réglé sur la commande, ce paiement compris, au moment
     *                  où il a été fait : un ancien reçu garde le reste à payer de son jour
     */
    public static PaymentDocument of(Payment payment, ShopInfo shop, BigDecimal paidSoFar, String currency) {
        Invoice invoice = payment.getInvoice();
        BigDecimal total = invoice.getSale().getTotalAmount();
        BigDecimal remaining = total.subtract(paidSoFar).max(BigDecimal.ZERO);
        CashRemittance remittance = payment.getCashRemittance();
        return new PaymentDocument(
                shop,
                number(payment.getId()),
                payment.getPaymentDate().format(DATE),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate().format(DATE),
                statusLabel(invoice.getPaymentStatus()),
                invoice.getClientName(),
                null,
                Money.format(payment.getAmount(), currency),
                methodLabel(payment.getPaymentMethod()),
                payment.getCollectedBy().getFullName(),
                remittance == null ? null : "Remise n° " + remittance.getId(),
                Money.format(total, currency),
                Money.format(paidSoFar, currency),
                Money.format(remaining, currency),
                null);
    }

    /** « PAY-000123 » : le numéro du reçu, dérivé de l'identifiant du paiement. */
    public static String number(Long paymentId) {
        return "PAY-" + String.format("%06d", paymentId);
    }

    static String methodLabel(PaymentMethod method) {
        return switch (method) {
            case CASH -> "Espèces";
            case MOBILE_MONEY -> "Mobile money";
            case BANK_TRANSFER -> "Virement bancaire";
            case OTHER -> "Autre";
        };
    }

    static String statusLabel(PaymentStatus status) {
        return switch (status) {
            case UNPAID -> "À payer";
            case CANCELLED -> "Annulée";
            case COLLECTED -> "Encaissée";
            case REMITTED -> "Remise en caisse";
            case PAID -> "Payée";
        };
    }
}