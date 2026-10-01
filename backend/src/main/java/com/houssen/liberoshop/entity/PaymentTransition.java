package com.houssen.liberoshop.entity;

/**
 * Everything that may happen to an order's money, each from exactly one status to another.
 *
 * <p>Transitions rather than target statuses, because the same target is reached by different
 * acts that must not be confused: {@link #CONFIRM_REMITTANCE} takes a {@code REMITTED} order to
 * {@code PAID}, while {@link #PAY_AT_TILL} only takes an {@code UNPAID} one there -- allowing
 * "REMITTED to PAID" in general would let the till write a second payment for cash already on its
 * way. The whole life of the money is this table: a new way of paying is a new constant here, and
 * no service has to learn it.
 */
public enum PaymentTransition {

    /** The customer pays the storekeeper or the order taker, who now holds the cash. */
    COLLECT(PaymentStatus.UNPAID, PaymentStatus.COLLECTED),
    /** The customer pays at the till: in the till at once. */
    PAY_AT_TILL(PaymentStatus.UNPAID, PaymentStatus.PAID),
    /** The collector brings the cash to the desk, on a slip. */
    REMIT(PaymentStatus.COLLECTED, PaymentStatus.REMITTED),
    /** A cashier counts the slip's cash: only now is it the shop's revenue. */
    CONFIRM_REMITTANCE(PaymentStatus.REMITTED, PaymentStatus.PAID),
    /** Nothing was paid, nothing is owed any more. A paid order is never cancelled: its money would have to go back. */
    CANCEL(PaymentStatus.UNPAID, PaymentStatus.CANCELLED);

    private final PaymentStatus from;
    private final PaymentStatus to;

    PaymentTransition(PaymentStatus from, PaymentStatus to) {
        this.from = from;
        this.to = to;
    }

    public PaymentStatus from() {
        return from;
    }

    public PaymentStatus to() {
        return to;
    }

    public boolean appliesTo(PaymentStatus current) {
        return current == from;
    }
}
