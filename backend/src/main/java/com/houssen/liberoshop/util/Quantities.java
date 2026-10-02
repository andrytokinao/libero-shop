package com.houssen.liberoshop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a quantity of goods is held: a decimal to the thousandth.
 *
 * <p>Whole units were enough while everything sold by the piece. Loose goods are not -- half a
 * kilo of rice is 1.75 kapoka -- so stock, movements and sale lines all count in decimals, and
 * this class is where their precision is decided once.
 */
public final class Quantities {

    /** A thousandth: a gram of a kilo, a millilitre of a litre. */
    public static final int SCALE = 3;
    /** Column precision for stock and movements: up to a hundred billion units, to the thousandth. */
    public static final int PRECISION = 14;

    private Quantities() {
    }

    /** A whole number of units, at the stored scale. */
    public static BigDecimal of(long units) {
        return BigDecimal.valueOf(units).setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * At the stored scale, rounded half up. Only a product of two quantities can carry more --
     * 0.125 kg at 3.5 kapoka a kilo is 0.4375 kapoka -- and a ten-thousandth of a kapoka is
     * below anything a shop can weigh.
     */
    public static BigDecimal scaled(BigDecimal quantity) {
        return quantity.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Null as zero, for the sums a database returns over no rows. */
    public static BigDecimal orZero(Object number) {
        if (number == null) {
            return of(0);
        }
        return number instanceof BigDecimal decimal ? decimal : new BigDecimal(number.toString());
    }

    /** As a person writes it: "3", "1,75" -- no trailing zeros, French decimal comma. */
    public static String format(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /** True when it holds more decimals than the stored scale, which would have to be rounded. */
    public static boolean tooPrecise(BigDecimal quantity) {
        return quantity.stripTrailingZeros().scale() > SCALE;
    }
}
