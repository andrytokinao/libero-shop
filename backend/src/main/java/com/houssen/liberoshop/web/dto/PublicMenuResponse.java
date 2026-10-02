package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The menu as a customer sees it at their table: what can be ordered and its price, nothing
 * else. No stock figure, no cost, no other order -- this is served to anyone holding the QR code.
 */
public record PublicMenuResponse(String tableName, List<Item> items) {

    /**
     * @param unit      the base unit's name, the one {@code price} is for; null for bare units
     * @param available false once the product is sold out
     * @param units     the other units it can be ordered in -- "kg", "pack 25 kg" -- smallest first
     */
    public record Item(Long id, String name, BigDecimal price, String unit, String category,
                       boolean available, List<Unit> units) {
    }

    /** A unit as the customer picks it: a name and a price, never how much stock it draws. */
    public record Unit(Long id, String label, BigDecimal price) {
    }
}
