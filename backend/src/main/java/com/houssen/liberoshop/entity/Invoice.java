package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "invoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @Column(nullable = false)
    private LocalDateTime invoiceDate;

    @Column(nullable = false)
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus;

    // Extension point: stays false until real printing is implemented.
    @Builder.Default
    @Column(nullable = false)
    private boolean printed = false;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    /** Set together, by {@code InvoiceService.cancel}, and only then. */
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private UserApp cancelledBy;

    /** A plain varchar, so a reason added later needs no column migration. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private CancelReason cancelReason;

    @Column(length = 255)
    private String cancelComment;

    /** To be implemented: build a PDF representation of this invoice. */
    public byte[] generatePdf() {
        throw new UnsupportedOperationException("PDF generation not implemented yet");
    }

    /** To be implemented: send this invoice to a physical/network printer. */
    public void print() {
        this.printed = true;
    }
}
