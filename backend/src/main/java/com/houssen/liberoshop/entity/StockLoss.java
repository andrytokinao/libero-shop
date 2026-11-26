package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A loss, said in quantity only: its value in money is never typed, it is absorbed by the cost
 * of the units that remain.
 *
 * <p>Lost on the way: {@code supply} is set. Lost during a transformation: {@code transformation}
 * is set. Breakage or expiry on the shelf: neither.
 */
@Entity
@DiscriminatorValue("LOSS")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StockLoss extends StockMovement {

    @Enumerated(EnumType.STRING)
    @Column(name = "loss_reason", length = 20)
    private LossReason reason;

    @Column(name = "loss_note", length = 255)
    private String note;

    /** The receipt whose goods were lost on the way. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loss_supply_id")
    private Supply supply;

    /** The transformation during which the material was lost. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loss_transformation_id")
    private Transformation transformation;

    @Override
    public int direction() {
        return -1;
    }
}
