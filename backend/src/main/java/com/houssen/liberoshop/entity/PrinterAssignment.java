package com.houssen.liberoshop.entity;

import jakarta.persistence.*;

/** Affectation « usage → imprimante » choisie par l'utilisateur. */
@Entity
@Table(name = "printer_assignment")
public class PrinterAssignment {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "usage_key", length = 30)
    private PrinterUsage usage;

    @Column(name = "printer_id", nullable = false, length = 36)
    private String printerId;

    protected PrinterAssignment() {
    }

    public PrinterAssignment(PrinterUsage usage, String printerId) {
        this.usage = usage;
        this.printerId = printerId;
    }

    public PrinterUsage getUsage() { return usage; }
    public String getPrinterId() { return printerId; }
    public void setPrinterId(String printerId) { this.printerId = printerId; }
}
