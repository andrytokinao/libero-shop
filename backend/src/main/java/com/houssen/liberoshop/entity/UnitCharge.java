package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A charge typed per unit received (per sack, per crate), on one receipt or on several. It is
 * booked with its total; the amount per unit is derived on the first split, and drives the total
 * afterwards: a corrected quantity changes what the charge costs.
 */
@Entity
@DiscriminatorValue("UNIT")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UnitCharge extends SupplyCharge {

    private static final int UNIT_SCALE = 6;

    @Column(precision = 18, scale = UNIT_SCALE)
    private BigDecimal unitAmount;

    @Override
    public ChargeBasis getBasis() {
        return ChargeBasis.UNIT;
    }

    @Override
    protected BigDecimal computeTotal(List<Supply> receipts) {
        BigDecimal quantity = receipts.stream()
                .map(Supply::receivedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (unitAmount == null) {
            unitAmount = Objects.requireNonNull(getCust(), "cust").divide(quantity, UNIT_SCALE, RoundingMode.HALF_UP);
            return getCust();
        }
        return unitAmount.multiply(quantity);
    }

    @Override
    protected Function<Supply, BigDecimal> weigher(List<Supply> receipts) {
        return Supply::receivedQuantity;
    }
}