package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** A total paid for one receipt. */
@Entity
@DiscriminatorValue("LOT")
@NoArgsConstructor
@SuperBuilder
public class LotCharge extends SupplyCharge {

    @Override
    public ChargeBasis getBasis() {
        return ChargeBasis.LOT;
    }

    @Override
    protected BigDecimal computeTotal(List<Supply> receipts) {
        if (receipts.size() != 1) {
            throw new IllegalStateException("A lot charge is carried by exactly one receipt");
        }
        return Objects.requireNonNull(getCust(), "cust");
    }

    @Override
    protected Function<Supply, BigDecimal> weigher(List<Supply> receipts) {
        return supply -> BigDecimal.ONE;
    }
}
