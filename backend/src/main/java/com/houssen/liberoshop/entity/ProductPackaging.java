package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Another way of selling or receiving a product than its base unit: "kg" next to "kapoka",
 * "carton de 12" next to "bouteille".
 *
 * <p>The base unit itself is not a row here. It stays on {@link Product} -- {@code unit},
 * {@code price}, {@code barcode}, and the stock counted in it -- so that a product sold one way
 * only, which is most of a catalogue, has nothing in this table and behaves exactly as before.
 * A row only ever says "this many base units, at this price".
 *
 * <p>The factor belongs to the product, never to the unit name: a kilo of rice is 3.5 kapoka, a
 * kilo of sugar is about 4. A shared "kg = 3.5 kapoka" table would be wrong for every product
 * but one.
 */
@Entity
@Table(name = "product_packaging",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "label"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPackaging {

    /** Up to a thousandth: 3.5 kapoka, 0.25 L. Beyond that the base unit is the wrong one. */
    public static final int FACTOR_SCALE = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** As the operator wrote it, like {@link Product#getUnit()}: "kg", "sac 50 kg". */
    @Column(nullable = false, length = Product.MAX_UNIT_LENGTH)
    private String label;

    /**
     * How many base units one of these holds: 3.5 for a kilo of rice counted in kapoka, 87.5 for
     * a 25 kg sack. Computed from {@link #reference} and {@link #referenceQuantity}, never typed,
     * and kept here so the till, the stock and the reports read one number without walking a
     * chain.
     */
    @Column(nullable = false, precision = 12, scale = FACTOR_SCALE)
    private BigDecimal factor;

    /**
     * The unit this one is written in: a "sac 25 kg" is 25 <em>kg</em>, whatever a kilo is in
     * kapoka. Null when it is written in the base unit directly.
     *
     * <p>Kept as a link, not as the number it gave at the time: when a kilo turns out to be 3.6
     * kapoka rather than 3.5, every unit written in kilos follows, as the sack still holds 25 kg.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_id")
    private ProductPackaging reference;

    /**
     * How many {@link #reference} units one of these holds: 25 for a 25 kg sack written in kg.
     * Null on units written before references existed, which were all in the base unit -- read it
     * through {@link #quantityInReference()}.
     */
    @Column(precision = 12, scale = FACTOR_SCALE)
    private BigDecimal referenceQuantity;

    /**
     * What one of these sells for. Set by the operator rather than derived from the base price:
     * a kapoka is priced to a round coin and a sack carries a wholesale discount, so
     * {@code factor × base price} is almost never the price on the board.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** A sack or a carton often has its own code, distinct from the unit's. */
    @Column(unique = true)
    private String barcode;

    /** How many reference units one of these holds; the factor itself for a unit in base units. */
    public BigDecimal quantityInReference() {
        return referenceQuantity == null ? factor : referenceQuantity;
    }

    /** Whether {@code other} is this unit or is written, directly or through others, in it. */
    public boolean underlies(ProductPackaging other) {
        for (ProductPackaging step = other; step != null; step = step.getReference()) {
            if (step == this || (id != null && id.equals(step.getId()))) {
                return true;
            }
        }
        return false;
    }
}
