package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A delivery note: the one event "goods came in" that groups everything booked by one
 * "Enregistrer" -- the receipts (one per line), the charges paid to bring them in, the losses on
 * the way and, when there was one, the transformation that followed.
 *
 * <p>Not a stock movement itself: it moves nothing, the movements it groups do. It exists so that
 * the note can be read back whole, and cancelled whole.
 *
 * <p>Receipts booked before notes existed, or through the plain {@code /deliveries} endpoint,
 * have no note: {@code Supply.deliveryNote} is nullable.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class DeliveryNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime date;

    /** Who delivered it, or null when no supplier was named. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by")
    private UserApp performedBy;

    /** What the goods cost on the supplier invoice, all lines together. Zero when no price was given. */
    @Column(precision = 14, scale = 2)
    private BigDecimal purchaseTotal;

    /** Transport, loading... all the charges of the note together. */
    @Column(precision = 14, scale = 2)
    private BigDecimal chargesTotal;

    private boolean withTransformation;

    /** When the whole note was cancelled -- kept, never deleted. Null while it stands. */
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "deliveryNote")
    private List<Supply> supplies = new ArrayList<>();

    @OneToMany(mappedBy = "deliveryNote")
    private List<SupplyCharge> charges = new ArrayList<>();

    @OneToMany(mappedBy = "deliveryNote")
    private List<Transformation> transformations = new ArrayList<>();

    public boolean isCancelled() {
        return cancelledAt != null;
    }
}
