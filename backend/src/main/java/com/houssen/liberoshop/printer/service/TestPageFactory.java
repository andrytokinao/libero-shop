package com.houssen.liberoshop.printer.service;

import com.houssen.liberoshop.entity.CodePage;
import com.houssen.liberoshop.entity.PrintJob;
import com.houssen.liberoshop.entity.PrinterConfig;
import com.houssen.liberoshop.entity.PrinterType;
import com.houssen.liberoshop.printer.escpos.EscPosBuilder;
import com.houssen.liberoshop.printer.escpos.EscPosBuilder.Align;
import com.houssen.liberoshop.printer.exception.PrintException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Génère les pages de test adaptées au type d'imprimante (ticket ESC/POS ou page A4 PDF). */
final class TestPageFactory {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ACCENTS = "é è à ç ù ô ê ï ñ";

    private TestPageFactory() {
    }

    static PrintJob create(PrinterConfig cfg) {
        return cfg.getType() == PrinterType.SYSTEM ? pdf(cfg) : escpos(cfg);
    }

    private static PrintJob escpos(PrinterConfig cfg) {
        byte[] data = EscPosBuilder.forPrinter(cfg)
                .init()
                .align(Align.CENTER).bold(true).size(2, 2).line("TEST").size(1, 1).bold(false)
                .line(cfg.getName())
                .separator()
                .align(Align.LEFT)
                .row("Profil", cfg.getProfile().name())
                .row("Largeur", cfg.getWidthChars() + " car.")
                .row("Table", "ESC t " + cfg.effectiveCodePage().table())
                .row("Date", LocalDateTime.now().format(DATE))
                .separator()
                .align(Align.CENTER)
                .line(ACCENTS)
                .cut()
                .build();
        return PrintJob.escpos("Test - " + cfg.getName(), data);
    }

    /**
     * Imprime les mêmes accents dans chaque table de caractères supportée.
     * La ligne lisible donne la valeur de {@code codePage} à enregistrer pour cette imprimante.
     */
    static PrintJob codePages(PrinterConfig cfg) {
        EscPosBuilder b = EscPosBuilder.forPrinter(cfg).init()
                .align(Align.CENTER).bold(true).line("TABLES DE CARACTERES").bold(false)
                .align(Align.LEFT)
                .line("Retenez le numero de la ligne")
                .line("dont les accents sont corrects.")
                .separator();
        for (CodePage cp : CodePage.values()) {
            b.useCodePage(cp).line("[" + cp.table() + "] " + cp.name());
            b.line("  " + ACCENTS);
        }
        byte[] data = b.separator().cut().build();
        return PrintJob.escpos("Tables - " + cfg.getName(), data);
    }

    private static PrintJob pdf(PrinterConfig cfg) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.addRect(40, 40, 515, 762);
                cs.stroke();
                write(cs, bold, 22, 60, 760, "Page de test d'impression");
                write(cs, regular, 12, 60, 725, "Imprimante : " + safe(cfg.getName()));
                write(cs, regular, 12, 60, 705, "Type : " + cfg.getType());
                write(cs, regular, 12, 60, 685, "Date : " + LocalDateTime.now().format(DATE));
                write(cs, regular, 12, 60, 650, "Si ce cadre est entièrement visible, la mise à l'échelle est correcte.");
            }
            doc.save(out);
            return PrintJob.pdf("Test - " + cfg.getName(), out.toByteArray());
        } catch (IOException e) {
            throw new PrintException("Impossible de générer la page de test", e);
        }
    }

    private static void write(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(text));
        cs.endText();
    }

    /** Ne garde que les caractères encodables en WinAnsi (polices PDF standard). */
    private static String safe(String text) {
        return text.replaceAll("[^\\x20-\\x7E\\u00A0-\\u00FF]", "?");
    }
}
