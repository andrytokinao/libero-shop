package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

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

    /** Who delivered it, or null when the stock was entered by an import. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** Purchase price of one unit of this receipt, or null when it was not given. */
    @Column(precision = 12, scale = 2)
    private BigDecimal unitCost;

    /** True when nobody delivered these goods: they were counted in, not received. */
    public boolean isFromImport() {
        return supplier == null;
    }
}
