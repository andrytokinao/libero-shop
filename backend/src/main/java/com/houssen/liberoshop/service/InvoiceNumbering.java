package com.houssen.liberoshop.service;

/**
 * How an invoice number is built, in one place.
 *
 * <p>Derived from the sale identifier, which the database already guarantees unique, so
 * two desks checking out at the same instant cannot collide -- no counter table, no
 * read-then-increment race. The offset only makes the first invoice of a fresh
 * installation read as F-1001 rather than F-1.
 */
public final class InvoiceNumbering {

    private static final long DISPLAY_OFFSET = 1000L;

    private InvoiceNumbering() {
    }

    public static String forSale(Long saleId) {
        return "F-" + (DISPLAY_OFFSET + saleId);
    }
}
