package com.houssen.liberoshop.printer.service.tiket;

import com.houssen.liberoshop.printer.exception.PrintException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Met un PDF au format qu'une imprimante ticket ESC/POS comprend, puisqu'elle ne lit pas le PDF :
 * chaque page est dessinée en image, passée en noir et blanc, puis écrite en trame ESC/POS
 * ({@code GS v 0}), suivie d'un avance-papier et d'une coupe.
 *
 * <p>Un simple convertisseur : il ne connaît ni les imprimantes ni {@code PrintService}, qui
 * l'appelle -- c'est ce qui évite que les deux se demandent l'un l'autre. Le PDF doit être fait
 * pour le papier du ticket (gabarits {@code *-ticket.html}) : sa largeur de page devient la
 * largeur imprimable, et un A4 réduit à 72 mm serait illisible.
 *
 * <p>La page du ticket est haute exprès (600 mm) : le blanc sous le contenu est coupé avant
 * l'envoi, de sorte que le ticket a la hauteur de ce qu'il contient. Un contenu plus long
 * déborde sur une seconde page, imprimée à la suite.
 */
@Component
public class PdfTicketPrinter {

    /** Sous ce niveau de gris (0 noir, 255 blanc), le point est imprimé. */
    private static final int BLACK_BELOW = 170;
    /** Lignes de points envoyées par commande {@code GS v 0} : les petits tampons n'en avalent pas plus. */
    private static final int BAND_ROWS = 128;
    /** Blanc gardé sous le dernier point de chaque page. */
    private static final int BOTTOM_MARGIN_ROWS = 24;

    private static final byte[] INIT = {0x1B, 0x40};
    /** Avance le papier jusqu'au couteau, puis coupe (coupe partielle). */
    private static final byte[] FEED_AND_CUT = {0x1D, 0x56, 0x42, 0x00};

    private final int widthDots;

    /**
     * @param widthDots largeur imprimable en points : 576 pour du papier 80 mm, 384 pour du 58 mm
     *                  (203 dpi). Arrondie au multiple de 8 inférieur.
     */
    public PdfTicketPrinter(@Value("${liberoshop.printer.ticket-width-dots:576}") int widthDots) {
        this.widthDots = widthDots / 8 * 8;
    }

    /**
     * Le PDF en commandes ESC/POS : initialisation, une image par page, avance et coupe.
     *
     * @throws PrintException si le PDF est illisible
     */
    public byte[] toEscPos(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(INIT);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDPage page = document.getPage(i);
                // La résolution qui donne exactement la largeur du papier : 72 points par pouce de page.
                float dpi = widthDots / (page.getMediaBox().getWidth() / 72f);
                BufferedImage gray = renderer.renderImageWithDPI(i, dpi, ImageType.GRAY);
                writeRaster(out, gray, usedRows(gray));
            }
            out.writeBytes(FEED_AND_CUT);
            return out.toByteArray();
        } catch (IOException e) {
            throw new PrintException("Le PDF n'a pas pu être converti en ticket : " + e.getMessage(), e);
        }
    }

    /** Hauteur utile de la page : jusqu'au dernier point noir, plus une marge ; 0 pour une page blanche. */
    private int usedRows(BufferedImage image) {
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isBlack(image, x, y)) {
                    return Math.min(image.getHeight(), y + 1 + BOTTOM_MARGIN_ROWS);
                }
            }
        }
        return 0;
    }

    /** {@code GS v 0 m xL xH yL yH data} par bandes : 8 points par octet, le plus à gauche en bit de poids fort. */
    private void writeRaster(ByteArrayOutputStream out, BufferedImage image, int rows) {
        int bytesPerRow = widthDots / 8;
        for (int top = 0; top < rows; top += BAND_ROWS) {
            int height = Math.min(BAND_ROWS, rows - top);
            out.writeBytes(new byte[]{0x1D, 0x76, 0x30, 0x00,
                    (byte) (bytesPerRow & 0xFF), (byte) (bytesPerRow >> 8),
                    (byte) (height & 0xFF), (byte) (height >> 8)});
            for (int y = top; y < top + height; y++) {
                for (int byteIndex = 0; byteIndex < bytesPerRow; byteIndex++) {
                    int value = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = byteIndex * 8 + bit;
                        if (x < image.getWidth() && isBlack(image, x, y)) {
                            value |= 0x80 >> bit;
                        }
                    }
                    out.write(value);
                }
            }
        }
    }

    private static boolean isBlack(BufferedImage image, int x, int y) {
        return image.getRaster().getSample(x, y, 0) < BLACK_BELOW;
    }
}