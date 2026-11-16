package com.houssen.liberoshop.printer.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import java.util.List;
import java.util.Optional;

/** Accès aux imprimantes déjà déclarées dans l'OS (Windows, CUPS sous Linux/macOS). */
public final class SystemPrinters {

    private static final Logger log = LoggerFactory.getLogger(SystemPrinters.class);

    private SystemPrinters() {
    }

    public static List<PrintService> all() {
        try {
            return List.of(PrintServiceLookup.lookupPrintServices(null, null));
        } catch (RuntimeException | LinkageError e) {
            log.warn("Impossible de lister les imprimantes système : {}", e.toString());
            return List.of();
        }
    }

    public static Optional<PrintService> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return all().stream().filter(p -> p.getName().equalsIgnoreCase(name)).findFirst();
    }

    public static Optional<PrintService> defaultService() {
        try {
            return Optional.ofNullable(PrintServiceLookup.lookupDefaultPrintService());
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    public static boolean isAccepting(PrintService service) {
        PrinterIsAcceptingJobs accepting = service.getAttribute(PrinterIsAcceptingJobs.class);
        return accepting == null || accepting == PrinterIsAcceptingJobs.ACCEPTING_JOBS;
    }
}
