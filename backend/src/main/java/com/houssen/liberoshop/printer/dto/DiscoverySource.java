package com.houssen.liberoshop.printer.dto;

public enum DiscoverySource {
    /** Imprimante déjà installée dans l'OS. */
    SYSTEM,
    /** Imprimante trouvée sur le réseau local (port 9100 ouvert). */
    NETWORK
}
