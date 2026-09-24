package com.houssen.libertyshop.license;

/** Billing cycle the license was sold under. Informational: expiry is what enforces. */
public enum LicensePlan {

    /** Paid monthly, typically renewed automatically by the online check. */
    MONTHLY,

    /** Paid yearly. */
    ANNUAL,

    /** Free evaluation period. */
    TRIAL;

    /** Maps a raw value from the license file, defaulting to {@link #ANNUAL} if unknown. */
    static LicensePlan from(String raw) {
        if (raw == null) {
            return ANNUAL;
        }
        for (LicensePlan plan : values()) {
            if (plan.name().equalsIgnoreCase(raw.trim())) {
                return plan;
            }
        }
        return ANNUAL;
    }
}
