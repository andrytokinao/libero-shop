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

    /** Largeur en caractères pour les tickets (58 mm = 32, 80 mm = 48). */
    @Column(nullable = false)
    private int widthChars = 42;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Nullable : les lignes créées avant l'ajout des profils restent valides (traitées comme GENERIC). */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EscPosProfile profile;

    /** Numéro de table de caractères ESC/POS ; null = valeur du profil. */
    private Integer codePage;

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

    public EscPosProfile getProfile() { return profile != null ? profile : EscPosProfile.GENERIC; }
    public void setProfile(EscPosProfile profile) { this.profile = profile; }
    public Integer getCodePage() { return codePage; }
    public void setCodePage(Integer codePage) { this.codePage = codePage; }

    /** Table de caractères réellement utilisée : celle de l'imprimante, sinon celle du profil. */
    public CodePage effectiveCodePage() {
        return codePage != null ? CodePage.fromTable(codePage) : getProfile().codePage();
    }
}
