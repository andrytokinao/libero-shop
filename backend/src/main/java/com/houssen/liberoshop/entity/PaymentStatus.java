package com.houssen.liberoshop.entity;

/**
 * Where the money for a sale is.
 *
 * <p>A sale paid at the desk is {@link #PAID} at once. One left unpaid walks the whole path:
 * {@link #UNPAID} until the customer pays the storekeeper on hand-over, {@link #COLLECTED} while
 * the cash is in the storekeeper's hands, {@link #REMITTED} once they have brought it to the desk,
 * and {@link #PAID} only when a cashier confirms having counted it. Only {@code PAID} counts as
 * revenue: money that has not reached the till is not yet the shop's.
 *
 * <p>Public because services, controllers and the REST payloads outside this package
 * branch on it. Persisted by name -- see {@code @Enumerated(EnumType.STRING)} -- so the
 * constant names are part of the stored data and of the HTTP contract: add constants,
 * never rename them. A new constant also needs {@code PaymentStatusMigration} to widen the
 * existing columns.
 */
public enum PaymentStatus {
    PAID,
    UNPAID,
    /** Paid to the storekeeper on hand-over; the cash is still with them. */
    COLLECTED,
    /** Brought to the desk by the storekeeper; a cashier has yet to confirm receiving it. */
    REMITTED;

    /** What a checkout may ask for: the other two are reached through the depot, never posted. */
    public boolean isCheckoutStatus() {
        return this == PAID || this == UNPAID;
    }
}
