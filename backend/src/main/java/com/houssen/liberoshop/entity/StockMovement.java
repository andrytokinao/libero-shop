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

    /**
     * When this receipt or correction was cancelled by an opposite movement -- kept, never
     * deleted, so the history still reads what happened. Null while it stands.
     */
    private LocalDateTime cancelledAt;

    /** +1 when the movement raises the stock, -1 when it lowers it. */
    public abstract int direction();

    /** The quantity with its sign, to be summed to get the stock of a product. */
    public BigDecimal signedQuantity() {
        return direction() >= 0 ? quantity : quantity.negate();
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }
}
