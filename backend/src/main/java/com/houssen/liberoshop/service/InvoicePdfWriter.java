package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.util.Quantities;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import com.houssen.liberoshop.web.dto.SaleLineResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Lays an invoice out on A4 pages: the shop, the number and date, who bought and who sold,
 * one row per line, the total and whether it is paid.
 *
 * <p>Uses the fonts every PDF reader carries (Helvetica), so the file stays small and opens
 * anywhere, including on a phone. Those fonts know the French accents but not every
 * character a product name may hold: anything they cannot draw is written as "?" rather
 * than making the whole invoice fail.
 */
final class InvoicePdfWriter {

    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float MARGIN = 40;
    private static final float ROW = 16;
    private static final float RIGHT = PAGE.getWidth() - MARGIN;
    private static final float QUANTITY_RIGHT = 350;
    private static final float PRICE_RIGHT = 460;
    private static final float NAME_WIDTH = 250;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private PDDocument document;
    private PDPageContentStream page;
    private float y;

    /** The invoice as a PDF file. */
    static byte[] write(InvoiceResponse invoice, String shopName) {
        return new InvoicePdfWriter().render(invoice, shopName);
    }

    private byte[] render(InvoiceResponse invoice, String shopName) {
        try (PDDocument pdf = new PDDocument()) {
            document = pdf;
            newPage();
            header(invoice, shopName);
            tableHead();
            for (SaleLineResponse line : invoice.sale().lines()) {
                if (y < MARGIN + 4 * ROW) {
                    newPage();
                    tableHead();
                }
                row(line);
            }
            footer(invoice);
            page.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("La facture n'a pas pu etre mise en PDF", e);
        }
    }

    private void newPage() throws IOException {
        if (page != null) {
            page.close();
        }
        PDPage next = new PDPage(PAGE);
        document.addPage(next);
        page = new PDPageContentStream(document, next);
        y = PAGE.getHeight() - MARGIN;
    }

    private void header(InvoiceResponse invoice, String shopName) throws IOException {
        text(bold, 18, MARGIN, shopName);
        y -= 28;
        text(bold, 13, MARGIN, "Facture " + invoice.invoiceNumber());
        textRight(regular, 10, RIGHT, WHEN.format(invoice.invoiceDate()));
        y -= ROW + 4;
        if (invoice.clientName() != null && !invoice.clientName().isBlank()) {
            text(regular, 10, MARGIN, "Client : " + invoice.clientName());
            y -= ROW;
        }
        if (invoice.sale().seller() != null) {
            text(regular, 10, MARGIN, "Vendeur : " + invoice.sale().seller().fullName());
            y -= ROW;
        }
        if (invoice.cancellation() != null) {
            text(bold, 11, MARGIN, "FACTURE ANNULÉE le " + WHEN.format(invoice.cancellation().at()));
            y -= ROW;
        }
        y -= 10;
    }

    private void tableHead() throws IOException {
        text(bold, 10, MARGIN, "Produit");
        textRight(bold, 10, QUANTITY_RIGHT, "Quantité");
        textRight(bold, 10, PRICE_RIGHT, "Prix unitaire");
        textRight(bold, 10, RIGHT, "Total");
        y -= 6;
        rule();
        y -= ROW;
    }

    private void row(SaleLineResponse line) throws IOException {
        text(regular, 10, MARGIN, fitted(line.product().name(), 10, NAME_WIDTH));
        textRight(regular, 10, QUANTITY_RIGHT, quantityOf(line));
        textRight(regular, 10, PRICE_RIGHT, amount(line.unitPrice()));
        textRight(regular, 10, RIGHT, amount(line.subtotal()));
        y -= ROW;
    }

    private void footer(InvoiceResponse invoice) throws IOException {
        y += ROW - 6;
        rule();
        y -= ROW + 2;
        textRight(bold, 12, PRICE_RIGHT, "Total");
        textRight(bold, 12, RIGHT, amount(invoice.sale().totalAmount()));
        y -= ROW + 4;
        textRight(regular, 10, RIGHT, statusOf(invoice.paymentStatus()));
        y -= 2 * ROW;
        text(regular, 10, MARGIN, "Merci de votre visite.");
    }

    /** "2 carton", "1,5 kg": the unit the line was sold in, or the product's own. */
    private static String quantityOf(SaleLineResponse line) {
        String unit = line.unitLabel() != null ? line.unitLabel() : line.product().unit();
        String quantity = Quantities.format(line.quantity());
        return unit == null || unit.isBlank() ? quantity : quantity + " " + unit;
    }

    static String statusOf(PaymentStatus status) {
        return switch (status) {
            case UNPAID -> "À payer";
            case CANCELLED -> "Annulée";
            case PAID, COLLECTED, REMITTED -> "Payée";
        };
    }

    /** "12 500 Ar", "1 250,50 Ar": spaces between thousands, cents only when there are some. */
    static String amount(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRENCH);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        BigDecimal cents = value.setScale(2, RoundingMode.HALF_UP);
        String pattern = cents.stripTrailingZeros().scale() > 0 ? "#,##0.00" : "#,##0";
        return new DecimalFormat(pattern, symbols).format(cents) + " Ar";
    }

    private void text(PDFont font, float size, float x, String value) throws IOException {
        page.beginText();
        page.setFont(font, size);
        page.newLineAtOffset(x, y);
        page.showText(drawable(font, value));
        page.endText();
    }

    private void textRight(PDFont font, float size, float right, String value) throws IOException {
        String shown = drawable(font, value);
        text(font, size, right - width(font, size, shown), shown);
    }

    private void rule() throws IOException {
        page.moveTo(MARGIN, y);
        page.lineTo(RIGHT, y);
        page.stroke();
    }

    /** Cut with an ellipsis when it would run into the next column. */
    private String fitted(String value, float size, float maxWidth) throws IOException {
        String shown = drawable(regular, value);
        if (width(regular, size, shown) <= maxWidth) {
            return shown;
        }
        while (!shown.isEmpty() && width(regular, size, shown + "...") > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown + "...";
    }

    private static float width(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(value) / 1000 * size;
    }

    /** What the font can draw: each character it does not know becomes "?". */
    static String drawable(PDFont font, String value) {
        if (value == null) {
            return "";
        }
        StringBuilder shown = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            String character = Character.toString(Character.isWhitespace(codePoint) ? ' ' : codePoint);
            try {
                font.encode(character);
                shown.append(character);
            } catch (IOException | IllegalArgumentException e) {
                shown.append('?');
            }
        });
        return shown.toString();
    }
}
