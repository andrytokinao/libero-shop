package com.houssen.liberoshop.entity;

import java.nio.charset.Charset;

/**
 * Tables de caractères ESC/POS (commande {@code ESC t n}) prises en charge, avec le charset Java correspondant.
 * Le numéro de table et le charset forment un couple : l'un ne va pas sans l'autre.
 */
public enum CodePage {
    PC437(0, "Cp437"),
    PC850(2, "Cp850"),
    WPC1252(16, "windows-1252"),
    /** PC850 + euro : table la plus courante sur les XPrinter et clones. */
    PC858(19, "Cp858");

    private final int table;
    private final String charsetName;

    CodePage(int table, String charsetName) {
        this.table = table;
        this.charsetName = charsetName;
    }

    public int table() {
        return table;
    }

    public boolean isAvailable() {
        return Charset.isSupported(charsetName);
    }

    public Charset charset() {
        return Charset.forName(charsetName);
    }

    public static CodePage fromTable(int table) {
        for (CodePage cp : values()) {
            if (cp.table == table) {
                return cp;
            }
        }
        throw new IllegalArgumentException("Table de caractères non supportée : " + table
                + " (valeurs possibles : 0, 2, 16, 19)");
    }
}
