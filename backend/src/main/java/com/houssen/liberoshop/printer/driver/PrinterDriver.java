package com.houssen.liberoshop.printer.driver;



import com.houssen.liberoshop.entity.PayloadKind;
import com.houssen.liberoshop.entity.PrintJob;
import com.houssen.liberoshop.entity.PrinterConfig;
import com.houssen.liberoshop.entity.PrinterType;

import java.util.Set;

/**
 * Port d'impression. Pour supporter un nouveau type d'imprimante (USB, Bluetooth…),
 * il suffit d'ajouter une valeur à {@link PrinterType} et un {@code @Component} qui implémente cette interface.
 */
public interface PrinterDriver {

    PrinterType type();

    Set<PayloadKind> supportedKinds();

    void print(PrinterConfig config, PrintJob job);

    /** Test rapide de disponibilité (utilisé pour afficher « en ligne / hors ligne »). */
    boolean isReachable(PrinterConfig config);
}
