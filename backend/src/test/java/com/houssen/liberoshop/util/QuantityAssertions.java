package com.houssen.liberoshop.util;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Compares a quantity by value: 12 and 12.000 are the same stock.
 *
 * <p>{@code assertEquals(12, quantity)} would still compile -- it boxes to {@code Object} -- and
 * fail on every run, while {@code assertEquals(BigDecimal.valueOf(12), quantity)} fails on the
 * scale alone. Neither says anything about the shelf.
 */
public final class QuantityAssertions {

    private QuantityAssertions() {
    }

    public static void assertQuantity(String expected, BigDecimal actual) {
        assertQuantity(expected, actual, null);
    }

    public static void assertQuantity(String expected, BigDecimal actual, String message) {
        assertNotNull(actual, message);
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros(), message);
    }

    public static void assertQuantity(long expected, BigDecimal actual) {
        assertQuantity(String.valueOf(expected), actual, null);
    }

    public static void assertQuantity(long expected, BigDecimal actual, String message) {
        assertQuantity(String.valueOf(expected), actual, message);
    }

    /** A whole number of units, for builders and requests. */
    public static BigDecimal qty(long units) {
        return BigDecimal.valueOf(units);
    }
}
