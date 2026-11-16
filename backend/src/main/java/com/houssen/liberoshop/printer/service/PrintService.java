package com.houssen.liberoshop.printer.service;


import com.houssen.liberoshop.entity.PrintJob;
import com.houssen.liberoshop.entity.PrinterAssignment;
import com.houssen.liberoshop.entity.PrinterConfig;
import com.houssen.liberoshop.entity.PrinterUsage;
import com.houssen.liberoshop.printer.driver.*;
import com.houssen.liberoshop.printer.exception.PrintException;
import com.houssen.liberoshop.printer.exception.PrinterNotFoundException;
import com.houssen.liberoshop.repository.PrinterAssignmentRepository;
import com.houssen.liberoshop.repository.PrinterConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Point d'entrée du reste de l'application :
 * {@code printService.printFor(PrinterUsage.TICKET, PrintJob.escpos("Ticket", data));}
 */
@Service
public class PrintService {

    private static final Logger log = LoggerFactory.getLogger(PrintService.class);

    private final PrinterRegistry registry;
    private final PrinterConfigRepository printers;
    private final PrinterAssignmentRepository assignments;
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public PrintService(PrinterRegistry registry,
                        PrinterConfigRepository printers,
                        PrinterAssignmentRepository assignments) {
        this.registry = registry;
        this.printers = printers;
        this.assignments = assignments;
    }

    /** Imprime sur l'imprimante que l'utilisateur a affectée à cet usage. */
    public void printFor(PrinterUsage usage, PrintJob job) {
        PrinterAssignment assignment = assignments.findById(usage)
                .orElseThrow(() -> new PrinterNotFoundException("Aucune imprimante affectée à l'usage " + usage));
        print(assignment.getPrinterId(), job);
    }

    public void print(String printerId, PrintJob job) {
        PrinterConfig cfg = printers.findById(printerId)
                .orElseThrow(() -> new PrinterNotFoundException("Imprimante inconnue : " + printerId));
        if (!cfg.isEnabled()) {
            throw new PrintException("Imprimante désactivée : " + cfg.getName(), null);
        }
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

    public void testPrint(String printerId) {
        PrinterConfig cfg = printers.findById(printerId)
                .orElseThrow(() -> new PrinterNotFoundException("Imprimante inconnue : " + printerId));
        print(printerId, TestPageFactory.create(cfg));
    }
}
