package com.houssen.liberoshop.printer.service;

import com.houssen.liberoshop.entity.PrintJob;
import com.houssen.liberoshop.entity.PrinterConfig;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Un document à imprimer, sans dire encore sous quelle forme : {@link PrintService} choisit,
 * imprimante par imprimante, celle qu'elle accepte.
 *
 * <p>Trois formes, toutes facultatives -- un document n'est pas forcément un PDF :
 * <ul>
 *   <li>un <b>PDF A4</b>, pour une imprimante {@code SYSTEM} ;</li>
 *   <li>un <b>ticket natif</b> ESC/POS, écrit directement pour l'imprimante : le meilleur rendu,
 *       préféré dès qu'il existe ;</li>
 *   <li>un <b>PDF au format ticket</b>, que {@code PdfTicketPrinter} dessine en image pour une
 *       imprimante ESC/POS qui n'a pas de ticket natif.</li>
 * </ul>
 * Chaque forme est fabriquée à la demande, et une seule fois : un PDF qu'aucune imprimante
 * n'attend n'est jamais produit.
 */
public final class PrintableDocument {

    private final String title;
    private final Lazy<byte[]> a4Pdf;
    private final Lazy<byte[]> ticketPdf;
    private final Function<PrinterConfig, PrintJob> nativeTicket;

    private PrintableDocument(Builder builder) {
        this.title = builder.title;
        this.a4Pdf = builder.a4Pdf == null ? null : new Lazy<>(builder.a4Pdf);
        this.ticketPdf = builder.ticketPdf == null ? null : new Lazy<>(builder.ticketPdf);
        this.nativeTicket = builder.nativeTicket;
    }

    public static Builder titled(String title) {
        return new Builder(title);
    }

    public String title() {
        return title;
    }

    /** Vrai si une imprimante A4 ({@code SYSTEM}) peut l'imprimer. */
    public boolean hasA4() {
        return a4Pdf != null;
    }

    /** Vrai si une imprimante ticket (ESC/POS) peut l'imprimer, en natif ou à partir d'un PDF. */
    public boolean hasTicket() {
        return nativeTicket != null || ticketPdf != null;
    }

    /** Le PDF A4 ; à n'appeler que si {@link #hasA4()}. */
    public byte[] a4Pdf() {
        return a4Pdf.get();
    }

    /** Le PDF au format ticket ; à n'appeler que si {@link #hasTicket()} sans forme native. */
    public byte[] ticketPdf() {
        if (ticketPdf == null) {
            throw new IllegalStateException("« " + title + " » n'a pas de PDF au format ticket");
        }
        return ticketPdf.get();
    }

    /** Le ticket écrit pour cette imprimante, s'il existe. */
    public Optional<PrintJob> nativeTicket(PrinterConfig printer) {
        return nativeTicket == null ? Optional.empty() : Optional.ofNullable(nativeTicket.apply(printer));
    }

    public static final class Builder {

        private final String title;
        private Supplier<byte[]> a4Pdf;
        private Supplier<byte[]> ticketPdf;
        private Function<PrinterConfig, PrintJob> nativeTicket;

        private Builder(String title) {
            this.title = title;
        }

        public Builder a4Pdf(Supplier<byte[]> a4Pdf) {
            this.a4Pdf = a4Pdf;
            return this;
        }

        public Builder ticketPdf(Supplier<byte[]> ticketPdf) {
            this.ticketPdf = ticketPdf;
            return this;
        }

        /** Le ticket ESC/POS écrit pour l'imprimante donnée ; peut répondre null pour « pas celle-là ». */
        public Builder nativeTicket(Function<PrinterConfig, PrintJob> nativeTicket) {
            this.nativeTicket = nativeTicket;
            return this;
        }

        public PrintableDocument build() {
            if (a4Pdf == null && ticketPdf == null && nativeTicket == null) {
                throw new IllegalStateException("« " + title + " » n'a aucune forme imprimable");
            }
            return new PrintableDocument(this);
        }
    }

    /** Une valeur calculée à la première lecture, par un seul fil. */
    private static final class Lazy<T> {

        private final Supplier<T> supplier;
        private T value;
        private boolean computed;

        private Lazy(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        private synchronized T get() {
            if (!computed) {
                value = supplier.get();
                computed = true;
            }
            return value;
        }
    }
}