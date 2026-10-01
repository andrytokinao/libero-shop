package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The menu as a customer sees it at their table: what can be ordered and its price, nothing
 * else. No stock figure, no cost, no other order -- this is served to anyone holding the QR code.
 */
public record PublicMenuResponse(String tableName, List<Item> items) {

    /** @param available false once the product is sold out */
    public record Item(Long id, String name, BigDecimal price, String category, boolean available) {
    }
}
