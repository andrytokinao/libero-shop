package com.houssen.liberoshop.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "printer_config",
        uniqueConstraints = @UniqueConstraint(columnNames = {"type", "address"}))
public class PrinterConfig {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrinterType type;

    /** Nom de l'imprimante dans l'OS (SYSTEM, ESCPOS_RAW_SYSTEM) ou adresse IP / hôte (ESCPOS_NET). */
    @Column(nullable = false, length = 255)
    private String address;

    private Integer port;

    /** Largeur en caractères pour les tickets (58 mm ≈ 32, 80 mm ≈ 42 ou 48). */
    @Column(nullable = false)
    private int widthChars = 42;

    @Column(nullable = false)
    private boolean enabled = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PrinterType getType() { return type; }
    public void setType(PrinterType type) { this.type = type; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public int getWidthChars() { return widthChars; }
    public void setWidthChars(int widthChars) { this.widthChars = widthChars; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
