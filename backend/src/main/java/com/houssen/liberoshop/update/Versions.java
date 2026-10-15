package com.houssen.liberoshop.update;

/**
 * Compares dotted application versions ({@code 1.2.0}, {@code 1.10.3}) numerically, so that
 * {@code 1.10.0} ranks above {@code 1.9.0} -- which a plain string comparison gets wrong.
 *
 * <p>Each version is split on dots; each part is compared as a number when both sides are
 * numeric, otherwise as text. Missing trailing parts count as zero, so {@code 1.2} equals
 * {@code 1.2.0}. A {@code SNAPSHOT} or other suffix on a part keeps it textual and simply ranks
 * it, which is enough here: the only decision that matters is "is the candidate strictly newer".
 */
public final class Versions {

    private Versions() {
    }

    /** Negative if {@code a} is older than {@code b}, positive if newer, zero if equal. */
    public static int compare(String a, String b) {
        String[] left = a == null ? new String[0] : a.trim().split("\\.");
        String[] right = b == null ? new String[0] : b.trim().split("\\.");
        int parts = Math.max(left.length, right.length);
        for (int i = 0; i < parts; i++) {
            String l = i < left.length ? left[i] : "0";
            String r = i < right.length ? right[i] : "0";
            int result = comparePart(l, r);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    /** True when {@code candidate} is strictly newer than {@code current}. */
    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    private static int comparePart(String l, String r) {
        Long ln = asNumber(l);
        Long rn = asNumber(r);
        if (ln != null && rn != null) {
            return Long.compare(ln, rn);
        }
        return l.compareTo(r);
    }

    private static Long asNumber(String part) {
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
