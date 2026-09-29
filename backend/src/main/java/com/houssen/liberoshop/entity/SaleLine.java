package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /**
     * What one unit cost the shop at the moment it was sold, frozen like {@code unitPrice}.
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
}
