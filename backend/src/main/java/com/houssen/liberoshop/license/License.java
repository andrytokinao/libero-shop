package com.houssen.liberoshop.license;

import java.time.LocalDate;
import java.util.List;

/**
 * The license data, as signed by the publisher.
 *
 * <p>Immutable and never persisted: the signed file on disk is the only source of truth.
 * Nothing here is mirrored into a database column, so there is no {@code isActive} flag
 * a customer could flip with an SQL client.
 *
 * <p>Instances only ever come out of {@link LicenseVerifier} after the signature has been
 * checked, so downstream code can treat every field as publisher-authenticated.
 *
 * @param licenseId           unique id of this issue, used by the renewal endpoint
 * @param customerId          stable customer reference
 * @param customerName        display name, shown in the UI
 * @param plan                billing cycle
 * @param issuedOn            first day of validity
 * @param expiresOn           last day of full validity, inclusive
 * @param graceDays           days after {@code expiresOn} during which writes are still allowed
 * @param machineFingerprints fingerprints of the machines authorised to run this license
 * @param notes               free text from the publisher, may be null
 */
public record License(
        String licenseId,
        String customerId,
        String customerName,
        LicensePlan plan,
        LocalDate issuedOn,
        LocalDate expiresOn,
        int graceDays,
        List<String> machineFingerprints,
        String notes) {

    public License {
        machineFingerprints = List.copyOf(machineFingerprints);
    }

    /**
     * First day on which the application turns read-only. Expiry is inclusive, so a
     * license expiring on the 30th with 15 grace days still accepts writes on the 15th
     * of the next month and degrades on the 16th.
     */
    public LocalDate readOnlyFrom() {
        return expiresOn.plusDays(graceDays + 1L);
    }

    /** Evaluates the state for a given day. */
    public LicenseState stateOn(LocalDate day) {
        if (!day.isAfter(expiresOn)) {
            return LicenseState.ACTIVE;
        }
        return day.isBefore(readOnlyFrom()) ? LicenseState.GRACE : LicenseState.READ_ONLY;
    }

    /** True when this fingerprint is one of the authorised machines. */
    public boolean authorises(String fingerprint) {
        return machineFingerprints.stream().anyMatch(authorised -> authorised.equalsIgnoreCase(fingerprint));
    }
}
