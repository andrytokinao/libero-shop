package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;

/**
 * Where "the stock is low" is defined, once.
 *
 * <p>The threshold drove four separate dashboards in the mock-up; keeping it here means
 * turning it into a per-category or per-product reorder level later is a change to this
 * class alone.
 */
public final class StockPolicy {

    public static final int LOW_STOCK_THRESHOLD = 10;

    private StockPolicy() {
    }

    public static boolean isLowStock(Product product) {
        return product.getStockQuantity() < LOW_STOCK_THRESHOLD;
    }
}
