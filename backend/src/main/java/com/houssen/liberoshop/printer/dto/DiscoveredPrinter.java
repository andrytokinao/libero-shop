package com.houssen.liberoshop.printer.dto;

import com.houssen.liberoshop.entity.PrinterType;

/**
 * Imprimante détectée, proposée à l'utilisateur pour sélection.
 *
 * @param suggestedType type de driver conseillé (ticket thermique ou A4)
 * @param osDefault     imprimante par défaut de l'OS
 * @param registered    déjà ajoutée à l'application
 */
public record DiscoveredPrinter(
        String name,
        DiscoverySource source,
        String address,
        Integer port,
        PrinterType suggestedType,
        boolean osDefault,
        boolean registered) {
}
