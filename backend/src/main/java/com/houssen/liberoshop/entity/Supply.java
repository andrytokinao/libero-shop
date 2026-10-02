package com.houssen.liberoshop.entity;

import com.houssen.liberoshop.util.Quantities;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Goods coming in: the stock rose, and this says by how much, when and at whose hand.
 *
 * <p>{@code supplier} is nullable, and the null carries meaning. A receipt booked through
 * {@code StockService.registerSupply} always names one -- that path still refuses to run
 * without it -- so the only entries without a supplier are the ones a product import wrote.
 * The distinction is therefore in the data rather than in a second movement class: "stock
 * entered from an inventory file" and "stock received from a wholesaler" are the same event
 * to the shelf, and differ only in whether anybody delivered it.
 *
 * <p>Two consequences worth knowing before querying this. The supplier has to be joined with a
 * LEFT join, or import entries silently vanish from the supplies list; and any aggregation by
 * supplier has to exclude the nulls rather than group them into a nameless bucket.
 *
 * <p>{@code unitCost} is what one unit of this receipt cost the shop. It is kept per receipt
 * rather than only folded into the product's average, because it is the one record of what a
 * given supplier charged on a given day: the average answers "what is my stock worth", these
 * rows answer "who sells me rice cheapest", and a later move to first-in-first-out costing
 * would read its lots from here. Null when nobody said -- receipts booked before costs were
 * recorded, or an import file without a cost column.
 */
@Entity
@DiscriminatorValue("SUPPLY")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Supply extends StockMovement {

    /** The two places of the cost columns, here and on the product's average. */
    private static final int COST_SCALE = 2;

    /** Who delivered it, or null when the stock was entered by an import. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /**
     * Purchase price of one unit <em>as received</em> -- of one sack for "10 sacs", as the
     * supplier's invoice says it -- or null when it was not given. Read {@link #baseUnitCost()}
     * for what one base unit cost, which is what averages and comparisons need.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal unitCost;

    /**
     * The unit the goods came in, as it was called then: "sac 50 kg". Copied, never linked, like
     * a sale line's: renaming or deleting the unit later must not rewrite the receipt. Null when
     * they came in the base unit, and on receipts booked before units existed.
     */
    @Column(length = Product.MAX_UNIT_LENGTH)
    private String unitLabel;

    /**
     * Base units in one unit received: 175 for a 50 kg sack of rice counted in kapoka. Null for
     * the base unit -- read it through {@link #factor()}. {@code quantity} stays in base units
     * either way, so the movement adds up with the stock.
     */
    @Column(precision = 12, scale = ProductPackaging.FACTOR_SCALE)
    private BigDecimal unitFactor;

    /** True when nobody delivered these goods: they were counted in, not received. */
    public boolean isFromImport() {
        return supplier == null;
    }

    /** Base units in one unit received; 1 for the base unit. */
    public BigDecimal factor() {
        return unitFactor == null ? BigDecimal.ONE : unitFactor;
    }

    /** How many units came in, as received: 10 for "10 sacs", whatever that is in base units. */
    public BigDecimal receivedQuantity() {
        return getQuantity().divide(factor(), Quantities.SCALE, RoundingMode.HALF_UP);
    }

    /**
     * What one base unit of this receipt cost: 145 000 Ar a sack of 175 kapoka is 828.57 Ar a
     * kapoka. Null when no cost was given.
     */
    public BigDecimal baseUnitCost() {
        return unitCost == null ? null
                : unitCost.divide(factor(), COST_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * What the whole receipt cost: the price as received times the quantity as received, so it
     * matches the supplier's invoice to the ariary -- not the rounded base cost times the base
     * quantity. Null when no cost was given.
     */
    public BigDecimal totalCost() {
        return unitCost == null ? null
                : unitCost.multiply(receivedQuantity()).setScale(2, RoundingMode.HALF_UP);
    }
}
