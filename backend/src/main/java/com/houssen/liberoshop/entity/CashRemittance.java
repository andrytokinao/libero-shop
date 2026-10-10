package com.houssen.liberoshop.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_remittance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashRemittance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Counts the changes made to the slip: two cashiers confirming it at the same instant must
     * not both be told they counted the money. See {@link Invoice}'s for the mechanism.
     */
    @Version
    @Setter(AccessLevel.NONE)
    @ColumnDefault("0")
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime remittanceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RemittanceStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by")
    private UserApp submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private UserApp confirmedBy;
}
