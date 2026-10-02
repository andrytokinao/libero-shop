package com.houssen.liberoshop.service.exception;

import com.houssen.liberoshop.util.Quantities;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Not enough units left for one or more lines of a sale.
 *
 * <p>Names every short line at once, not just the first: the till marks each of them in red,
 * and a cashier who fixed one line only to be refused on the next would have to try once per
 * article. The {@link #data() shortages} travel with the error so the screen knows which lines
 * to mark without reading the message.
 */
public class InsufficientStockException extends BusinessRuleException {

    public static final String CODE = "INSUFFICIENT_STOCK";

    /**
     * One product the shelf cannot fill: what was asked and what is really there, both in the
     * product's base unit -- its lines may have been in several units.
     *
     * @param unit the base unit's name, null for a product counted in bare units
     */
    public record Shortage(Long productId, String productName, BigDecimal requested,
                           BigDecimal available, String unit) {
    }

    private final List<Shortage> shortages;

    public InsufficientStockException(List<Shortage> shortages) {
        super(CODE, messageFor(shortages));
        this.shortages = List.copyOf(shortages);
    }

    public List<Shortage> shortages() {
        return shortages;
    }

    @Override
    public Object data() {
        return shortages;
    }

    private static String messageFor(List<Shortage> shortages) {
        return "Stock insuffisant pour " + shortages.stream()
                .map(s -> s.productName() + " (" + amount(s.requested(), s.unit()) + " demande(s), "
                        + amount(s.available(), s.unit()) + " disponible(s))")
                .collect(Collectors.joining(", ")) + ".";
    }

    /** "1,75 kapoka", or a bare "3" for a product counted in bare units. */
    private static String amount(BigDecimal quantity, String unit) {
        return Quantities.format(quantity) + (unit == null ? "" : " " + unit);
    }
}
