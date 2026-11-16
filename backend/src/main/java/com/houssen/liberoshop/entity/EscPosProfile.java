package com.houssen.liberoshop.entity;

/**
 * Profil d'imprimante ESC/POS : valeurs par défaut propres à une famille de matériel.
 * Les drivers n'en dépendent pas : ils ne font que transporter les octets.
 * Chaque valeur reste modifiable imprimante par imprimante (largeur, table de caractères).
 */
public enum EscPosProfile {

    /** Valeurs prudentes pour une imprimante 80 mm inconnue. */
    GENERIC(42, CodePage.WPC1252, true, false),
    EPSON_80(48, CodePage.WPC1252, true, false),
    /** XPrinter 80 mm (XP-80, XP-N160II, XP-Q800…). */
    XPRINTER_80(48, CodePage.PC858, true, true),
    /** XPrinter 58 mm (XP-58, XP-58IIH…) : souvent sans coupeur. */
    XPRINTER_58(32, CodePage.PC858, false, true);

    private final int defaultWidth;
    private final CodePage codePage;
    private final boolean cutter;
    private final boolean cancelChineseMode;

    EscPosProfile(int defaultWidth, CodePage codePage, boolean cutter, boolean cancelChineseMode) {
        this.defaultWidth = defaultWidth;
        this.codePage = codePage;
        this.cutter = cutter;
        this.cancelChineseMode = cancelChineseMode;
    }

    /** Nombre de caractères par ligne (police A). */
    public int defaultWidth() {
        return defaultWidth;
    }

    public CodePage codePage() {
        return codePage;
    }

    /** Faux : l'imprimante n'a pas de coupeur, on avance le papier pour pouvoir le déchirer. */
    public boolean hasCutter() {
        return cutter;
    }

    /**
     * Beaucoup d'imprimantes fabriquées pour le marché chinois démarrent en mode caractères chinois (GBK),
     * qui déforme les accents. La commande {@code FS .} désactive ce mode ; les autres modèles l'ignorent.
     */
    public boolean cancelChineseMode() {
        return cancelChineseMode;
    }
}
