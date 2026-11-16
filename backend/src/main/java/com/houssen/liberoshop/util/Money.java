package com.houssen.liberoshop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** How the shop writes an amount on its documents. */
public final class Money {

    private Money() {
    }

    /** "12 500 Ar", "1 250,50 Ar": spaces between thousands, cents only when there are some. */
    public static String format(BigDecimal value, String currency) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRENCH);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        BigDecimal cents = value.setScale(2, RoundingMode.HALF_UP);
        String pattern = cents.stripTrailingZeros().scale() > 0 ? "#,##0.00" : "#,##0";
        return new DecimalFormat(pattern, symbols).format(cents) + " " + currency;
    }
}