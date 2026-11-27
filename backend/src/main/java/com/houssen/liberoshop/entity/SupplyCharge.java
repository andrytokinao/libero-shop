package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A cost paid to bring goods in: transport, loading, unloading, duties.
 *
 * <p>{@code cust} is the whole amount of the charge, in ariary. {@link #allocate()} splits it
 * over the receipts; subclasses only say what the total is and what each receipt weighs.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "charge_type", length = 10)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class SupplyCharge {

    private static final int SHARE_SCALE = 2;

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

    /** The delivery note this charge was paid for. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_note_id")
    private DeliveryNote deliveryNote;

    @OneToMany(mappedBy = "charge", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SupplyUseCharge> supplys = new ArrayList<>();

    /** How it was typed: a total or per unit received. */
    public abstract ChargeBasis getBasis();

    /** The whole amount of the charge over these receipts. */
    protected abstract BigDecimal computeTotal(List<Supply> receipts);

    /** How much each receipt weighs in the split. */
    protected abstract Function<Supply, BigDecimal> weigher(List<Supply> receipts);

    /** Links a receipt to this charge, on both sides. */
    public void addSupply(Supply supply) {
        if (supplys.stream().anyMatch(use -> use.getSupply() == supply)) {
            throw new IllegalArgumentException("Receipt already linked to charge: " + raison);
        }
        SupplyUseCharge use = SupplyUseCharge.builder()
                .charge(this)
                .supply(supply)
                .build();
        supplys.add(use);
        if (supply.getCharges() == null) {
            supply.setCharges(new ArrayList<>());
        }
        supply.getCharges().add(use);
    }

    /**
     * Template method: computes the total, then each share. The last receipt takes the rounding
     * remainder, so the shares add up to the charge exactly. Not final: Hibernate proxies must
     * be able to delegate it.
     */
    public void allocate() {
        if (supplys.isEmpty()) {
            throw new IllegalStateException("Charge without receipt: " + raison);
        }
        List<Supply> receipts = supplys.stream().map(SupplyUseCharge::getSupply).toList();
        cust = computeTotal(receipts).setScale(SHARE_SCALE, RoundingMode.HALF_UP);

        List<BigDecimal> weights = receipts.stream().map(weigher(receipts)).toList();
        BigDecimal totalWeight = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.signum() <= 0) {
            throw new IllegalStateException("Nothing to split the charge on: " + raison);
        }

        BigDecimal remaining = cust;
        int last = supplys.size() - 1;
        for (int i = 0; i < last; i++) {
            BigDecimal share = cust.multiply(weights.get(i))
                    .divide(totalWeight, SHARE_SCALE, RoundingMode.DOWN);
            supplys.get(i).setShare(share);
            remaining = remaining.subtract(share);
        }
        supplys.get(last).setShare(remaining);
    }
}
