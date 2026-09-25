package com.houssen.libertyshop.entity;

/**
 * Whether the money for a sale has been collected.
 *
 * <p>Public because services, controllers and the REST payloads outside this package
 * branch on it. Persisted by name -- see {@code @Enumerated(EnumType.STRING)} -- so the
 * constant names are part of the stored data and of the HTTP contract: add constants,
 * never rename them.
 */
public enum PaymentStatus {
    PAID,
    UNPAID
}
