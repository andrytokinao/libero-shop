package com.houssen.liberoshop.entity;

import com.houssen.liberoshop.util.Quantities;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A product obtained by a transformation: the stock rises because something was made.
 *
 * <p>Same shape as a receipt ({@link Supply}): quantity in base units, the unit it was obtained
 * in, the cost of one unit. The difference: it never has a supplier, and its cost is not a
 * purchase price but the part of the transformation's cost that falls to it.
 */
@Entity
@DiscriminatorValue("PRODUCTION")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Production extends StockMovement {

    private static final int COST_SCALE = 2;
    private static final int EXACT_COST_SCALE = 6;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_transformation_id")
    private Transformation transformation;

    /** The part of the transformation's cost carried by this product, in percent. */
    @Column(name = "production_cost_share", precision = 5, scale = 2)
    private BigDecimal costShare;

    /** Cost of one unit as obtained (a sack, a carton), or null when an input cost is unknown. */
    @Column(name = "production_unit_cost", precision = 14, scale = 6)
    private BigDecimal unitCost;

    /** The unit it was obtained in, copied like a receipt's. Null: the base unit. */
    @Column(name = "production_unit_label", length = Product.MAX_UNIT_LENGTH)
    private String unitLabel;

    /** Base units in one unit obtained. Null for the base unit. */
    @Column(name = "production_unit_factor", precision = 12, scale = ProductPackaging.FACTOR_SCALE)
    private BigDecimal unitFactor;

    @Override
    public int direction() {
        return 1;
    }

    public BigDecimal factor() {
        return unitFactor == null ? BigDecimal.ONE : unitFactor;
    }

    /** How many units were obtained, as obtained. */
    public BigDecimal producedQuantity() {
        return getQuantity().divide(factor(), Quantities.SCALE, RoundingMode.HALF_UP);
    }

    /** Cost of one base unit, to six places, for the product's average. */
    public BigDecimal exactBaseUnitCost() {
        return unitCost == null ? null
                : unitCost.divide(factor(), EXACT_COST_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal baseUnitCost() {
        return unitCost == null ? null
                : unitCost.divide(factor(), COST_SCALE, RoundingMode.HALF_UP);
    }

    /** What the quantity obtained is worth: its share of the transformation's cost. */
    public BigDecimal totalCost() {
        return unitCost == null ? null
                : unitCost.multiply(producedQuantity()).setScale(2, RoundingMode.HALF_UP);
    }
}
