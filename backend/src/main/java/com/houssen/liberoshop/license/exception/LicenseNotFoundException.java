package com.houssen.liberoshop.license.exception;

/**
 * No license file at the configured path.
 *
 * <p>Distinct from {@link LicenseInvalidException} because the remedy is different:
 * nothing was tampered with, the file simply has not been installed yet. The message
 * carries the expected path and the machine fingerprint so a fresh installation can be
 * licensed without a support call.
 */
public class LicenseNotFoundException extends LicenseException {

    public LicenseNotFoundException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "LICENSE_NOT_FOUND";
    }
}
