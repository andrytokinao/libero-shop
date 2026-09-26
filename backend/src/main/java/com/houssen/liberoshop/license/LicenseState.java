package com.houssen.liberoshop.license;

/**
 * Runtime state derived from the current date and either a valid, correctly signed
 * license or the local evaluation period.
 *
 * <p>There is no INVALID state on purpose: a broken signature or a foreign machine is an
 * exception, not a state, and prevents the application from starting at all.
 *
 * <p>The two trial states are kept separate from {@link #ACTIVE} and {@link #READ_ONLY}
 * rather than folded into them. They permit exactly the same operations, but the UI, the
 * logs and the status endpoint must never present an unlicensed installation as a
 * licensed one -- and {@code TrialRegistry} mints its trial locally, so the distinction is
 * also the line between publisher-signed data and data the machine produced itself.
 */
public enum LicenseState {

    /** Before the expiry date: everything works. */
    ACTIVE,

    /** Past expiry but inside the grace window: everything still works, with a warning. */
    GRACE,

    /** Past the grace window: browsing stays available, writes are refused. */
    READ_ONLY,

    /** No license installed, inside the one-off evaluation period: everything works. */
    TRIAL,

    /** The evaluation period is over and no license was ever installed: writes are refused. */
    TRIAL_EXPIRED;

    /** Whether new sales, invoices and stock movements may be created. */
    public boolean allowsWrites() {
        return this != READ_ONLY && this != TRIAL_EXPIRED;
    }

    /** Whether the application is running on the local evaluation period rather than a license. */
    public boolean isTrial() {
        return this == TRIAL || this == TRIAL_EXPIRED;
    }
}
