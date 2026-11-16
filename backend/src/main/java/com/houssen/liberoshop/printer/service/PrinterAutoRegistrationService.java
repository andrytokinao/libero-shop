package com.houssen.liberoshop.printer.service;

import com.houssen.liberoshop.entity.PrinterConfig;
import com.houssen.liberoshop.entity.PrinterType;
import com.houssen.liberoshop.entity.PrinterUsage;
import com.houssen.liberoshop.printer.driver.SystemPrinters;
import com.houssen.liberoshop.printer.dto.DiscoveredPrinter;
import com.houssen.liberoshop.printer.dto.PrinterView;
import com.houssen.liberoshop.printer.dto.RegisterPrinterRequest;
import com.houssen.liberoshop.repository.PrinterConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enregistre automatiquement en base les imprimantes détectées (OS et, sur demande, réseau local),
 * puis affecte les usages encore libres : plus aucun ajout manuel nécessaire.
 * Les imprimantes déjà connues, ou affectées par l'utilisateur, ne sont jamais modifiées.
 */
@Service
public class PrinterAutoRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(PrinterAutoRegistrationService.class);

    private final PrinterDiscoveryService discovery;
    private final PrinterManagementService management;
    private final PrinterConfigRepository printers;

    public PrinterAutoRegistrationService(PrinterDiscoveryService discovery,
                                          PrinterManagementService management,
                                          PrinterConfigRepository printers) {
        this.discovery = discovery;
        this.management = management;
        this.printers = printers;
    }

    /**
     * Détecte et enregistre les imprimantes pas encore connues.
     *
     * @param network inclure le scan du réseau local (port 9100), plus lent
     * @param subnet  ex. {@code 192.168.1} ; null = réseaux de la machine
     * @return les imprimantes nouvellement ajoutées
     */
    public synchronized List<PrinterView> sync(boolean network, String subnet) {
        List<DiscoveredPrinter> found = new ArrayList<>(discovery.discoverSystem());
        if (network) {
            found.addAll(discovery.discoverNetwork(subnet));
        }
        List<RegisterPrinterRequest> requests = found.stream()
                .filter(d -> !d.registered())
                .map(d -> new RegisterPrinterRequest(d.name(), d.suggestedType(), d.address(),
                        d.port(), null, d.suggestedProfile(), null))
                .toList();
        List<PrinterView> added = requests.isEmpty() ? List.of() : management.registerAll(requests);
        log.info("Imprimantes détectées : {}, ajoutées : {}", found.size(), added.size());
        return added;
    }

    /**
     * Affecte les usages sans imprimante : INVOICE et REPORT à l'imprimante A4 par défaut de l'OS (sinon la première),
     * TICKET à la première imprimante ticket. Un usage déjà affecté n'est pas touché.
     */
    public void assignFreeUsages() {
        Map<PrinterUsage, String> assigned = management.assignments();
        List<PrinterConfig> all = printers.findAll(Sort.by("name")).stream()
                .filter(PrinterConfig::isEnabled)
                .toList();

        Optional<PrinterConfig> a4 = pickA4(all);
        Optional<PrinterConfig> ticket = all.stream()
                .filter(p -> p.getType() != PrinterType.SYSTEM)
                .findFirst();

        for (PrinterUsage usage : List.of(PrinterUsage.INVOICE, PrinterUsage.REPORT)) {
            if (!assigned.containsKey(usage)) {
                a4.ifPresent(p -> assign(usage, p));
            }
        }
        if (!assigned.containsKey(PrinterUsage.TICKET)) {
            ticket.ifPresent(p -> assign(PrinterUsage.TICKET, p));
        }
    }

    private Optional<PrinterConfig> pickA4(List<PrinterConfig> all) {
        List<PrinterConfig> system = all.stream().filter(p -> p.getType() == PrinterType.SYSTEM).toList();
        String osDefault = SystemPrinters.defaultService().map(ps -> ps.getName()).orElse(null);
        return system.stream()
                .filter(p -> p.getAddress().equalsIgnoreCase(osDefault))
                .findFirst()
                .or(() -> system.stream().findFirst());
    }

    private void assign(PrinterUsage usage, PrinterConfig printer) {
        management.assign(usage, printer.getId());
        log.info("Usage {} affecté automatiquement à « {} »", usage, printer.getName());
    }
}
