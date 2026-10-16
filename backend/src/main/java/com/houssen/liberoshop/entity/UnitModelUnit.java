package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One unit of a {@link UnitModel}, written the way it is said: "tonne" = 1000 "kg".
 *
 * <p>The reference is kept by name, not as a link: a model is a short list read whole, and the
 * name is what is matched against a product's units when the model is applied.
 */
@Entity
@Table(name = "unit_model_unit",
        uniqueConstraints = @UniqueConstraint(columnNames = {"model_id", "label"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitModelUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id")
    private UnitModel model;

    @Column(nullable = false, length = Product.MAX_UNIT_LENGTH)
    private String label;

    /** How many {@link #referenceLabel} units it holds: 1000 for a tonne written in kg. */
    @Column(nullable = false, precision = 12, scale = ProductPackaging.FACTOR_SCALE)
    private BigDecimal quantity;

    /** Another unit of the model, by name; null when written in the model's reference unit. */
    @Column(length = Product.MAX_UNIT_LENGTH)
    private String referenceLabel;
}
