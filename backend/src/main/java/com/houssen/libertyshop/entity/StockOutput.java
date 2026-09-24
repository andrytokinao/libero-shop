package com.houssen.libertyshop.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
}
