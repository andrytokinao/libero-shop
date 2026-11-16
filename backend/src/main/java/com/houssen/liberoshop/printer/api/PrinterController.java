package com.houssen.liberoshop.printer.api;

import com.houssen.liberoshop.entity.PrinterUsage;
import com.houssen.liberoshop.printer.dto.*;
import com.houssen.liberoshop.printer.service.PrintService;
import com.houssen.liberoshop.printer.service.PrinterAutoRegistrationService;
import com.houssen.liberoshop.printer.service.PrinterDiscoveryService;
import com.houssen.liberoshop.printer.service.PrinterManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/printers")
public class PrinterController {

    private final PrinterDiscoveryService discovery;
    private final PrinterManagementService management;
    private final PrintService printService;
    private final PrinterAutoRegistrationService autoRegistration;

    public PrinterController(PrinterDiscoveryService discovery,
                             PrinterManagementService management,
                             PrintService printService,
                             PrinterAutoRegistrationService autoRegistration) {
        this.discovery = discovery;
        this.management = management;
        this.printService = printService;
        this.autoRegistration = autoRegistration;
    }

    /** Imprimantes détectées (installées dans l'OS ; + réseau local si {@code network=true}). */
    @GetMapping("/discover")
    public List<DiscoveredPrinter> discover(@RequestParam(defaultValue = "false") boolean network,
                                            @RequestParam(required = false) String subnet) {
        List<DiscoveredPrinter> result = new ArrayList<>(discovery.discoverSystem());
        if (network) {
            result.addAll(discovery.discoverNetwork(subnet));
        }
        return result;
    }

    /**
     * Détecte les imprimantes et enregistre celles qui ne le sont pas encore ; renvoie les imprimantes ajoutées.
     * Les usages libres sont ensuite affectés automatiquement.
     */
    @PostMapping("/sync")
    public List<PrinterView> sync(@RequestParam(defaultValue = "false") boolean network,
                                  @RequestParam(required = false) String subnet) {
        List<PrinterView> added = autoRegistration.sync(network, subnet);
        autoRegistration.assignFreeUsages();
        return added;
    }

    @GetMapping
    public List<PrinterView> list() {
        return management.list();
    }

    @PostMapping
    public ResponseEntity<PrinterView> register(@Valid @RequestBody RegisterPrinterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(management.register(request));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<PrinterView>> registerAll(@RequestBody List<RegisterPrinterRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED).body(management.registerAll(requests));
    }

    @PutMapping("/{id}")
    public PrinterView update(@PathVariable String id, @Valid @RequestBody UpdatePrinterRequest request) {
        return management.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        management.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Void> test(@PathVariable String id) {
        printService.testPrint(id);
        return ResponseEntity.noContent().build();
    }

    /** Imprime les accents dans chaque table de caractères : l'utilisateur retient celle qui s'affiche bien. */
    @PostMapping("/{id}/test-codepages")
    public ResponseEntity<Void> testCodePages(@PathVariable String id) {
        printService.testCodePages(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assignments")
    public Map<PrinterUsage, String> assignments() {
        return management.assignments();
    }

    @PutMapping("/assignments/{usage}")
    public ResponseEntity<Void> assign(@PathVariable PrinterUsage usage, @Valid @RequestBody AssignRequest request) {
        management.assign(usage, request.printerId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/assignments/{usage}")
    public ResponseEntity<Void> unassign(@PathVariable PrinterUsage usage) {
        management.unassign(usage);
        return ResponseEntity.noContent().build();
    }
}
