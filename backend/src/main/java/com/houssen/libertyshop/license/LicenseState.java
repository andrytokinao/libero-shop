package com.houssen.libertyshop.license;

/**
 * Runtime state derived from a valid, correctly signed license and the current date.
 *
 * <p>There is no INVALID state on purpose: a broken signature or a foreign machine is an
 * exception, not a state, and prevents the application from starting at all.
 */
public enum LicenseState {

    /** Before the expiry date: everything works. */
    ACTIVE,

    /** Past expiry but inside the grace window: everything still works, with a warning. */
    GRACE,

    /** Past the grace window: browsing stays available, writes are refused. */
    READ_ONLY;

    /** Whether new sales, invoices and stock movements may be created. */
    public boolean allowsWrites() {
        return this != READ_ONLY;
    }
}
