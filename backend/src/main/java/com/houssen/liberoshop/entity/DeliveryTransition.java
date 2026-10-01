package com.houssen.liberoshop.entity;

/**
 * Everything that may happen to an order's goods. Same shape as {@link PaymentTransition}: one
 * status to another, and nothing else.
 *
 * <p>An order cancelled after hand-over keeps {@code DELIVERED}: the goods did leave, and only its
 * money is cancelled.
 */
public enum DeliveryTransition {

    /** The goods leave the depot with the customer, the waiter or the courier. */
    HAND_OVER(DeliveryStatus.PENDING, DeliveryStatus.DELIVERED),
    /** Cancelled before leaving: the goods go back on the shelf. */
    CANCEL(DeliveryStatus.PENDING, DeliveryStatus.CANCELLED);

    private final DeliveryStatus from;
    private final DeliveryStatus to;

    DeliveryTransition(DeliveryStatus from, DeliveryStatus to) {
        this.from = from;
        this.to = to;
    }

    public DeliveryStatus from() {
        return from;
    }

    public DeliveryStatus to() {
        return to;
    }

    public boolean appliesTo(DeliveryStatus current) {
        return current == from;
    }
}
