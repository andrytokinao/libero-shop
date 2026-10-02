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

    /** How many base units one of these holds: 3.5 for a kilo of rice counted in kapoka. */
    @Column(nullable = false, precision = 12, scale = FACTOR_SCALE)
    private BigDecimal factor;

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
}
