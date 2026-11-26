package com.houssen.liberoshop.entity;

import com.houssen.liberoshop.util.Quantities;
import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Une matière consommée : sort du stock. */
@Entity
@Getter @Setter @NoArgsConstructor
public class TransformationInput {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Transformation transformation;
    @ManyToOne(optional = false) private Product product;

    /** En unité de base du produit. */
    @Column(precision = Quantities.PRECISION, scale = Quantities.SCALE) private BigDecimal quantity;
    /** Coût rendu d'une unité de base au moment de la consommation (copié, jamais recalculé). */
    @Column(precision = 14, scale = 6) private BigDecimal unitCost;
    @Column(precision = 14, scale = 2) private BigDecimal totalCost;
}