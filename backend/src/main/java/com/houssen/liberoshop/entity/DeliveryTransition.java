package com.houssen.liberoshop.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Everything that may happen to an order's goods. Same shape as {@link PaymentTransition} -- a
 * status reached, and the statuses it may be reached from -- except that some steps start from
 * more than one: an order may be handed over straight from the queue, or once someone took it on.
 *
 * <p>An order cancelled after hand-over keeps {@code DELIVERED}: the goods did leave, and only its
 * money is cancelled.
 */
public enum DeliveryTransition {
    /** Someone takes the order on: "je m'en occupe". The others leave it to them. */
    TAKE_OVER(DeliveryStatus.IN_PROGRESS, DeliveryStatus.PENDING),
    /** Given back to the queue -- not theirs after all, or they left. */
    RELEASE(DeliveryStatus.PENDING, DeliveryStatus.IN_PROGRESS),
    /** The goods leave the depot with the customer, the waiter or the courier. */
    HAND_OVER(DeliveryStatus.DELIVERED, DeliveryStatus.PENDING, DeliveryStatus.IN_PROGRESS),
    /** Cancelled before leaving: the goods go back on the shelf. */
    CANCEL(DeliveryStatus.CANCELLED, DeliveryStatus.PENDING, DeliveryStatus.IN_PROGRESS);

    private final DeliveryStatus to;
    private final Set<DeliveryStatus> from;

    DeliveryTransition(DeliveryStatus to, DeliveryStatus first, DeliveryStatus... others) {
        this.to = to;
        this.from = EnumSet.of(first, others);
    }

    /** The statuses this step may start from. */
    public Set<DeliveryStatus> from() {
        return from;
    }

    public DeliveryStatus to() {
        return to;
    }

    public boolean appliesTo(DeliveryStatus current) {
        return from.contains(current);
    }
}
