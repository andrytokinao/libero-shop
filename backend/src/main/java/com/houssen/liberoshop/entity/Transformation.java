package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Materials turned into finished products: the event that groups what went out (consumptions),
 * what was lost, what came out ({@link Production}) and what it cost to do it.
 *
 * <p>Not a stock movement itself: it moves nothing, its movements do.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Transformation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime date;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by")
    private UserApp performedBy;

    private String note;

    /** The delivery note whose goods were transformed, or null for a transformation done alone. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_note_id")
    private DeliveryNote deliveryNote;

    /** What the materials used were worth, at their cost when used. */
    @Column(precision = 14, scale = 2)
    private BigDecimal inputCost;

    /** Wages, energy, labour: what it cost to make, besides the materials. */
    @Column(precision = 14, scale = 2)
    private BigDecimal productionCost;

    /** {@code inputCost + productionCost}: the sum spread over the products obtained. */
    @Column(precision = 14, scale = 2)
    private BigDecimal totalCost;

    /** When it was cancelled -- kept, never deleted. Null while it stands. */
    private LocalDateTime cancelledAt;

    /** The materials used. */
    @OneToMany(mappedBy = "transformation")
    private List<Consumption> inputs = new ArrayList<>();

    /** What was lost while making it. */
    @OneToMany(mappedBy = "transformation")
    private List<StockLoss> losses = new ArrayList<>();

    /** The products obtained. */
    @OneToMany(mappedBy = "transformation")
    private List<Production> outputs = new ArrayList<>();

    @OneToMany(mappedBy = "transformation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransformationCost> costs = new ArrayList<>();

    public boolean isCancelled() {
        return cancelledAt != null;
    }
}
