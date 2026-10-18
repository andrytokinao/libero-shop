package com.houssen.liberoshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One change to a product's sheet -- its price, a unit, its name -- with who made it, when, and
 * what it was before: the trace the shop asks for when a price is found changed.
 *
 * <p>Written as text, as the screen said it ("12 500", "sac 50 kg"): the journal is read, never
 * computed with, and it must still read right after the unit it names was renamed or removed.
 */
@Entity
@Table(name = "catalog_change", indexes = @Index(name = "idx_catalog_change_product", columnList = "product_id"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** Null for a change no one signed in for: an import at start-up, the demo data. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private UserApp changedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** What changed, in the shop's words: "Prix de vente (sac 50 kg)". */
    @Column(nullable = false, length = 120)
    private String what;

    @Column(name = "value_before", length = 255)
    private String before;

    @Column(name = "value_after", length = 255)
    private String after;
}
