package com.houssen.libertyshop.license.exception;

/**
 * The license file is unreadable, malformed, or its signature does not match the
 * embedded public key.
 *
 * <p>This is the tampering signal: it means the file was edited, truncated, or produced
 * with a different private key. Unlike expiry, it never degrades gracefully -- the
 * application refuses to start.
 */
public class LicenseInvalidException extends LicenseException {

    public LicenseInvalidException(String message) {
        super(message);
    }

    public LicenseInvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "LICENSE_INVALID";
    }
}
