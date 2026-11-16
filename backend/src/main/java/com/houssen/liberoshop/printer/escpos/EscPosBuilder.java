package com.houssen.liberoshop.printer.escpos;

import com.houssen.liberoshop.entity.CodePage;
import com.houssen.liberoshop.entity.EscPosProfile;
import com.houssen.liberoshop.entity.PrinterConfig;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Construit les octets ESC/POS d'un ticket (XPrinter, Epson TM, Star en mode ESC/POS…).
 * Le builder ne connaît ni le transport (réseau, USB) ni le driver : il produit seulement des octets.
 */
public class EscPosBuilder {

    public enum Align { LEFT, CENTER, RIGHT }

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final int width;
    private final boolean cutter;
    private final boolean cancelChineseMode;
    private CodePage codePage;
    private Charset charset;

    /** Réglages issus de l'imprimante choisie (profil, largeur, table de caractères). */
    public static EscPosBuilder forPrinter(PrinterConfig cfg) {
        EscPosProfile profile = cfg.getProfile();
        return new EscPosBuilder(cfg.getWidthChars(), cfg.effectiveCodePage(),
                profile.hasCutter(), profile.cancelChineseMode());
    }

    /** Imprimante 80 mm générique (table Windows-1252, avec coupeur). */
    public EscPosBuilder(int widthChars) {
        this(widthChars, CodePage.WPC1252, true, false);
    }

    public EscPosBuilder(int widthChars, CodePage codePage, boolean cutter, boolean cancelChineseMode) {
        this.width = widthChars;
        this.cutter = cutter;
        this.cancelChineseMode = cancelChineseMode;
        select(codePage);
    }

    /** Repli sur Windows-1252 si la JVM n'a pas le charset demandé (runtime minimal sans jdk.charsets). */
    private void select(CodePage requested) {
        this.codePage = requested.isAvailable() ? requested : CodePage.WPC1252;
        this.charset = this.codePage.charset();
    }

    public EscPosBuilder init() {
        raw(0x1B, 0x40);                       // ESC @ : réinitialise l'imprimante
        if (cancelChineseMode) {
            raw(0x1C, 0x2E);                   // FS . : quitte le mode caractères chinois
        }
        return raw(0x1B, 0x74, codePage.table()); // ESC t n : table de caractères
    }

    /** Change de table de caractères en cours de ticket (utilisé par la page de calibration). */
    public EscPosBuilder useCodePage(CodePage cp) {
        select(cp);
        return raw(0x1B, 0x74, codePage.table());
    }

    public EscPosBuilder align(Align align) {
        return raw(0x1B, 0x61, align.ordinal());
    }

    public EscPosBuilder bold(boolean on) {
        return raw(0x1B, 0x45, on ? 1 : 0);
    }

    /** Taille du texte : largeur et hauteur de 1 à 8. */
    public EscPosBuilder size(int w, int h) {
        int n = ((Math.max(1, Math.min(8, w)) - 1) << 4) | (Math.max(1, Math.min(8, h)) - 1);
        return raw(0x1D, 0x21, n);
    }

    public EscPosBuilder text(String text) {
        out.writeBytes(text.getBytes(charset));
        return this;
    }

    public EscPosBuilder line(String text) {
        return text(text).raw(0x0A);
    }

    /** Ligne « libellé ........ valeur » alignée sur la largeur du ticket. */
    public EscPosBuilder row(String left, String right) {
        int room = width - right.length() - 1;
        String l = left.length() > room ? left.substring(0, Math.max(0, room)) : left;
        int spaces = Math.max(1, width - l.length() - right.length());
        return line(l + " ".repeat(spaces) + right);
    }

    public EscPosBuilder separator() {
        return line("-".repeat(width));
    }

    public EscPosBuilder feed(int lines) {
        return raw(0x1B, 0x64, lines);
    }

    /** Coupe le papier ; sans coupeur, avance simplement le papier pour pouvoir le déchirer. */
    public EscPosBuilder cut() {
        return cutter ? feed(3).raw(0x1D, 0x56, 0x42, 0x00) : feed(5);
    }

    /** Ouvre le tiroir-caisse relié à l'imprimante. */
    public EscPosBuilder drawer() {
        return raw(0x1B, 0x70, 0x00, 0x19, 0xFA);
    }

    private EscPosBuilder raw(int... bytes) {
        for (int b : bytes) {
            out.write(b);
        }
        return this;
    }

    public byte[] build() {
        return out.toByteArray();
    }
}
