package com.houssen.liberoshop.printer.escpos;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/** Construit les octets ESC/POS d'un ticket (compatible Epson TM, Xprinter, Star en mode ESC/POS…). */
public class EscPosBuilder {

    public enum Align { LEFT, CENTER, RIGHT }

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final Charset charset;
    private final int codePage;
    private final int width;

    public EscPosBuilder(int widthChars) {
        this(widthChars, Charset.forName("windows-1252"), 16);
    }

    /** @param codePage numéro de table de caractères de l'imprimante (16 = WPC1252 sur la plupart des modèles) */
    public EscPosBuilder(int widthChars, Charset charset, int codePage) {
        this.width = widthChars;
        this.charset = charset;
        this.codePage = codePage;
    }

    public EscPosBuilder init() {
        return raw(0x1B, 0x40).raw(0x1B, 0x74, codePage);
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

    /** Avance le papier puis coupe (coupe partielle). */
    public EscPosBuilder cut() {
        return feed(3).raw(0x1D, 0x56, 0x42, 0x00);
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
