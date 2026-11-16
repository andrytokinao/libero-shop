package com.houssen.liberoshop.printer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Au démarrage : détecte les imprimantes et les enregistre. Réglages :
 * {@code liberoshop.printers.auto-register} (défaut true), {@code auto-register-network} (défaut false, scan plus long),
 * {@code auto-assign} (défaut true).
 */
@Component
@ConditionalOnProperty(name = "liberoshop.printers.auto-register", havingValue = "true", matchIfMissing = true)
public class PrinterAutoRegistrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PrinterAutoRegistrationRunner.class);

    private final PrinterAutoRegistrationService service;
    private final boolean network;
    private final boolean autoAssign;

    public PrinterAutoRegistrationRunner(PrinterAutoRegistrationService service,
                                         @Value("${liberoshop.printers.auto-register-network:false}") boolean network,
                                         @Value("${liberoshop.printers.auto-assign:true}") boolean autoAssign) {
        this.service = service;
        this.network = network;
        this.autoAssign = autoAssign;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Une détection qui échoue (pas de CUPS, réseau coupé…) ne doit jamais empêcher l'application de démarrer.
        try {
            service.sync(network, null);
            if (autoAssign) {
                service.assignFreeUsages();
            }
        } catch (RuntimeException e) {
            log.warn("Détection automatique des imprimantes impossible : {}", e.toString());
        }
    }
}
