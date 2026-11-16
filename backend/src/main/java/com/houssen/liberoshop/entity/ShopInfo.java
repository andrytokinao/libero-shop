package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Who the shop is, as its documents print it: the name at the top of an invoice or a receipt,
 * and the address and contact under it.
 *
 * <p>One row for the whole shop, always {@link #SINGLETON_ID}: there is no list of shops to
 * choose from, and a fixed key means a second row cannot appear by mistake. Read and written
 * through {@code ShopInfoService}, which falls back on the {@code liberoshop.shop-*} properties
 * while nothing has been saved yet.
 *
 * <p>The documents' templates call {@link #name()}, {@link #address()} and {@link #contact()},
 * as they did on the record this entity replaces; the last two never answer null, so a template
 * can test {@code isEmpty()} without a guard.
 */
@Entity
@Table(name = "shop_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopInfo {

    /** The key of the one row. */
    public static final long SINGLETON_ID = 1L;

    @Id
    @Builder.Default
    private Long id = SINGLETON_ID;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(length = 255)
    private String contact;
    private String current;

    /** For the templates: what the documents print at the top. */
    public String name() {
        return name;
    }

    /** For the templates: empty, never null, when the shop gave no address. */
    public String address() {
        return address == null ? "" : address;
    }

    /** For the templates: empty, never null, when the shop gave no contact. */
    public String contact() {
        return contact == null ? "" : contact;
    }
}