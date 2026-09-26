package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    /** Long enough for "carton de 12", short enough to stay a unit rather than a comment. */
    public static final int MAX_UNIT_LENGTH = 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stockQuantity;

    /**
     * How the shelf counts it: "kg", "L", "sachet", "carton de 12". Kept exactly as the
     * operator wrote it -- an import file says "Kilo" where another says "kg", and folding
     * those into a vocabulary this application invented would relabel the shop's own goods.
     * Nullable, because a catalogue counted in bare units has nothing to say here.
     */
    @Column(length = MAX_UNIT_LENGTH)
    private String unit;

    // Extension point: filled by hand today, by a barcode scanner tomorrow.
    @Column(unique = true)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    public void adjustStock(int quantity) {
        this.stockQuantity += quantity;
    }
}
