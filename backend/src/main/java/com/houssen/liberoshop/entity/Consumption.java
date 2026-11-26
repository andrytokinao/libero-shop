package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A material leaving the stock because a transformation used it.
 *
 * <p>{@code unitCost} is what one base unit cost at that moment: copied, never recomputed, so
 * the cost of the finished products does not move when the product's average moves later.
 */
@Entity
@DiscriminatorValue("CONSUMPTION")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Consumption extends StockMovement {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consumption_transformation_id")
    private Transformation transformation;

    /** What one base unit cost when it was used, or null when that cost is unknown. */
    @Column(name = "consumption_unit_cost", precision = 14, scale = 6)
    private BigDecimal unitCost;

    @Override
    public int direction() {
        return -1;
    }

    /** What the quantity used was worth, or null when its cost is unknown. */
    public BigDecimal totalCost() {
        return unitCost == null ? null
                : unitCost.multiply(getQuantity()).setScale(2, RoundingMode.HALF_UP);
    }
}
