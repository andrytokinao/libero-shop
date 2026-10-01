package com.houssen.liberoshop.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * An order, as the till printed it.
 *
 * <p>Its two statuses -- where the money is, where the goods are -- only move through
 * {@link #apply(PaymentTransition)}, {@link #apply(DeliveryTransition)} and {@link #cancel}: they
 * have no setter, so no service can put an order in a state the shop's rules never lead to. What
 * a shop may or may not do on top (cancel after hand-over, who may cancel) is its settings'
 * business, checked by the services before they call in here.
 */
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

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Setter(AccessLevel.NONE)
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

    /** Set together, by {@link #cancel}, and only then. */
    @Setter(AccessLevel.NONE)
    private LocalDateTime cancelledAt;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private UserApp cancelledBy;

    /** A plain varchar, so a reason added later needs no column migration. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private CancelReason cancelReason;

    @Setter(AccessLevel.NONE)
    @Column(length = 255)
    private String cancelComment;

    /** Refuses, without changing anything, a transition the order's money cannot take now. */
    public void check(PaymentTransition transition) {
        if (!transition.appliesTo(paymentStatus)) {
            throw StatusTransitionException.of(invoiceNumber, transition, paymentStatus);
        }
    }

    /** Refuses, without changing anything, a transition the order's goods cannot take now. */
    public void check(DeliveryTransition transition) {
        if (!transition.appliesTo(deliveryStatus)) {
            throw StatusTransitionException.of(invoiceNumber, transition, deliveryStatus);
        }
    }

    /** Moves the money; the sale, which reports on it, moves with it. */
    public void apply(PaymentTransition transition) {
        check(transition);
        paymentStatus = transition.to();
        sale.follow(paymentStatus);
    }

    public void apply(DeliveryTransition transition) {
        check(transition);
        deliveryStatus = transition.to();
    }

    public boolean isHandedOver() {
        return deliveryStatus == DeliveryStatus.DELIVERED;
    }

    /**
     * Cancels the order's money and, when its goods have not left, the goods too; keeps who, when
     * and why. Giving the goods back to the stock is the caller's: it touches other aggregates.
     */
    public void cancel(UserApp by, CancelReason reason, String comment, LocalDateTime at) {
        check(PaymentTransition.CANCEL);
        if (!isHandedOver()) {
            apply(DeliveryTransition.CANCEL);
        }
        apply(PaymentTransition.CANCEL);
        cancelledBy = by;
        cancelReason = reason;
        cancelComment = comment;
        cancelledAt = at;
    }

    /** To be implemented: build a PDF representation of this invoice. */
    public byte[] generatePdf() {
        throw new UnsupportedOperationException("PDF generation not implemented yet");
    }

    /** To be implemented: send this invoice to a physical/network printer. */
    public void print() {
        this.printed = true;
    }
}
