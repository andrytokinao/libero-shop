package com.houssen.liberoshop.license.exception;

import java.time.LocalDate;

/**
 * The license is past its expiry date and past its grace period, so the application
 * runs read-only.
 *
 * <p>Deliberately <em>not</em> thrown at startup: expiry degrades the application
 * instead of blocking it (see {@code LicenseService}). It is thrown when a write
 * operation guarded by {@code @RequiresActiveLicense} is attempted.
 */
public class LicenseExpiredException extends LicenseException {

    private final LocalDate expiredOn;

    public LicenseExpiredException(String message, LocalDate expiredOn) {
        super(message);
        this.expiredOn = expiredOn;
    }

    /** The expiry date carried by the license, for display in the renewal message. */
    public LocalDate getExpiredOn() {
        return expiredOn;
    }

    @Override
    public String code() {
        return "LICENSE_EXPIRED";
    }
}
