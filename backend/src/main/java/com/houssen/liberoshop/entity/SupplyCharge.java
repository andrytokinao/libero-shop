package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A cost paid to bring goods in: transport, loading, unloading, duties.
 *
 * <p>{@code cust} is the whole amount of the charge, in ariary. The part of it carried by each
 * receipt is on the {@link SupplyUseCharge} that links the two.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SupplyCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The whole amount of the charge. */
    @Column(precision = 12, scale = 2)
    private BigDecimal cust;

    /** What it was for: "Transport", "Embarquement". */
    private String raison;

    /** True when it was entered for all the products of the note, not for one. Information only. */
    private boolean general;

    /** How it was typed: "lot" (a total) or "unit" (per unit received). Information only. */
    @Column(length = 10)
    private String basis;

    @OneToMany(mappedBy = "charge", cascade = CascadeType.ALL)
    private List<SupplyUseCharge> supplys = new ArrayList<>();
}
