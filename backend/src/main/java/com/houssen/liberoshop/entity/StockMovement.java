package com.houssen.liberoshop.entity;

import com.houssen.liberoshop.util.Quantities;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movement")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "movement_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** In the product's base unit, whatever unit the goods were sold or received in. */
    @Column(nullable = false, precision = Quantities.PRECISION, scale = Quantities.SCALE)
    private BigDecimal quantity;

    @Column(nullable = false)
    private LocalDateTime movementDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by")
    private UserApp performedBy;
}
