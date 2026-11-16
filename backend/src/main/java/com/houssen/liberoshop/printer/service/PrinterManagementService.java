package com.houssen.liberoshop.printer.service;

import com.houssen.liberoshop.entity.*;
import com.houssen.liberoshop.printer.driver.PrinterDriver;
import com.houssen.liberoshop.printer.driver.PrinterRegistry;
import com.houssen.liberoshop.printer.dto.PrinterView;
import com.houssen.liberoshop.printer.dto.RegisterPrinterRequest;
import com.houssen.liberoshop.printer.dto.UpdatePrinterRequest;
import com.houssen.liberoshop.printer.exception.PrinterConflictException;
import com.houssen.liberoshop.printer.exception.PrinterNotFoundException;
import com.houssen.liberoshop.repository.PrinterAssignmentRepository;
import com.houssen.liberoshop.repository.PrinterConfigRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/** Gestion des imprimantes choisies dans l'application et de leur affectation par usage. */
@Service
public class PrinterManagementService {

    private static final int DEFAULT_PORT = 9100;

    private final PrinterConfigRepository printers;
    private final PrinterAssignmentRepository assignments;
    private final PrinterRegistry registry;

    public PrinterManagementService(PrinterConfigRepository printers,
                                    PrinterAssignmentRepository assignments,
                                    PrinterRegistry registry) {
        this.printers = printers;
        this.assignments = assignments;
        this.registry = registry;
    }

    /** Liste des imprimantes avec état en ligne, vérifié en parallèle pour rester rapide avec beaucoup d'imprimantes. */
    @Transactional(readOnly = true)
    public List<PrinterView> list() {
        List<PrinterConfig> all = printers.findAll(Sort.by("name"));
        Map<String, List<PrinterUsage>> usages = assignments.findAll().stream()
                .collect(Collectors.groupingBy(PrinterAssignment::getPrinterId,
                        Collectors.mapping(PrinterAssignment::getUsage, Collectors.toList())));
        Map<String, Boolean> online = checkOnline(all);
        return all.stream()
                .map(p -> toView(p, online.getOrDefault(p.getId(), false), usages.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @Transactional
    public PrinterView register(RegisterPrinterRequest request) {
        validate(request);
        String address = request.address().trim();
        if (printers.findByTypeAndAddressIgnoreCase(request.type(), address).isPresent()) {
            throw new PrinterConflictException("Imprimante déjà ajoutée : " + address);
        }
        return toView(printers.save(toEntity(request)), null, List.of());
    }

    /** Ajoute plusieurs imprimantes d'un coup (sélection multiple) ; les doublons sont ignorés. */
    @Transactional
    public List<PrinterView> registerAll(List<RegisterPrinterRequest> requests) {
        List<PrinterView> created = new ArrayList<>();
        for (RegisterPrinterRequest request : requests) {
            validate(request);
            if (printers.findByTypeAndAddressIgnoreCase(request.type(), request.address().trim()).isPresent()) {
                continue;
            }
            created.add(toView(printers.save(toEntity(request)), null, List.of()));
        }
        return created;
    }

    @Transactional
    public PrinterView update(String id, UpdatePrinterRequest request) {
        PrinterConfig cfg = find(id);
        if (request.name() != null && !request.name().isBlank()) {
            cfg.setName(request.name().trim());
        }
        if (request.port() != null && cfg.getType() == PrinterType.ESCPOS_NET) {
            cfg.setPort(request.port());
        }
        if (request.widthChars() != null) {
            cfg.setWidthChars(request.widthChars());
        }
        if (request.profile() != null) {
            cfg.setProfile(request.profile());
        }
        if (request.codePage() != null) {
            cfg.setCodePage(CodePage.fromTable(request.codePage()).table());
        }
        if (request.enabled() != null) {
            cfg.setEnabled(request.enabled());
        }
        return toView(printers.save(cfg), null, usagesOf(id));
    }

    @Transactional
    public void delete(String id) {
        PrinterConfig cfg = find(id);
        assignments.deleteByPrinterId(id);
        printers.delete(cfg);
    }

    @Transactional(readOnly = true)
    public Map<PrinterUsage, String> assignments() {
        return assignments.findAll().stream().collect(Collectors.toMap(
                PrinterAssignment::getUsage, PrinterAssignment::getPrinterId,
                (a, b) -> a, () -> new EnumMap<>(PrinterUsage.class)));
    }

    @Transactional
    public void assign(PrinterUsage usage, String printerId) {
        PrinterConfig cfg = find(printerId);
        if (!cfg.isEnabled()) {
            throw new IllegalArgumentException("Imprimante désactivée : " + cfg.getName());
        }
        assignments.save(new PrinterAssignment(usage, printerId));
    }

    @Transactional
    public void unassign(PrinterUsage usage) {
        assignments.deleteById(usage);
    }

    private PrinterConfig find(String id) {
        return printers.findById(id).orElseThrow(() -> new PrinterNotFoundException("Imprimante inconnue : " + id));
    }

    private List<PrinterUsage> usagesOf(String printerId) {
        return assignments.findByPrinterId(printerId).stream().map(PrinterAssignment::getUsage).toList();
    }

    private PrinterView toView(PrinterConfig cfg, Boolean online, List<PrinterUsage> usages) {
        return PrinterView.of(cfg, online, usages, registry.get(cfg.getType()).supportedKinds());
    }

    private static void validate(RegisterPrinterRequest r) {
        if (r.name() == null || r.name().isBlank()) {
            throw new IllegalArgumentException("Le nom de l'imprimante est obligatoire");
        }
        if (r.type() == null) {
            throw new IllegalArgumentException("Le type d'imprimante est obligatoire");
        }
        if (r.address() == null || r.address().isBlank()) {
            throw new IllegalArgumentException("L'adresse (nom système ou IP) est obligatoire");
        }
        if (r.port() != null && (r.port() < 1 || r.port() > 65535)) {
            throw new IllegalArgumentException("Port invalide : " + r.port());
        }
    }

    private static PrinterConfig toEntity(RegisterPrinterRequest r) {
        PrinterConfig cfg = new PrinterConfig();
        cfg.setName(r.name().trim());
        cfg.setType(r.type());
        cfg.setAddress(r.address().trim());
        cfg.setPort(r.type() == PrinterType.ESCPOS_NET ? (r.port() != null ? r.port() : DEFAULT_PORT) : null);
        EscPosProfile profile = r.profile() != null ? r.profile() : EscPosProfile.GENERIC;
        cfg.setProfile(profile);
        cfg.setWidthChars(r.widthChars() != null ? r.widthChars() : profile.defaultWidth());
        if (r.codePage() != null) {
            cfg.setCodePage(CodePage.fromTable(r.codePage()).table());
        }
        return cfg;
    }

    private Map<String, Boolean> checkOnline(List<PrinterConfig> all) {
        Map<String, Boolean> result = new HashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<Boolean>> futures = new HashMap<>();
            for (PrinterConfig cfg : all) {
                PrinterDriver driver = registry.get(cfg.getType());
                futures.put(cfg.getId(), executor.submit(() -> driver.isReachable(cfg)));
            }
            futures.forEach((id, future) -> {
                try {
                    result.put(id, future.get(3, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    result.put(id, false);
                } catch (ExecutionException | TimeoutException e) {
                    result.put(id, false);
                }
            });
        }
        return result;
    }
}
