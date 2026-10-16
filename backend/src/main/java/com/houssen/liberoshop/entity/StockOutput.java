package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@DiscriminatorValue("OUTPUT")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StockOutput extends StockMovement {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    /**
     * What the goods were sold for: the total of the sale line, at the price and in the unit of
     * the sale. Null on outputs recorded before it was kept; those are valued at the product's
     * current price per base unit, as they always were.
     */
    @Column(name = "sale_value", precision = 12, scale = 2)
    private BigDecimal saleValue;

    /** {@link #saleValue}, or the current price per base unit for an output without one. */
    public BigDecimal value() {
        return saleValue != null ? saleValue
                : getProduct().getPrice().multiply(getQuantity()).setScale(2, RoundingMode.HALF_UP);
    }
}
