package com.houssen.liberoshop.printer.pdf;

import com.houssen.liberoshop.entity.Payment;
import com.houssen.liberoshop.entity.ShopInfo;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * Met en page le reçu d'un paiement : le gabarit {@code templates/pdf/payment-receipt.html}
 * rempli par un {@link PaymentDocument}, puis converti en PDF par OpenHTMLtoPDF.
 *
 * <p>Lecture seule : imprimer un reçu ne change rien au paiement. La transaction reste ouverte
 * le temps de construire le modèle, car la facture, la vente et l'encaisseur sont chargés
 * paresseusement ; le rendu PDF, lui, ne touche plus à la base.
 */
@Service
@RequiredArgsConstructor
public class PaymentPdfService {

    static final String TEMPLATE = "pdf/payment-receipt";

    private final PaymentRepository payments;
    private final TemplateEngine templates;

    /**
     * Le reçu du paiement {@code paymentId}, en PDF.
     *
     * @param shop     nom, adresse et contact de la boutique, tels que ses réglages les donnent
     * @param currency symbole de la monnaie, ex. « Ar »
     * @throws ResourceNotFoundException si le paiement n'existe pas
     */
    @Transactional(readOnly = true)
    public byte[] receipt(Long paymentId, ShopInfo shop, String currency) {
        PaymentDocument document = document(paymentId, shop, currency);
        return toPdf(html(document));
    }

    /** Le nom de fichier proposé au téléchargement, ex. « PAY-000123.pdf ». */
    public static String fileName(Long paymentId) {
        return PaymentDocument.number(paymentId) + ".pdf";
    }

    private PaymentDocument document(Long paymentId, ShopInfo shop, String currency) {
        Payment payment = payments.findDetailedById(paymentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Paiement", paymentId));
        BigDecimal paidSoFar = payments.sumUpTo(payment.getInvoice().getId(), payment.getId());
        return PaymentDocument.of(payment, shop, paidSoFar, currency);
    }

    private String html(PaymentDocument document) {
        Context context = new Context(Locale.FRENCH);
        context.setVariable("payment", document);
        return templates.process(TEMPLATE, context);
    }

    private static byte[] toPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .withHtmlContent(html, null)
                    .toStream(out)
                    .run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Le reçu de paiement n'a pas pu être mis en PDF", e);
        } catch (Exception e) {
            // PdfRendererBuilder.run() déclare Exception : un gabarit mal formé finit ici.
            throw new IllegalStateException("Le gabarit du reçu de paiement est invalide", e);
        }
    }
}