package com.houssen.liberoshop.entity;

import com.houssen.liberoshop.util.Quantities;
import com.houssen.liberoshop.util.SearchText;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    /** Counted in the base unit ({@link #unit}), to the thousandth -- see {@link Quantities}. */
    @Column(nullable = false, precision = Quantities.PRECISION, scale = Quantities.SCALE)
    private BigDecimal stockQuantity;

    /**
     * Weighted average purchase cost of one unit of the stock on hand -- the "coût moyen
     * pondéré". Moved only by {@code PurchaseCosting}, on every receipt that states a cost.
     * Null while no receipt of this product has ever carried one.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal averageCost;

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

    /**
     * Name and barcode folded by {@link SearchText}: what the sale screens' search compares
     * against, so "cafe" finds "Café". Derived, never written by hand -- refreshed before every
     * insert and update, and filled for older rows by {@code SearchTextBackfill}.
     */
    @Setter(AccessLevel.NONE)
    @Column(length = 512)
    private String searchText;

    /**
     * The other units it sells in, smallest first -- see {@link ProductPackaging}. Read-only from
     * here: they are written through {@code ProductUnitService}, so this side never decides.
     * Batch-fetched, so a grid of forty products costs one query for all their units.
     */
    @Setter(AccessLevel.NONE)
    @Builder.Default
    @OneToMany(mappedBy = "product")
    @OrderBy("factor asc")
    @BatchSize(size = 100)
    private List<ProductPackaging> packagings = new ArrayList<>();

    /** @param quantity in base units; negative takes goods out */
    public void adjustStock(BigDecimal quantity) {
        this.stockQuantity = Quantities.scaled(stockQuantity.add(quantity));
    }

    @PrePersist
    @PreUpdate
    public void refreshSearchText() {
        this.searchText = SearchText.fold(name, barcode);
    }
}
