package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;

import java.math.BigDecimal;

/**
 * Where "the stock is low" is defined, once.
 *
 * <p>The threshold drove four separate dashboards in the mock-up; keeping it here means
 * turning it into a per-category or per-product reorder level later is a change to this
 * class alone.
 */
public final class StockPolicy {

    public static final BigDecimal LOW_STOCK_THRESHOLD = BigDecimal.TEN;

    private StockPolicy() {
    }

    public static boolean isLowStock(Product product) {
        return product.getStockQuantity().compareTo(LOW_STOCK_THRESHOLD) < 0;
    }
}
