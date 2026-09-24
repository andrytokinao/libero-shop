package com.houssen.libertyshop.license.exception;

import java.util.List;

/**
 * The signature is valid but the license was issued for a different machine.
 *
 * <p>Typically means the license file was copied to a second installation. It can also
 * happen legitimately after a machine rename or a disk re-image, in which case the
 * customer must send the new fingerprint (reported in the message) to get a reissued
 * license.
 */
public class LicenseMachineMismatchException extends LicenseException {

    private final String currentFingerprint;
    private final List<String> licensedFingerprints;

    public LicenseMachineMismatchException(String message, String currentFingerprint,
                                           List<String> licensedFingerprints) {
        super(message);
        this.currentFingerprint = currentFingerprint;
        this.licensedFingerprints = List.copyOf(licensedFingerprints);
    }

    /** Fingerprint of the machine actually running the application. */
    public String getCurrentFingerprint() {
        return currentFingerprint;
    }

    /** Fingerprints the license authorises. */
    public List<String> getLicensedFingerprints() {
        return licensedFingerprints;
    }

    @Override
    public String code() {
        return "LICENSE_MACHINE_MISMATCH";
    }
}
