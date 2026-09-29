package com.houssen.liberoshop.notification;

/**
 * What a notification is about, so a screen can react to the kind without reading the wording.
 *
 * <p>Sent as its name. Adding a kind is adding a constant here and a case where the client
 * listens; nothing in the transport has to change.
 */
public enum NotificationType {

    /** A sale was recorded at the counter: goods are waiting to leave the depot. */
    SALE_CREATED
}
