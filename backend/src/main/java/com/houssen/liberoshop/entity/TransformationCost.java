package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** A cost of production, besides the materials: wages, energy, packaging labour. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class TransformationCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Transformation transformation;

    /** What it was for: "Salaires", "Énergie". */
    private String raison;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;
}
