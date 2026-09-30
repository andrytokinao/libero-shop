package com.houssen.liberoshop.notification;

/**
 * What a notification is about, so a screen can react to the kind without reading the wording.
 *
 * <p>Sent as its name. Adding a kind is adding a constant here and a case where the client
 * listens; nothing in the transport has to change.
 */
public enum NotificationType {

    /** A sale was recorded at the counter: goods are waiting to leave the depot. */
    SALE_CREATED,

    /** An order was handed over at the depot: its seller learns the customer has the goods. */
    ORDER_DELIVERED,

    /** A storekeeper brought cash to the desk: a cashier has to count it and confirm. */
    REMITTANCE_SUBMITTED,

    /** A cashier confirmed receiving a storekeeper's cash: the orders are paid. */
    REMITTANCE_CONFIRMED
}
