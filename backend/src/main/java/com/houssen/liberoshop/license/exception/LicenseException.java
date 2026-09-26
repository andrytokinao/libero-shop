package com.houssen.liberoshop.license.exception;

/**
 * Base type for every license failure.
 *
 * <p>Unchecked on purpose: license enforcement happens through an AOP interceptor and
 * during startup, where a checked exception would force noise into unrelated signatures.
 *
 * <p>Every subclass carries a message written for the end user (a shop manager, not a
 * developer) because it is surfaced as-is in the UI and in the startup log.
 */
public abstract class LicenseException extends RuntimeException {

    protected LicenseException(String message) {
        super(message);
    }

    protected LicenseException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Short machine-readable code, used by the HTTP error payload and by the UI. */
    public abstract String code();
}
