package com.houssen.liberoshop.entity;

import com.houssen.liberoshop.util.Quantities;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "sale_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** In the unit it was sold in -- 2 for "2 kg" -- not in the product's base unit. */
    @Column(nullable = false, precision = 12, scale = Quantities.SCALE)
    private BigDecimal quantity;

    /** The price of one of the unit sold: the kilo's price for "2 kg", not the kapoka's. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /**
     * The unit it was sold in, as it was called at that moment: "kg", "sac 50 kg", or the base
     * unit's name. Copied, never linked, like the price -- renaming or deleting a unit later must
     * not rewrite a receipt. Null for a product counted in bare units, and on lines sold before
     * units existed.
     */
    @Column(length = Product.MAX_UNIT_LENGTH)
    private String unitLabel;

    /**
     * Base units in one of the unit sold, copied at the sale: 3.5 for a kilo of rice counted in
     * kapoka. Null on lines sold before units existed, which were all in the base unit -- read it
     * through {@link #factor()}.
     */
    @Column(precision = 12, scale = ProductPackaging.FACTOR_SCALE)
    private BigDecimal unitFactor;

    /**
     * What one of the unit sold cost the shop at the moment it was sold, frozen like
     * {@code unitPrice} -- the average cost of a base unit times {@link #factor()}.
     *
     * <p>Copied from the product's average cost at checkout so that the margin of a past sale
     * never moves when a later delivery changes the average. Null when the cost was unknown at
     * the time -- a sale made before costs were recorded -- and reports leave those lines out of
     * the margin rather than counting them as free goods.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal unitCost;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Base units in one of the unit sold; 1 for a line sold before units existed. */
    public BigDecimal factor() {
        return unitFactor == null ? BigDecimal.ONE : unitFactor;
    }

    /** What left the shelf, in base units: 2 kg of rice is 7 kapoka. */
    public BigDecimal baseQuantity() {
        return Quantities.scaled(quantity.multiply(factor()));
    }

    /** Price times quantity, to the ariary's two places. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }
}
