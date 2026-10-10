package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The stock corrected by hand: goods broken, missing or spoiled, or a shelf counted and found
 * different from what the application believed.
 *
 * <p>Unlike a supply or an output, whose direction is their type, {@code quantity} is signed
 * here: negative for what left, positive when a count found more than expected. It is the
 * difference, never the count itself, so the movements of a product still add up to its stock.
 *
 * <p>The product's average cost is left alone either way: losing three bottles does not change
 * what the remaining ones cost, and goods found by a count came from a receipt that already
 * taught the average its price.
 */
@Entity
@DiscriminatorValue("ADJUSTMENT")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StockAdjustment extends StockMovement {

    /** A plain varchar, so a reason added later needs no column migration. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private AdjustmentReason reason;

    /** What the operator had to say about it; required for {@link AdjustmentReason#OTHER}. */
    @Column(name = "adjustment_comment")
    private String comment;
}
