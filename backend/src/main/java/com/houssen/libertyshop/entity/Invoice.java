package com.houssen.libertyshop.entity;

import com.houssen.Sale;
import jakarta.persistence.*;
import lombok.*;

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

    /** To be implemented: build a PDF representation of this invoice. */
    public byte[] generatePdf() {
        throw new UnsupportedOperationException("PDF generation not implemented yet");
    }

    /** To be implemented: send this invoice to a physical/network printer. */
    public void print() {
        this.printed = true;
    }
}
