package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** A total paid for all the products of a note, shared between the receipts. */
@Entity
@DiscriminatorValue("GENERAL")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class GeneralCharge extends SupplyCharge {

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private AllocationKey allocationKey = AllocationKey.VALUE;

    @Override
    public ChargeBasis getBasis() {
        return ChargeBasis.LOT;
    }

    @Override
    protected BigDecimal computeTotal(List<Supply> receipts) {
        return Objects.requireNonNull(getCust(), "cust");
    }

    /** By value only when every receipt has a cost and one is above zero; else by quantity. */
    @Override
    protected Function<Supply, BigDecimal> weigher(List<Supply> receipts) {
        boolean byValue = allocationKey == AllocationKey.VALUE
                && receipts.stream().allMatch(s -> s.totalCost() != null)
                && receipts.stream().anyMatch(s -> s.totalCost().signum() > 0);
        return byValue ? Supply::totalCost : Supply::receivedQuantity;
    }
}
