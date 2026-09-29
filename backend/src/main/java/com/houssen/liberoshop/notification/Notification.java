package com.houssen.liberoshop.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * The envelope of every message pushed to a screen.
 *
 * <p>The same shape whatever the kind, so the client decodes one thing and dispatches on
 * {@link #type}. {@link #title} and {@link #message} are ready to show, in French; {@link #data}
 * is what a screen needs to act -- an invoice number to open, a figure to refresh -- and its
 * shape is fixed per type.
 *
 * @param id        unique per notification, so a client that receives one twice -- after a
 *                  reconnection, say -- can tell
 * @param createdAt an instant, not a local time: the browser formats it in its own zone
 */
public record Notification(String id,
                           NotificationType type,
                           String title,
                           String message,
                           Instant createdAt,
                           Object data) {

    public static Notification of(NotificationType type, String title, String message, Object data) {
        return new Notification(UUID.randomUUID().toString(), type, title, message, Instant.now(), data);
    }
}
