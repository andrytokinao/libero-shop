package com.houssen.liberoshop.entity;

public enum PrinterType {
    /** Imprimante installée dans l'OS (A4, PDF). Utilise le spooler via PDFBox. */
    SYSTEM,
    /** Imprimante ticket vue par l'OS (USB), envoi ESC/POS brut via le spooler. */
    ESCPOS_RAW_SYSTEM,
    /** Imprimante ticket réseau (Ethernet/Wi-Fi), ESC/POS via TCP 9100. Aucun pilote. */
    ESCPOS_NET
}
