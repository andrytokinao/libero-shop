package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Links a charge to a receipt it was paid for, with the part of the charge that receipt carries.
 * A general charge has one row per receipt of the note; their shares add up to the charge.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SupplyUseCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private SupplyCharge charge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Supply supply;

    /** The part of {@code charge.cust} carried by this receipt, in ariary. */
    @Column(precision = 12, scale = 2)
    private BigDecimal share;
}
