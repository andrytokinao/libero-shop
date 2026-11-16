package com.houssen.liberoshop.printer.service;

import com.houssen.liberoshop.entity.*;
import com.houssen.liberoshop.printer.driver.PrinterDriver;
import com.houssen.liberoshop.printer.driver.PrinterRegistry;
import com.houssen.liberoshop.printer.exception.PrintException;
import com.houssen.liberoshop.printer.exception.PrinterNotFoundException;
import com.houssen.liberoshop.printer.service.tiket.PdfTicketPrinter;
import com.houssen.liberoshop.repository.PrinterAssignmentRepository;
import com.houssen.liberoshop.repository.PrinterConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Point d'entrée du reste de l'application :
 * {@code printService.printFor(PrinterUsage.TICKET, PrintJob.escpos("Ticket", data));}
 *
 * <p>Deux façons d'imprimer. Un {@link PrintJob} est un travail déjà dans sa forme finale : il va
 * à une imprimante qui accepte son format, ou est refusé. Un {@link PrintableDocument} offre
 * plusieurs formes, et c'est ici qu'on choisit celle de chaque imprimante :
 * <ul>
 *   <li>imprimante {@code SYSTEM} (A4) : le PDF A4 ;</li>
 *   <li>imprimante ticket ESC/POS : le ticket natif du document s'il en a un ; sinon son PDF au
 *       format ticket, converti en image ESC/POS par {@link PdfTicketPrinter}.</li>
 * </ul>
 * Un document sans forme pour une imprimante est simplement passé à la suivante.
 */
@Service
@RequiredArgsConstructor
public class PrintService {

    private static final Logger log = LoggerFactory.getLogger(PrintService.class);
    private final PrinterRegistry registry;
    private final PrinterConfigRepository printers;
    private final PrinterAssignmentRepository assignments;
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final PdfTicketPrinter pdfTicketPrinter;

    /** Imprime sur l'imprimante que l'utilisateur a affectée à cet usage. */
    public void printFor(PrinterUsage usage, PrintJob job) {
        print(assigned(usage), job);
    }

    /** Imprime le document sur l'imprimante affectée à cet usage, sous la forme qu'elle accepte. */
    public void printFor(PrinterUsage usage, PrintableDocument document) {
        print(assigned(usage), document);
    }

    public void print(String printerId, PrintJob job) {
        dispatch(enabled(printerId), job);
    }

    /**
     * Imprime le document sur l'imprimante {@code printerId}, sous la forme qu'elle accepte.
     *
     * @throws PrintException si l'imprimante est désactivée, ou si le document n'a pas de forme pour elle
     */
    public void print(String printerId, PrintableDocument document) {
        PrinterConfig cfg = enabled(printerId);
        if (!canPrint(cfg, document)) {
            throw new PrintException("L'imprimante « " + cfg.getName() + " » (" + cfg.getType()
                    + ") ne peut pas imprimer « " + document.title() + " » : le document n'a pas de version "
                    + (cfg.getType() == PrinterType.SYSTEM ? "A4" : "ticket"), null);
        }
        dispatch(cfg, jobFor(cfg, document));
    }

    /**
     * Sur l'imprimante {@code printerId} si fournie, sinon sur la première qui peut imprimer le
     * document -- ce qu'attend un écran qui laisse le choix de l'imprimante facultatif.
     */
    public void printDocument(String printerId, PrintableDocument document) {
        if (printerId == null || printerId.isBlank()) {
            printForFirstAvailable(document);
        } else {
            print(printerId, document);
        }
    }

    /**
     * Imprime sur la première imprimante active, joignable et compatible avec le format du job
     * (ordre alphabétique). Si l'envoi échoue sur l'une, la suivante est essayée.
     */
    public void printForFirstAvailable(PrintJob job) {
        printOnFirst(
                cfg -> registry.get(cfg.getType()).supportedKinds().contains(job.kind()),
                cfg -> job,
                "Aucune imprimante active compatible avec le format " + job.kind());
    }

    /**
     * Imprime le document sur la première imprimante active et joignable qui a une forme pour lui
     * (ordre alphabétique) : A4 pour une imprimante système, ticket pour une ESC/POS. Si l'envoi
     * échoue sur l'une, la suivante est essayée.
     */
    public void printForFirstAvailable(PrintableDocument document) {
        printOnFirst(
                cfg -> canPrint(cfg, document),
                cfg -> jobFor(cfg, document),
                "Aucune imprimante active capable d'imprimer « " + document.title() + " »");
    }

    public void testPrint(String printerId) {
        PrinterConfig cfg = find(printerId);
        print(printerId, TestPageFactory.create(cfg));
    }

    /** Imprime les accents dans chaque table de caractères pour choisir la bonne (imprimantes ESC/POS). */
    public void testCodePages(String printerId) {
        PrinterConfig cfg = find(printerId);
        if (cfg.getType() == PrinterType.SYSTEM) {
            throw new IllegalArgumentException("Le test des tables de caractères concerne les imprimantes ESC/POS");
        }
        print(printerId, TestPageFactory.codePages(cfg));
    }

    /** Le travail de {@code document} pour cette imprimante : A4, ticket natif, ou PDF ticket en image. */
    private PrintJob jobFor(PrinterConfig cfg, PrintableDocument document) {
        if (cfg.getType() == PrinterType.SYSTEM) {
            return PrintJob.pdf(document.title(), document.a4Pdf());
        }
        return document.nativeTicket(cfg)
                .orElseGet(() -> PrintJob.escpos(document.title(), pdfTicketPrinter.toEscPos(document.ticketPdf())));
    }

    private static boolean canPrint(PrinterConfig cfg, PrintableDocument document) {
        return cfg.getType() == PrinterType.SYSTEM ? document.hasA4() : document.hasTicket();
    }

    /**
     * Le travail de chaque imprimante active qui l'accepte, essayé dans l'ordre alphabétique ;
     * les injoignables sont sautées, et un échec d'envoi passe à la suivante.
     */
    private void printOnFirst(Predicate<PrinterConfig> accepts, Function<PrinterConfig, PrintJob> jobFor,
                              String noPrinterMessage) {
        List<PrinterConfig> candidates = printers.findAll(Sort.by("name")).stream()
                .filter(PrinterConfig::isEnabled)
                .filter(accepts)
                .toList();
        if (candidates.isEmpty()) {
            throw new PrinterNotFoundException(noPrinterMessage);
        }
        PrintException lastFailure = null;
        for (PrinterConfig cfg : candidates) {
            if (!registry.get(cfg.getType()).isReachable(cfg)) {
                continue;
            }
            try {
                dispatch(cfg, jobFor.apply(cfg));
                return;
            } catch (PrintException e) {
                log.warn("Imprimante « {} » en échec, essai de la suivante : {}", cfg.getName(), e.getMessage());
                lastFailure = e;
            }
        }
        throw lastFailure != null ? lastFailure
                : new PrintException("Aucune imprimante joignable parmi " + candidates.size() + " configurée(s)", null);
    }

    private String assigned(PrinterUsage usage) {
        PrinterAssignment assignment = assignments.findById(usage)
                .orElseThrow(() -> new PrinterNotFoundException("Aucune imprimante affectée à l'usage " + usage));
        return assignment.getPrinterId();
    }

    private PrinterConfig enabled(String printerId) {
        PrinterConfig cfg = find(printerId);
        if (!cfg.isEnabled()) {
            throw new PrintException("Imprimante désactivée : " + cfg.getName(), null);
        }
        return cfg;
    }

    private PrinterConfig find(String printerId) {
        return printers.findById(printerId)
                .orElseThrow(() -> new PrinterNotFoundException("Imprimante inconnue : " + printerId));
    }

    private void dispatch(PrinterConfig cfg, PrintJob job) {
        PrinterDriver driver = registry.get(cfg.getType());
        if (!driver.supportedKinds().contains(job.kind())) {
            throw new PrintException("L'imprimante « " + cfg.getName() + " » (" + cfg.getType()
                    + ") n'accepte pas le format " + job.kind(), null);
        }
        // Un seul job à la fois par imprimante : les imprimantes thermiques n'aiment pas les connexions concurrentes.
        ReentrantLock lock = locks.computeIfAbsent(cfg.getId(), k -> new ReentrantLock());
        lock.lock();
        try {
            driver.print(cfg, job);
            log.info("Impression « {} » envoyée à {} ({})", job.title(), cfg.getName(), cfg.getType());
        } finally {
            lock.unlock();
        }
    }
}